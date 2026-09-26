package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/**
 * Shoots, and very occasionally swings.
 *
 * <p>A trooper fires at whatever its mind has settled on, and only while it is standing still to do
 * it (1.d) - the decision about <em>what</em> it is fighting is upstream in {@code TrooperMindSystem},
 * and nothing here asks that question again.
 *
 * <p><strong>The hand strike is rare on purpose, and that is a design constraint rather than a
 * tuning choice.</strong> Answering a leap with a sweep is the commander's moment: it is the one
 * exchange in the game that rewards reading a tell, and G0013 built the whole clinch rule around it.
 * A squad that reliably swatted leapers out of the air would quietly take that away from him. So a
 * trooper tries it seldom enough to read as a lucky break rather than as a tactic - and when it does
 * land, it pays the same clinch the commander pays, because it goes through the same code.
 *
 * <p>The roll goes through {@code Odds} against {@code AiChance.SWING_AT_A_LEAP}, like every other
 * occasional behaviour in the game: seeded, so a run replays identically (pillar 6), stated per
 * second rather than per tick, and findable from the one enum that indexes all of them.
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
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
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
     * <p>Only at something <em>committed</em>. A sweep at a creature merely walking is a swat that
     * earns no clinch (G0013), and a trooper spending its swing on one would be worse than useless -
     * it would be standing there with its hands busy while the thing walked into it.
     *
     * <p>Returns whether it swung, because a trooper that has just thrown a punch is not also firing
     * a rifle in the same tick.
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
     * One pull of the trigger at what it is aiming at, whenever the gun is ready.
     *
     * <p>Through the same place the commander, a shooting alien and a gun on a mount go through, so
     * what a pull puts in the air is a property of the gun and not of who is holding it. It worked
     * out its own muzzle point until 2026-09-18 and was therefore the one shooter in the game that a
     * twin mount or a scattergun would have behaved differently in.
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
                muzzle);
        weapon.remainingTicks = weapon.cooldownTicks;
    }
}
