package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import java.util.Objects;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Avoidance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.query.Compass;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Bends a unit's course around whatever it is about to run into, before it runs into it.
 *
 * <p><strong>Context steering</strong>: a fixed ring of directions scored for desire and danger. Both
 * parties are projected {@code lookaheadSeconds} ahead, so it avoids where things will be.
 *
 * <p>What is worth going round is on the collider ({@code avoided}), decided at spawn. <strong>Never
 * a pickup it wants</strong>, except one it shuns (G0022, a question about the pair), and
 * <strong>never the other side</strong> - that is what it is trying to reach. <strong>Ground is the
 * other way round</strong> (G0035, amended): ruined ground is given room by whoever it could catch and
 * walked through by the side it belongs to, so a frog wades through its own venom and a man goes round
 * it. Something solid is a body; something that is not is ground. <strong>Ground may be heeded only on a
 * chance</strong> (G0076) - an alien's toxic blood - drawn from the man and the spatter together, so he goes
 * round all of one spatter or walks through all of it, and the same every tick.
 *
 * <p><strong>A unit standing still steps off ground that would hurt it.</strong> With no course there is
 * nothing to bend, so it walks straight away from whatever ground is inside its scan range instead -
 * a man holding a point does not watch fire swell up round his boots. Bodies never move it.
 *
 * <p><strong>Ground is measured from where the unit is as well as from where it will be</strong>, so a circle
 * level with a unit running past it is never behind the point it is measured from.
 *
 * <p><strong>Ground is weighed along the way rock will let him go</strong> (G0090): a way that leans into a wall he is
 * against is the way along the wall, since that is where the solver will put him - so a man pressed to a corridor's
 * side by fire across it holds short of it, rather than taking the way that points past it and sliding into it.
 * Bodies are weighed as they always were.
 *
 * <p>Adjusts a desired velocity, never sets one. Hazards are filed in a grid once a tick; the query
 * reach is an exact bound, so the answer equals a scan over every hazard.
 */
public final class AvoidanceSystem extends EntitySystem {

    /** Twelve candidate directions, thirty degrees apart - enough resolution to slip past a circle. */
    private static final int SLOTS = Compass.SLOTS;

    private static final float EPSILON = 1e-4f;

    /** How far past his own edge rock is looked for, as a share of his radius: touching, not near (G0090). */
    private static final float WALL_PROBE = 0.25f;

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Avoidance> AVOIDANCE = ComponentMapper.getFor(Avoidance.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);

    /** Danger per candidate direction, refilled per unit. One array, reused, so nothing allocates. */
    private final float[] danger = new float[SLOTS];

    /**
     * Each candidate direction as rock leaves it for this unit (G0090): the part that leans into a wall he is against
     * taken off, then made unit length again, or zero where nothing is left. Refilled per unit with {@link #danger}.
     */
    private final float[] wayX = new float[SLOTS];

    private final float[] wayY = new float[SLOTS];

    /** Where rock is, which ground is weighed against for the ways above. */
    private final Solid rock;

    /** Which way is away from the ground near this unit, summed; refilled per unit with {@link #danger}. */
    @NotState("scratch: refilled for every unit")
    private float awayX;

    @NotState("scratch: refilled for every unit")
    private float awayY;

    /** The world's seed, which the chance of heeding a piece of ground is keyed from (G0076). */
    private final long seed;

    /** Every hazard, filed once per tick so each unit asks only the cells within reach of it. */
    private final SpatialGrid grid;

    /** What one unit's query found, reused so nothing allocates. */
    private final Array<Entity> nearby = new Array<>();

    /** The widest hazard filed this tick, for the reach bound. */
    @NotState("scratch: recomputed every update")
    private float widestHazard;

    /** The fastest hazard filed this tick, for the reach bound. */
    @NotState("scratch: recomputed every update")
    private float fastestHazard;

    private ImmutableArray<Entity> steerers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> hazards = new ImmutableArray<>(new Array<>());

    public AvoidanceSystem(SimConfig config, Solid rock) {
        this.seed = config.seed();
        this.rock = rock;
        this.grid = new SpatialGrid(rock.map().worldWidth(), rock.map().worldHeight(), config.largestRadius() * 2f);
    }

