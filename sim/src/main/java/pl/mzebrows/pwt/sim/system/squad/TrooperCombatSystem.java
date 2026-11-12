package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Odds;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.TrooperGrenadeDef;
import pl.mzebrows.pwt.sim.system.combat.Guns;
import pl.mzebrows.pwt.sim.system.combat.Throws;
import pl.mzebrows.pwt.sim.system.query.Deflect;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * Shoots, and very occasionally swings.
 *
 * <p>Fires at what {@code TrooperMindSystem} settled on, standing still (1.d), and holds fire while he
 * is still closing in on something his gun does not reach. <strong>The hand strike
 * is rare on purpose</strong> - answering a leap is the commander's moment (G0013) - rolled through
 * {@code Odds} on {@code AiChance.SWING_AT_A_LEAP}. A man in something's jaws fires nothing and
 * swings at what holds him, now and then ({@code AiChance.STRUGGLE}, G0045).
 *
 * <p><strong>A man, not a machine</strong> (G0086): he fires only once he has turned to within {@code fireArcDegrees}
 * of where he aims, and each shot of his own errs by up to {@code aimErrorDegrees} either side, drawn from the squad's
 * stream. And a leap he would deflect - {@code Deflect}, keyed per leap and man - he meets with his hand strike as it
 * comes into his reach, whatever else he is doing, so it lands on the creature before its grip could.
 *
 * <p>And, once an upgrade has turned {@code AiChance.THROW_A_GRENADE} up, throws a grenade into a pack
 * instead of a shot - only where the blast is the enemy's (G0051): a pack where it lands, nobody of
 * ours near it, a clear path, and the squad's gap run out.
 */
