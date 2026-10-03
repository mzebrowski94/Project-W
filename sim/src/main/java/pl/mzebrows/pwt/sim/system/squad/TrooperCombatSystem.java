package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Odds;
import pl.mzebrows.pwt.sim.SimConfig;
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

    private final TileMap map;
    private final TrooperGrenadeDef grenade;
    private final int squadGapTicks;
    private final float tickSeconds;

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

    public TrooperCombatSystem(SimConfig config, TileMap map, Guns guns, Throws throwing, Odds odds) {
        this.guns = guns;
        this.throwing = throwing;
        this.odds = odds;
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
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.gripped(entity)) {
                struggle(entity, deltaTime);
                continue;
            }
            if (Drivers.held(entity)) {
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
        Position at = POSITION.get(entity);
        float reach = melee.reach + melee.radius + BODY.get(entity).radius;
        for (int i = 0; i < leapers.size(); i++) {
            Entity leaper = leapers.get(i);
            if (!LEAP.get(leaper).committed() || Life.dying(leaper)) {
                continue;
            }
            Position where = POSITION.get(leaper);
            if (!Sight.withinRange(at.x, at.y, where.x, where.y, reach)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            trooper.swingDue = odds.roll(entity, AiChance.SWING_AT_A_LEAP, trooper.swingDue, deltaTime);
            if (!Odds.spent(trooper.swingDue)) {
                return false;
            }
            melee.beginSwing(where.x, where.y);
            return true;
        }
        return false;
    }

    /**
     * A man in something's jaws swings at it, now and then (G0045).
     *
     * <p><strong>Slow on purpose</strong>, through {@code Odds} on {@code AiChance.STRUGGLE}: every swing
     * lands on the thing holding him, and a man who freed himself every time would make being taken a
     * formality. The commander coming for him is meant to matter.
     */
    private void struggle(Entity entity, float deltaTime) {
        Melee melee = MELEE.get(entity);
        if (!melee.ready()) {
            return;
        }
        Entity holder = Handles.resolve(handled, GRIPPED.get(entity).by);
        if (holder == null || !POSITION.has(holder)) {
            return;
        }
        Trooper trooper = TROOPER.get(entity);
        trooper.struggleDue = odds.roll(entity, AiChance.STRUGGLE, trooper.struggleDue, deltaTime);
        if (!Odds.spent(trooper.struggleDue)) {
            return;
        }
        Position where = POSITION.get(holder);
        melee.beginSwing(where.x, where.y);
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
        if (!thrower.ready() || Drivers.handsFull(entity) || level.tick < level.squadThrowReadyTick) {
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
                && x + radius <= map.worldWidth()
                && y - radius >= 0f
                && y + radius <= map.worldHeight();
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
     * One pull of the trigger at what it is aiming at, whenever the gun is ready.
     *
     * <p>Through {@link Guns} and {@code Muzzle}, as every other shooter.
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
        guns.pull(entity, toX / distance, toY / distance, Tally.NOBODY);
    }
}