    @Override
    public void addedToEngine(Engine engine) {
        steerers = engine.getEntitiesFor(
                Family.all(Avoidance.class, Steering.class, Position.class, Velocity.class, Body.class, Faction.class)
                        .get());
        hazards = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Collider.class).get());
    }

    @Override
    public void update(float deltaTime) {
        fileHazards();
        for (int i = 0; i < steerers.size(); i++) {
            Entity entity = steerers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Avoidance avoidance = AVOIDANCE.get(entity);
            if (avoidance.enabled) {
                steer(entity, avoidance);
            }
        }
    }

    /** Files everything that is steered around, and measures the widest and fastest of it. */
    private void fileHazards() {
        grid.clear();
        widestHazard = 0f;
        fastestHazard = 0f;
        for (int i = 0; i < hazards.size(); i++) {
            Entity hazard = hazards.get(i);
            if (!COLLIDER.get(hazard).avoided) {
                continue;
            }
            Position at = POSITION.get(hazard);
            grid.insert(hazard, at.x, at.y);
            widestHazard = Math.max(widestHazard, BODY.get(hazard).radius);
            if (VELOCITY.has(hazard)) {
                fastestHazard = Math.max(fastestHazard, speedOf(VELOCITY.get(hazard)));
            }
        }
    }

    private static float speedOf(Velocity velocity) {
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
    }

    private void steer(Entity entity, Avoidance avoidance) {
        Steering steering = STEERING.get(entity);
        float speed = (float) Math.sqrt(steering.desiredX * steering.desiredX + steering.desiredY * steering.desiredY);
        if (speed <= EPSILON) {
            stepOffTheGround(entity, avoidance, steering);
            return;
        }
        float wantX = steering.desiredX / speed;
        float wantY = steering.desiredY / speed;
        if (!mapDanger(entity, avoidance)) {
            return;
        }
        int best = 0;
        float bestScore = -Float.MAX_VALUE;
        for (int slot = 0; slot < SLOTS; slot++) {
            float interest = Compass.x(slot) * wantX + Compass.y(slot) * wantY;
            float score = interest - avoidance.weight * danger[slot];
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        steering.set(Compass.x(best) * speed, Compass.y(best) * speed);
    }

    /**
     * Walks a unit that asked to go nowhere straight away from the ground near it, at its own pace.
     *
     * <p>Only ground moves it: a unit standing beside a wall or a crowd stays where it was put.
     */
    private void stepOffTheGround(Entity entity, Avoidance avoidance, Steering steering) {
        if (!SPEED.has(entity) || !mapDanger(entity, avoidance)) {
            return;
        }
        float length = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (length <= EPSILON) {
            return;
        }
        float pace = SPEED.get(entity).value;
        steering.set(awayX / length * pace, awayY / length * pace);
    }

    /**
     * Scores every candidate direction against everything worth avoiding, and reports whether anything
     * was.
     *
     * <p>False: the course is kept exactly, not rounded.
     */
    private boolean mapDanger(Entity entity, Avoidance avoidance) {
        for (int slot = 0; slot < SLOTS; slot++) {
            danger[slot] = 0f;
        }
        awayX = 0f;
        awayY = 0f;
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        float radius = BODY.get(entity).radius;
        Side side = FACTION.get(entity).side;
        float aheadX = position.x + velocity.x * avoidance.lookaheadSeconds;
        float aheadY = position.y + velocity.y * avoidance.lookaheadSeconds;
        fillWays(position, radius);
        boolean threatened = false;
        // Nothing further than this can project to within scanRange: the gap between projected
        // centres is at most the gap now plus how far each can travel in the lookahead. The extra
        // unit is slack for rounding, and only ever widens the query.
        float reach = avoidance.scanRange
                + radius
                + widestHazard
                + avoidance.lookaheadSeconds * (speedOf(velocity) + fastestHazard)
                + 1f;
        grid.near(position.x, position.y, reach, nearby);
        for (int i = 0; i < nearby.size; i++) {
            Entity hazard = nearby.get(i);
            Collider collider = COLLIDER.get(hazard);
            if (Objects.equals(hazard, entity)
                    || !collider.avoided
                    || (PICKUP.has(hazard) && !shunnedBy(entity, hazard))
                    || (collider.solid
                            ? opposed(side, hazard)
                            : (ownGround(side, hazard) || !heeds(entity, collider)))) {
                continue;
            }
            Position hazardPosition = POSITION.get(hazard);
            float hazardRadius = BODY.get(hazard).radius;
            if (!collider.solid) {
                // Ground from where the unit is now, too: a circle level with him is behind the point he will
                // be at, and measured only from there it would look clear to every way forward (G0046).
                threatened |= weigh(
                        hazardPosition.x - position.x,
                        hazardPosition.y - position.y,
                        radius + hazardRadius,
                        avoidance,
                        false);
            }
            float hazardAheadX = hazardPosition.x;
            float hazardAheadY = hazardPosition.y;
            if (VELOCITY.has(hazard)) {
                Velocity hazardVelocity = VELOCITY.get(hazard);
                hazardAheadX += hazardVelocity.x * avoidance.lookaheadSeconds;
                hazardAheadY += hazardVelocity.y * avoidance.lookaheadSeconds;
            }
            threatened |= weigh(
                    hazardAheadX - aheadX, hazardAheadY - aheadY, radius + hazardRadius, avoidance, collider.solid);
        }
        return threatened;
    }

    /**
     * Each candidate direction as the rock he is against leaves it (G0090): the solver takes off whatever leans into a
     * wall he touches, so that is the way he will actually go. Four probes a unit, only for one with something near.
     */
    private void fillWays(Position at, float radius) {
        float probe = radius * (1f + WALL_PROBE);
        boolean east = !rock.passableAt(at.x + probe, at.y);
        boolean west = !rock.passableAt(at.x - probe, at.y);
        boolean north = !rock.passableAt(at.x, at.y + probe);
        boolean south = !rock.passableAt(at.x, at.y - probe);
        for (int slot = 0; slot < SLOTS; slot++) {
            float x = Compass.x(slot);
            float y = Compass.y(slot);
            if ((x > 0f && east) || (x < 0f && west)) {
                x = 0f;
            }
            if ((y > 0f && north) || (y < 0f && south)) {
                y = 0f;
            }
            float length = (float) Math.sqrt(x * x + y * y);
            wayX[slot] = length > EPSILON ? x / length : 0f;
            wayY[slot] = length > EPSILON ? y / length : 0f;
        }
    }

    /**
     * Scores every candidate direction against one hazard seen from one place, keeping the greater
     * threat in each, and reports whether it was close enough to matter.
     *
     * @param towardsX from the unit to the hazard, centre to centre
     * @param radii the two bodies' radii together
     */
    private boolean weigh(float towardsX, float towardsY, float radii, Avoidance avoidance, boolean solid) {
        float distance = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
        if (distance <= EPSILON) {
            return false;
        }
        float gap = distance - radii;
        if (gap >= avoidance.scanRange) {
            return false;
        }
        float closeness = gap <= 0f ? 1f : (avoidance.scanRange - gap) / avoidance.scanRange;
        float unitX = towardsX / distance;
        float unitY = towardsY / distance;
        if (!solid) {
            awayX -= unitX * closeness;
            awayY -= unitY * closeness;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            // Ground along the way he will actually go, bodies along the way he asked for (G0090).
            float slotX = solid ? Compass.x(slot) : wayX[slot];
            float slotY = solid ? Compass.y(slot) : wayY[slot];
            float alignment = slotX * unitX + slotY * unitY;
            if (alignment <= 0f) {
                continue;
            }
            float threat = closeness * alignment;
            if (!solid) {
                // Ground is weighed by whether a way takes his body across it, not by how near its middle that way
                // points: across it is as bad as straight at it, past its edge costs nothing. So a seam between two
                // circles narrower than he is reads as shut, and the way round as open (ticket 18).
                float offTheWay = Math.abs(slotX * towardsY - slotY * towardsX);
                threat = offTheWay < radii ? closeness : 0f;
            }
            if (threat > danger[slot]) {
                danger[slot] = threat;
            }
        }
        return true;
    }

    /**
     * Whether this steerer gives that pickup a wide berth.
     *
     * <p>A question about the pair: not shunned by anything that can swallow one (G0022).
     */
    private static boolean shunnedBy(Entity steerer, Entity hazard) {
        if (!PICKUP.get(hazard).shunned) {
            return false;
        }
        return !TAKING.has(steerer) || !TAKING.get(steerer).swallows;
    }

    /**
     * Ground its own side laid, which cannot catch it (G0034): a frog wades through its own venom.
     *
     * <p>Ground on nobody's side - fire - catches everybody, so nobody is exempt from going round it.
     */
    private static boolean ownGround(Side side, Entity hazard) {
        if (side == Side.NEUTRAL || !FACTION.has(hazard)) {
            return false;
        }
        return FACTION.get(hazard).side == side;
    }

    /**
     * Whether this man gives this ground room at all (G0076). Always, but for ground that says it is heeded only
     * on a chance - and that is drawn from the man and the spatter alone, so it is one answer for every circle of
     * it, on every tick and every machine.
     */
    private boolean heeds(Entity steerer, Collider ground) {
        return ground.heeded >= 1f || Rng.keyed(seed, ground.spatter, Handles.of(steerer)) < ground.heeded;
    }

    /** On the other side, and therefore the thing being hunted rather than something in the way. */
    private static boolean opposed(Side side, Entity hazard) {
        if (side == Side.NEUTRAL || !FACTION.has(hazard)) {
            return false;
        }
        Side other = FACTION.get(hazard).side;
        return other != Side.NEUTRAL && other != side;
    }
}