public final class TrooperCombatSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Melee> MELEE = ComponentMapper.getFor(Melee.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);
    private static final ComponentMapper<Thrower> THROWER = ComponentMapper.getFor(Thrower.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);

    private static final float EPSILON = 1e-4f;

    private final Guns guns;
    private final Throws throwing;
    private final Odds odds;

    /** The squad's stream, for how far each of his shots errs (G0086). */
    private final Rng rng;

    private final Solid map;
    private final TrooperGrenadeDef grenade;
    private final int squadGapTicks;
    private final float tickSeconds;

    /** How far onto his aim his facing must be before he fires, as a cosine (G0086). */
    private final float fireArcCosine;

    /** How far either side of his aim a shot of his may go, in radians; zero shoots true (G0086). */
    private final float aimError;

    /** The level's seed, which a deflect is keyed from (G0086). */
    private final long levelSeed;

    /** Every leaper on the field, filed once a tick, for the leaps that come into a man's reach. */
    private final SpatialGrid leapsNear;

    @NotState("scratch refilled by every query of the leapers")
    private final Array<Entity> nearby = new Array<>();

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> aliens = new ImmutableArray<>(new Array<>());

    /** Everybody on the player's side a blast could catch: commanders and troopers, down or not. */
    private ImmutableArray<Entity> ours = new ImmutableArray<>(new Array<>());

    /** Everything with a body, for the scenery a grenade would come off. */
    private ImmutableArray<Entity> bodies = new ImmutableArray<>(new Array<>());

    private ImmutableArray<Entity> leapers = new ImmutableArray<>(new Array<>());

    /** Every entity with a handle, for finding what has a man in its jaws. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    public TrooperCombatSystem(SimConfig config, Solid map, Guns guns, Throws throwing, Odds odds, Rng rng) {
        this.guns = guns;
        this.throwing = throwing;
        this.odds = odds;
        this.rng = rng;
        this.fireArcCosine = config.trooper().fireArcCosine();
        this.aimError = config.trooper().aimErrorRadians();
        this.levelSeed = config.seed();
        this.leapsNear = new SpatialGrid(map.map().worldWidth(), map.map().worldHeight(), config.largestRadius() * 2f);
        this.map = map;
        this.grenade = config.trooper().grenade();
        this.tickSeconds = config.tickSeconds();
        this.squadGapTicks = Ticks.of(grenade.squadGapSeconds(), tickSeconds);
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers = engine.getEntitiesFor(
                Family.all(Trooper.class, Position.class, Weapon.class, Melee.class, Faction.class)
                        .get());
        leapers = engine.getEntitiesFor(Family.all(Leap.class, Position.class).get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
        aliens = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        ours = engine.getEntitiesFor(
                Family.all(Position.class).one(Commander.class, Trooper.class).get());
        bodies = engine.getEntitiesFor(
                Family.all(Collider.class, Position.class, Body.class).get());
    }

    @Override
    public void update(float deltaTime) {
        fileTheLeapers();
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.gripped(entity)) {
                struggle(entity, deltaTime);
                continue;
            }
            if (Drivers.clung(entity) && struggle(entity, deltaTime)) {
                // A leech on him (G0083): now and then he swings at it instead of fighting on.
                continue;
            }
            if (Drivers.held(entity)) {
                continue;
            }
            if (deflectALeap(entity)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            if (trooper.mode != Trooper.Mode.FIGHT || !trooper.hasTarget) {
                continue;
            }
            if (swingAtALeap(entity, deltaTime)) {
                continue;
            }
            if (throwAGrenade(entity, trooper, deltaTime)) {
                continue;
            }
            if (trooper.closing) {
                // Walking in on it: his fire would go out short, and he does not walk and shoot (1.d).
                continue;
            }
            if (trooper.fouled) {
                // A mate stands across every line he has (G0084): he holds his fire rather than shoot through him.
                continue;
            }
            fire(entity, trooper);
        }
    }

    /**
     * Tries a bare-handed sweep at something already committed to a leap, if the dice allow it.
     *
     * <p>Only at something <em>committed</em> (G0013). Returns whether it swung; if so it does not fire.
     */
    private boolean swingAtALeap(Entity entity, float deltaTime) {
        Melee melee = MELEE.get(entity);
        if (!melee.ready()) {
            return false;
        }
        Entity leaper = leapInReach(entity, melee, false);
        if (leaper == null) {
            return false;
        }
        Trooper trooper = TROOPER.get(entity);
        trooper.swingDue = odds.roll(entity, AiChance.SWING_AT_A_LEAP, trooper.swingDue, deltaTime);
        if (!Odds.spent(trooper.swingDue)) {
            return false;
        }
        Position where = POSITION.get(leaper);
        melee.beginSwing(where.x, where.y);
        return true;
    }

    /**
     * Meets a leap he deflects with his hand strike as it comes into his reach (G0086): no dice per second, the
     * keyed answer {@link Deflect} gives for that leap and him. Returns whether he swung; if so he does not fire.
     *
     * <p>Swung now, while it is still in the air, so the strike lands on it before it could take hold of him - and
     * the contact rules refuse its grip on the same answer, should it land first: the strike is recorded as spent on
     * this leap.
     */
    private boolean deflectALeap(Entity entity) {
        Melee melee = MELEE.get(entity);
        if (!melee.ready()) {
            return false;
        }
        Entity leaper = leapInReach(entity, melee, true);
        if (leaper == null) {
            return false;
        }
        Position where = POSITION.get(leaper);
        melee.beginSwing(where.x, where.y);
        // The strike now spent on this leap still answers for it as it lands, and for no other.
        Trooper trooper = TROOPER.get(entity);
        trooper.metLeaper = Handles.of(leaper);
        trooper.metLeap = LEAP.get(leaper).leaps;
        return true;
    }

    /** Every leaper not dying into the grid, once a tick, before any man asks it. */
    private void fileTheLeapers() {
        leapsNear.clear();
        for (int i = 0; i < leapers.size(); i++) {
            Entity leaper = leapers.get(i);
            if (Life.dying(leaper)) {
                continue;
            }
            Position where = POSITION.get(leaper);
            leapsNear.insert(leaper, where.x, where.y);
        }
    }

    /**
     * The nearest leap within his hand strike's reach that he would deflect, or else - {@code deflecting} false - the
     * nearest one merely committed (G0013), or null. From the grid; ties go to the lower handle, so the answer does
     * not hang on the order it files them in.
     */
    private @Nullable Entity leapInReach(Entity entity, Melee melee, boolean deflecting) {
        Position at = POSITION.get(entity);
        float reach = melee.reach + melee.radius + BODY.get(entity).radius;
        leapsNear.near(at.x, at.y, reach, nearby);
        @Nullable Entity nearest = null;
        float shortest = Float.MAX_VALUE;
        for (int i = 0; i < nearby.size; i++) {
            Entity leaper = nearby.get(i);
            Position where = POSITION.get(leaper);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float squared = dx * dx + dy * dy;
            if (squared > reach * reach
                    || squared > shortest
                    || (squared == shortest && nearest != null && Handles.of(leaper) > Handles.of(nearest))) {
                continue;
            }
            boolean answers = deflecting
                    ? Deflect.deflects(levelSeed, leaper, entity)
                    : LEAP.get(leaper).committed();
            if (answers) {
                nearest = leaper;
                shortest = squared;
            }
        }
        return nearest;
    }

    /**
     * A man in something's jaws swings at it, now and then (G0045) - and so does one with a leech on him (G0083), who
     * otherwise fights on. Returns whether he swung.
     *
     * <p><strong>Slow on purpose</strong>, through {@code Odds} on {@code AiChance.STRUGGLE}: every swing
     * lands on the thing holding him, and a man who freed himself every time would make being taken a
     * formality. The commander coming for him is meant to matter.
     */
    private boolean struggle(Entity entity, float deltaTime) {
        Melee melee = MELEE.get(entity);
        if (!melee.ready()) {
            return false;
        }
        Entity holder = Handles.resolve(handled, GRIPPED.get(entity).by);
        if (holder == null || !POSITION.has(holder)) {
            return false;
        }
        Trooper trooper = TROOPER.get(entity);
        trooper.struggleDue = odds.roll(entity, AiChance.STRUGGLE, trooper.struggleDue, deltaTime);
        if (!Odds.spent(trooper.struggleDue)) {
            return false;
        }
        Position where = POSITION.get(holder);
        melee.beginSwing(where.x, where.y);
        return true;
    }

    /**
     * Throws a grenade into the pack he is fighting, if every rule of G0051 holds and the dice allow it.
     * Returns whether he threw; if so he does not fire this tick.
     *
     * <p>The rules, cheapest first: the throw can happen at all (the chance is above zero, which draws
     * nothing), his hands are free, his own throw is ready and the squad's gap has run out; the path is
     * inside the arena and clear of rock; a pack where it lands; nobody of ours near it; no scenery in
     * the way. <strong>Only then is the chance spent</strong> (G0039), so a throw is an opportunity taken
     * now and then, never a timer. The throw is the commander's (G0019): along his bearing to what he is
     * fighting, at the throw's own distance.
     */
    private boolean throwAGrenade(Entity entity, Trooper trooper, float deltaTime) {
        if (!THROWER.has(entity) || levels.size() == 0) {
            return false;
        }
        if (!odds.possible(entity, AiChance.THROW_A_GRENADE)) {
            return false;
        }
        Thrower thrower = THROWER.get(entity);
        Level level = LEVEL.get(levels.first());
        if (!throwing.ready(entity, thrower) || Drivers.handsFull(entity) || level.tick < level.squadThrowReadyTick) {
            return false;
        }
        Position at = POSITION.get(entity);
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return false;
        }
        float directionX = toX / distance;
        float directionY = toY / distance;
        float reach = throwing.reach(entity, thrower);
        float landX = at.x + directionX * reach;
        float landY = at.y + directionY * reach;
        // It goes off on the tick its distance runs out, so up to one tick's flight past the mark.
        float overshoot = thrower.speed * tickSeconds;
        float endX = landX + directionX * overshoot;
        float endY = landY + directionY * overshoot;
        if (!insideArena(endX, endY, thrower.radius)
                || !map.isClearPath(at.x, at.y, endX, endY, thrower.radius)
                || !packAt(landX, landY, thrower.blastRadius)
                || oursNear(landX, landY, endX, endY, thrower.blastRadius + grenade.safetyMargin())
                || sceneryAcross(at.x, at.y, endX, endY, thrower.radius)) {
            return false;
        }
        trooper.throwDue = odds.roll(entity, AiChance.THROW_A_GRENADE, trooper.throwDue, deltaTime);
        if (!Odds.spent(trooper.throwDue)) {
            return false;
        }
        throwing.release(entity, thrower, directionX, directionY);
        level.squadThrowReadyTick = level.tick + squadGapTicks;
        return true;
    }

    /** Whether a thing this size ends here still wholly inside the arena, where no edge turns it. */
    private boolean insideArena(float x, float y, float radius) {
        return x - radius >= 0f
                && x + radius <= map.map().worldWidth()
                && y - radius >= 0f
                && y + radius <= map.map().worldHeight();
    }

    /** Whether at least the cluster a throw is worth stands on its feet within the blast of this point. */
    private boolean packAt(float x, float y, float blastRadius) {
        int count = 0;
        for (int i = 0; i < aliens.size(); i++) {
            Entity alien = aliens.get(i);
            if (Life.down(alien)) {
                continue;
            }
            Position where = POSITION.get(alien);
            if (Sight.withinRange(x, y, where.x, where.y, blastRadius) && ++count >= grenade.minCluster()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether anybody on the player's side - on his feet, down, or on a gun - stands within this distance
     * of where the blast can be, which is anywhere from the mark to one tick's flight past it.
     */
    private boolean oursNear(float fromX, float fromY, float toX, float toY, float distance) {
        for (int i = 0; i < ours.size(); i++) {
            Position where = POSITION.get(ours.get(i));
            if (Sight.nearLine(fromX, fromY, toX, toY, where.x, where.y, distance)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether anything a grenade comes off stands across its path: scenery, solid and never moving
     * (G0019). It passes through the living, so nothing else is asked.
     */
    private boolean sceneryAcross(float fromX, float fromY, float toX, float toY, float radius) {
        for (int i = 0; i < bodies.size(); i++) {
            Entity body = bodies.get(i);
            Collider collider = COLLIDER.get(body);
            if (!collider.solid || collider.movable) {
                continue;
            }
            Position where = POSITION.get(body);
            if (Sight.nearLine(fromX, fromY, toX, toY, where.x, where.y, BODY.get(body).radius + radius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One pull of the trigger at what it is aiming at, whenever the gun is ready and he has turned onto it.
     *
     * <p>Through {@link Guns} and {@code Muzzle}, as every other shooter. <strong>Only once his facing is within his
     * fire arc of the aim</strong>, and the shot then errs by a draw from the squad's stream (G0086) - his own error,
     * before his gun's spread; a gun on a mount he crews is the mount's, and never comes through here.
     */
    private void fire(Entity entity, Trooper trooper) {
        if (!guns.ready(entity)) {
            return;
        }
        Position at = POSITION.get(entity);
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        float aimX = toX / distance;
        float aimY = toY / distance;
        if (trooper.lookX * aimX + trooper.lookY * aimY < fireArcCosine) {
            // Still turning onto it (G0086).
            return;
        }
        if (aimError > 0f) {
            float turn = rng.nextFloat(-aimError, aimError);
            float cos = (float) StrictMath.cos(turn);
            float sin = (float) StrictMath.sin(turn);
            float erredX = aimX * cos - aimY * sin;
            aimY = aimX * sin + aimY * cos;
            aimX = erredX;
        }
        guns.pull(entity, aimX, aimY, Tally.NOBODY);
    }
}
