package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/**
 * Shoots, and very occasionally swings.
 *
 * <p>Fires at what {@code TrooperMindSystem} settled on, standing still (1.d). <strong>The hand strike
 * is rare on purpose</strong> - answering a leap is the commander's moment (G0013) - rolled through
 * {@code Odds} on {@code AiChance.SWING_AT_A_LEAP}. A man in something's jaws fires nothing and
 * swings at what holds him, now and then ({@code AiChance.STRUGGLE}, G0045).
 */
public final class TrooperCombatSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Melee> MELEE = ComponentMapper.getFor(Melee.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);

    /** Clear of its own body, so a trooper never shoots itself in the foot. */
    private static final float MUZZLE_GAP = 0.1f;

    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final Rng rng;

    /** A gun's own pattern, which is a different concern from a man's nerve and draws separately. */
    private final Rng scatter;

    /** Scratch, so working out a muzzle costs no allocation per trooper per shot. */
    private final float[] muzzle = new float[2];

    private final Chances chances;

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> leapers = new ImmutableArray<>(new Array<>());

    /** Every entity with a handle, for finding what has a man in its jaws. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    public TrooperCombatSystem(SimConfig config, Spawner spawner, Rng rng, Rng scatter) {
        this.scatter = scatter;
        this.spawner = spawner;
        this.rng = rng;
        this.chances = config.chances();
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers = engine.getEntitiesFor(
                Family.all(Trooper.class, Position.class, Weapon.class, Melee.class, Faction.class)
                        .get());
        leapers = engine.getEntitiesFor(Family.all(Leap.class, Position.class).get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
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
            if (!LEAP.get(leaper).committed() || (DYING.has(leaper) && DYING.get(leaper).begun)) {
                continue;
            }
            Position where = POSITION.get(leaper);
            if (!Sight.withinRange(at.x, at.y, where.x, where.y, reach)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            trooper.swingDue = Odds.spend(rng, trooper.swingDue, chances, AiChance.SWING_AT_A_LEAP, deltaTime);
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
        trooper.struggleDue = Odds.spend(rng, trooper.struggleDue, chances, AiChance.STRUGGLE, deltaTime);
        if (!Odds.spent(trooper.struggleDue)) {
            return;
        }
        Position where = POSITION.get(holder);
        melee.beginSwing(where.x, where.y);
    }

    /**
     * One pull of the trigger at what it is aiming at, whenever the gun is ready.
     *
     * <p>Through {@link Guns} and {@link Muzzle}, as every other shooter.
     */
    private void fire(Entity entity, Trooper trooper) {
        Weapon weapon = WEAPON.get(entity);
        if (!weapon.ready()) {
            return;
        }
        Position at = POSITION.get(entity);
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        float directionX = toX / distance;
        float directionY = toY / distance;
        Guns.volley(
                spawner,
                scatter,
                entity,
                weapon,
                at.x,
                at.y,
                BODY.get(entity).radius,
                MUZZLE_GAP,
                directionX,
                directionY,
                FACTION.get(entity).side,
                Tally.NOBODY,
                muzzle);
        weapon.remainingTicks = weapon.cooldownTicks;
    }
}
