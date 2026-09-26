package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Approach;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * Walks each trooper towards whatever it has been given to head for, sweeping its attention from side
 * to side as it goes.
 *
 * <p><strong>Urgency blends the route against the sweep, and never the speed.</strong> At one a
 * trooper makes a beeline; at zero it weaves so hard it barely advances. Speed is deliberately left
 * alone, because "forward" is the thing that changes speed - one dial doing both would make that
 * order invisible, which is the sort of overlap that quietly removes a mechanic.
 *
 * <p>The sweep is what "looking around" actually is. Leaning off the route turns the trooper, and its
 * facing is written from the lean rather than from where it happens to be walking - so a trooper that
 * stops still has somewhere it is looking, which is what the stand-still order will need.
 *
 * <p>How long a lean lasts is drawn from the squad's own stream between two bounds, so a squad does
 * not sweep in lockstep. Sweeping together reads as a chorus line rather than as men watching their
 * own arcs, and it is the sort of thing only visible by running it.
 *
 * <p>It writes a desired velocity rather than a velocity, so avoidance still gets a say and
 * {@code DriveSystem} has the last word.
 */
public final class TrooperMoveSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Approach> APPROACH = ComponentMapper.getFor(Approach.class);

    private static final float EPSILON = 1e-4f;

    /** What {@link #headingTowards} found: nothing to walk to, a straight line, or the route. */
    private static final int NOWHERE = 0;

    private static final int STRAIGHT = 1;

    private static final int BY_ROUTE = 2;

    /** A turn of a hundred and twenty degrees, as its cosine and sine: back off and across. */
    private static final float DETOUR_COS = -0.5f;

    private static final float DETOUR_SIN = 0.8660254f;

    /** Rejection sampling inside the unit circle succeeds about 79% of the time; this never runs out. */
    private static final int BEARING_ATTEMPTS = 8;

    private final TileMap map;
    private final Rng rng;
    private final float urgency;
    private final float scanSpread;
    private final float scanMinSeconds;
    private final float scanMaxSeconds;
    private final float forwardSpeedFactor;
    private final float followDistance;
    private final float walkingSpeed;
    private final int blockTicks;
    private final float blockDistance;
    private final int detourTicks;

    /** One field, re-aimed at whatever a trooper is currently walking to. */
    private final FlowField route;

    /** Scratch for one direction, so sampling a field costs no allocation per unit per tick. */
    private final float[] direction = new float[2];

    /** Scratch for the point beside where it is going, so spreading out costs no allocation either. */
    private final Position goal = new Position();

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public TrooperMoveSystem(SimConfig config, TileMap map, Rng rng) {
        this.map = map;
        this.rng = rng;
        SimConfig.TrooperConfig trooper = config.trooper();
        this.urgency = trooper.urgency();
        this.scanSpread = trooper.scanSpread();
        this.scanMinSeconds = trooper.scanMinSeconds();
        this.scanMaxSeconds = trooper.scanMaxSeconds();
        this.forwardSpeedFactor = trooper.forwardSpeedFactor();
        this.followDistance = trooper.followDistance();
        this.walkingSpeed = trooper.speed();
        this.blockTicks = Ticks.of(trooper.blockSeconds(), config.tickSeconds());
        this.blockDistance = trooper.blockDistance();
        this.detourTicks = Ticks.of(trooper.detourSeconds(), config.tickSeconds());
        this.route = FlowField.over(map, trooper.radius());
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers = engine.getEntitiesFor(Family.all(Trooper.class, Position.class, Steering.class, Speed.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            sweep(trooper, deltaTime);
            if (trooper.mode == Trooper.Mode.HOLD
                    || trooper.mode == Trooper.Mode.SECURE
                    || (trooper.mode == Trooper.Mode.AID && !trooper.hasTarget)) {
                // 3.c, a squad holding a point, and a man standing over somebody: all still watching.
                // Each asks for no velocity at all rather than for a zero one, so DriveSystem stops
                // it and a shove still moves it. A trooper aiding has nowhere to head for precisely
                // when it has arrived, so the two questions are the same question.
                keepWatching(trooper, POSITION.get(entity));
                continue;
            }
            if (!trooper.hasTarget) {
                trooper.sampling = false;
                continue;
            }
            Position at = POSITION.get(entity);
            if (trooper.mode == Trooper.Mode.FIGHT) {
                // 1.d: a trooper stands still to shoot, and looks straight at what it is shooting
                // rather than sweeping past it. It has stopped watching its own arc, so when the
                // fight ends it takes a fresh bearing rather than resuming the one it had before.
                trooper.settled = false;
                trooper.sampling = false;
                aimAt(trooper, at);
                continue;
            }
            int way = headingTowards(entity, at, trooper);
            if (way == NOWHERE) {
                trooper.sampling = false;
                continue;
            }
            if (trooper.mode == Trooper.Mode.FOLLOW && closeEnoughToTheCommander(at, trooper)) {
                // 3.a: a loose formation, not a queue on his heels. Close enough is close enough, and
                // it goes on watching while it waits.
                keepWatching(trooper, at);
                continue;
            }
            trooper.settled = false;
            trooper.baseLookX = direction[0];
            trooper.baseLookY = direction[1];
            watchForABlock(trooper, at);
            if (trooper.detourRemaining > 0) {
                trooper.detourRemaining--;
                sidestep(entity, trooper);
            } else if (way == STRAIGHT) {
                // Only in the open. Following the route means rock is close by, and a lean
                // perpendicular to a corridor is a lean into its walls.
                lean(trooper);
            }
            trooper.lookX = direction[0];
            trooper.lookY = direction[1];
            float speed = SPEED.get(entity).value * pace(trooper, deltaTime);
            STEERING.get(entity).set(direction[0] * speed, direction[1] * speed);
        }
    }

    /**
     * Goes on watching without moving: the sweep leans off the direction it was last looking, rather
     * than off wherever it happens to be walking, because it is not walking.
     *
     * <p>This is the reason a trooper carries a look direction of its own instead of inheriting one
     * from its velocity. A unit told to hold that kept pointing wherever it last moved would be a
     * unit staring at the ground it came from.
     */
    private void keepWatching(Trooper trooper, Position at) {
        trooper.sampling = false;
        if (trooper.alertRemaining > 0) {
            turnTowardsTheHit(trooper, at);
        } else if (trooper.sectorSpan > 0) {
            watchItsSector(trooper);
        } else {
            takeABearing(trooper);
        }
        direction[0] = trooper.baseLookX;
        direction[1] = trooper.baseLookY;
        lean(trooper);
        trooper.lookX = direction[0];
        trooper.lookY = direction[1];
    }

    /**
     * Faces where the last hit came from, while it is still looking for what did it. What a man does
     * when he is shot from behind: he turns round. The mind forgives his arc for the same while, so
     * whether he then finds anything is the ordinary question of range and a clear line.
     */
    private static void turnTowardsTheHit(Trooper trooper, Position at) {
        float toX = trooper.alertX - at.x;
        float toY = trooper.alertY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        trooper.settled = false;
        trooper.baseLookX = toX / distance;
        trooper.baseLookY = toY / distance;
    }

    /**
     * Looks along its own sector of the circle, one slot further each time the sweep turns over, and
     * back again at the end of the run rather than jumping across it.
     *
     * <p>The sector is handed out upstream by {@code TrooperMindSystem}, which knows who else is standing
     * on the point; this only turns through it. The sweep's lean still applies on top, so a man is
     * never staring down one exact line.
     */
    private static void watchItsSector(Trooper trooper) {
        int span = trooper.sectorSpan;
        int offset = 0;
        if (span > 1) {
            int cycle = 2 * span - 2;
            int phase = Math.floorMod(trooper.sectorStep, cycle);
            offset = phase < span ? phase : cycle - phase;
        }
        int slot = trooper.sectorFrom + offset;
        trooper.settled = true;
        trooper.baseLookX = Compass.x(slot);
        trooper.baseLookY = Compass.y(slot);
    }

    /**
     * Gives a trooper that has just stopped somewhere of its own to watch.
     *
     * <p><strong>Once per stop, and different for each man.</strong> Keeping the direction it was
     * walking in means a squad following the commander all faces him and a squad on a point all faces
     * its middle - four men looking at the same thing, which is the opposite of a squad covering its
     * surroundings. A bearing each, drawn from the squad's seeded stream, reads as men watching their
     * own arcs and still replays identically (pillar 6).
     *
     * <p>Rejection sampled inside the unit circle rather than taken from an angle, because turning an
     * angle into a direction needs trigonometry and {@code ArchitectureTest} bans it here.
     */
    private void takeABearing(Trooper trooper) {
        if (trooper.settled) {
            return;
        }
        trooper.settled = true;
        for (int attempt = 0; attempt < BEARING_ATTEMPTS; attempt++) {
            float x = rng.nextFloat(-1f, 1f);
            float y = rng.nextFloat(-1f, 1f);
            float lengthSquared = x * x + y * y;
            if (lengthSquared > EPSILON && lengthSquared <= 1f) {
                float scale = 1f / (float) Math.sqrt(lengthSquared);
                trooper.baseLookX = x * scale;
                trooper.baseLookY = y * scale;
                return;
            }
        }
        // Vanishingly unlikely. Keeping the direction it had is better than pointing at nothing.
    }

    /**
     * How much faster it is walking than usual, and spends the push as it goes.
     *
     * <p>Forward buys a <em>distance</em>, so it is charged for ground covered rather than for time
     * elapsed. A squad told to push on and then pinned by a fight gets the whole push afterwards,
     * which is what the order would look like it promised.
     */
    private float pace(Trooper trooper, float deltaTime) {
        if (!trooper.pushingForward()) {
            return 1f;
        }
        trooper.forwardRemaining -= walkingSpeed * forwardSpeedFactor * deltaTime;
        return forwardSpeedFactor;
    }

    /** Close enough to the commander that a follower stops crowding him. */
    private boolean closeEnoughToTheCommander(Position at, Trooper trooper) {
        return Sight.withinRange(at.x, at.y, trooper.targetX, trooper.targetY, followDistance);
    }

    /**
     * Turns to face what it is shooting at, without moving.
     *
     * <p>Its facing is written even though it is standing still, which is the point of a trooper
     * owning a look direction rather than inheriting one from its velocity: a stationary unit that
     * kept pointing wherever it was last walking would be aiming somewhere other than where its
     * bullets went.
     */
    private static void aimAt(Trooper trooper, Position at) {
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        trooper.look(toX / distance, toY / distance);
    }

    /**
     * Counts down to the next sweep and flips which way it is leaning.
     *
     * <p>Runs for a held trooper too - being knocked down does not stop you looking about, and it
     * keeps a squad's sweeps from all resetting together when a grenade goes off among them.
     */
    private void sweep(Trooper trooper, float deltaTime) {
        trooper.scanRemaining--;
        if (trooper.scanRemaining > 0) {
            return;
        }
        trooper.scanSide = -trooper.scanSide;
        trooper.sectorStep++;
        trooper.scanRemaining = Ticks.of(rng.nextFloat(scanMinSeconds, scanMaxSeconds), deltaTime);
    }

    /**
     * Writes the unit direction to walk into {@link #direction}, and reports how it got it: straight
     * at the place, by the route, or {@link #NOWHERE} when it is already standing on it.
     *
     * <p>Straight at it when the way is clear, and by the grid when it is not - the same order the
     * enemies' navigation uses, and for the same reason: a four-connected field's gradient in the
     * open is grid-shaped, so a squad crossing an empty arena would converge onto one row and walk
     * along it.
     */
    private int headingTowards(Entity entity, Position at, Trooper trooper) {
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        float radius = BODY.get(entity).radius;
        if (distance <= EPSILON) {
            return NOWHERE;
        }
        if (walksAbreast(trooper)) {
            // The pack's spread (G0033, G0041): a point beside where it is going, on its own side, so a
            // squad crosses the ground abreast rather than down one line. Only while the line to that
            // point is clear - out of it the route answers, and a corridor still files.
            goal.set(trooper.targetX, trooper.targetY);
            NavigationSystem.spreadOut(entity, at, goal);
            float toGoalX = goal.x - at.x;
            float toGoalY = goal.y - at.y;
            float toGoal = (float) Math.sqrt(toGoalX * toGoalX + toGoalY * toGoalY);
            if (toGoal > EPSILON
                    && (goal.x != trooper.targetX || goal.y != trooper.targetY)
                    && map.isClearPath(at.x, at.y, goal.x, goal.y, radius)) {
                direction[0] = toGoalX / toGoal;
                direction[1] = toGoalY / toGoal;
                return STRAIGHT;
            }
        }
        if (distance <= map.tileSize() || map.isClearPath(at.x, at.y, trooper.targetX, trooper.targetY, radius)) {
            direction[0] = toX / distance;
            direction[1] = toY / distance;
            return STRAIGHT;
        }
        route.rebuildFor(map.colAt(trooper.targetX), map.rowAt(trooper.targetY));
        if (route.directionAt(at.x, at.y, direction)) {
            return BY_ROUTE;
        }
        direction[0] = toX / distance;
        direction[1] = toY / distance;
        return BY_ROUTE;
    }

    /**
     * Notices a man who is walking and getting nowhere, and sends him off to one side for a moment.
     *
     * <p><strong>Block detection</strong>, as engines ship it (Unreal's path following samples where an
     * agent is at an interval and calls it blocked when the samples will not spread out): where he was
     * a while ago against where he is now. Less than the block distance in the block time is stuck.
     *
     * <p>Asking the route again would not help - it is a function of terrain, so it gives the same
     * answer - and the place he is stuck in is exactly where the route and steering are both blind:
     * an entity against rock. So the answer is a change of course rather than of plan: a short detour,
     * after which he tries his way again from somewhere else.
     */
    private void watchForABlock(Trooper trooper, Position at) {
        if (blockTicks <= 0) {
            return;
        }
        if (!trooper.sampling) {
            trooper.sampling = true;
            trooper.sampleX = at.x;
            trooper.sampleY = at.y;
            trooper.sampleClock = 0;
            return;
        }
        trooper.sampleClock++;
        if (trooper.sampleClock < blockTicks) {
            return;
        }
        boolean stuck = Sight.withinRange(at.x, at.y, trooper.sampleX, trooper.sampleY, blockDistance)
                && trooper.detourRemaining <= 0;
        trooper.sampleX = at.x;
        trooper.sampleY = at.y;
        trooper.sampleClock = 0;
        if (stuck) {
            trooper.detourRemaining = detourTicks;
            trooper.detours++;
        }
    }

    /**
     * Turns the way he wanted to go two thirds of the way round, to one side: back off and across,
     * out of whatever he walked into.
     *
     * <p>Which side is his own - the sign of the spread he was seeded with where he was put down, so
     * nothing is drawn (G0012's argument) - and it alternates with every detour, so a man who backed
     * into rock the first time goes the other way the second. The angle is a pair of literals because
     * {@code Math.sin} and {@code Math.cos} are banned here.
     */
    private void sidestep(Entity entity, Trooper trooper) {
        float own = APPROACH.has(entity) && APPROACH.get(entity).bearing < 0f ? -1f : 1f;
        float side = (trooper.detours & 1) == 1 ? own : -own;
        float wantX = direction[0];
        float wantY = direction[1];
        direction[0] = wantX * DETOUR_COS - wantY * DETOUR_SIN * side;
        direction[1] = wantX * DETOUR_SIN * side + wantY * DETOUR_COS;
    }

    /**
     * Whether this walk is one a squad makes together: to a point, or after the commander. Roaming,
     * going to a casualty and going to a gun are each a man's own errand, to a place of his own.
     */
    private static boolean walksAbreast(Trooper trooper) {
        return trooper.mode == Trooper.Mode.ADVANCE || trooper.mode == Trooper.Mode.FOLLOW;
    }

    /**
     * Leans the direction off the route by however much urgency leaves over.
     *
     * <p>The lean is perpendicular to the route, so the trooper keeps making ground while it looks:
     * a sweep that pointed it away from the objective would be a patrol rather than an advance.
     */
    private void lean(Trooper trooper) {
        float lateral = scanSpread * (1f - urgency) * trooper.scanSide;
        if (lateral == 0f) {
            return;
        }
        float leanedX = direction[0] - direction[1] * lateral;
        float leanedY = direction[1] + direction[0] * lateral;
        float length = (float) Math.sqrt(leanedX * leanedX + leanedY * leanedY);
        if (length <= EPSILON) {
            return;
        }
        direction[0] = leanedX / length;
        direction[1] = leanedY / length;
    }
}
