package pl.mzebrows.pwt.sim.system.creature;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.combat.Guns;

/**
 * Runs a boss's or a giant's barrage: winds up, fires a pattern salvo by salvo, rests, and goes on to the next
 * (G0048).
 *
 * <ul>
 *   <li><strong>It shoots what {@code PerceptionSystem} published</strong> (G0026) - whoever it is
 *       hunting, or, with nobody in range, the place it was last hurt from - and holds fire with
 *       neither. A place it was only roused to (a point being taken, a nest being hit) is not one. Idle keeps its place in the cycle. Where it shoots is published on the barrage, and
 *       is the way it faces.
 *   <li><strong>A blow does not interrupt it.</strong> A fist holds a grunt still for a moment; a boss
 *       held still by every swing would be a boss beaten by standing next to it and pressing one key.
 *       Only going down stops it - or, for a barrage that says so, a declared stun.
 *   <li><strong>Nothing is rolled.</strong> The cycle, the turn and the timings are all counted, so a
 *       barrage can be learnt and replays exactly (pillar 6).
 * </ul>
 *
 * <p>Its guns are named by the pattern and put in its hands for the salvo, so every shot leaves
 * through the one trigger every other shooter pulls ({@link Guns}) - the same muzzle, the same gap and
 * the same upgrades - while the pattern, not the gun, keeps the time: nothing here waits on a cooldown
 * or runs hot.
 */
public final class BarrageSystem extends EntitySystem {

    private static final ComponentMapper<Barrage> BARRAGE = ComponentMapper.getFor(Barrage.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Shake> SHAKE = ComponentMapper.getFor(Shake.class);

    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final Guns guns;

    private ImmutableArray<Entity> bosses = new ImmutableArray<>(new Array<>());

    public BarrageSystem(Spawner spawner, Guns guns) {
        this.spawner = spawner;
        this.guns = guns;
    }

    @Override
    public void addedToEngine(Engine engine) {
        bosses = engine.getEntitiesFor(Family.all(
                        Barrage.class,
                        Position.class,
                        Body.class,
                        Weapon.class,
                        Faction.class,
                        Health.class,
                        Perception.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < bosses.size(); i++) {
            Entity entity = bosses.get(i);
            Barrage barrage = BARRAGE.get(entity);
            if (!barrage.enabled) {
                continue;
            }
            if (barrage.burstTicks > 0) {
                barrage.burstTicks--;
            }
            if (Life.down(entity)) {
                continue;
            }
            Perception perception = PERCEPTION.get(entity);
            boolean hunting = perception.aggro || perception.hurtFromAPlace();
            if (!hunting && !committed(barrage)) {
                barrage.phase = Barrage.Phase.IDLE;
                continue;
            }
            if (cancelledByStun(entity, barrage)) {
                continue;
            }
            Position at = POSITION.get(entity);
            if (hunting && !locked(barrage)) {
                float huntedX = perception.aggro ? perception.targetX : perception.provokedX;
                float huntedY = perception.aggro ? perception.targetY : perception.provokedY;
                barrage.aimX = huntedX - at.x;
                barrage.aimY = huntedY - at.y;
            }
            // Lost while winding up or firing, it finishes what it started at the place it last aimed (G0071).
            float targetX = at.x + barrage.aimX;
            float targetY = at.y + barrage.aimY;
            boolean inReach = barrage.reach <= 0f
                    || barrage.aimX * barrage.aimX + barrage.aimY * barrage.aimY <= barrage.reach * barrage.reach;
            float pace = enraged(barrage, HEALTH.get(entity)) ? barrage.enragedFactor : 1f;
            advance(entity, barrage, targetX, targetY, inReach, pace, deltaTime);
        }
    }

    /**
     * Whether it has started a pattern - wound up or firing - which it then finishes whoever walks away: a roar is
     * always answered (G0071). Only a declared stun, for a barrage that says so, or going down stops one.
     */
    private static boolean committed(Barrage barrage) {
        return barrage.aiming() && (barrage.phase == Barrage.Phase.WINDING_UP || barrage.phase == Barrage.Phase.FIRING);
    }

    /** Whether it is firing a pattern that keeps the bearing its wind-up ended on (G0073). */
    private static boolean locked(Barrage barrage) {
        return barrage.phase == Barrage.Phase.FIRING && barrage.current().locked();
    }

    /**
     * A declared stun breaks off a barrage that says so: it goes back to idle on the pattern it was on, and
     * winds that one up again from the start once the stun is over. A stagger or a lock does not - only a
     * stun something declared (G0053), shock among them.
     */
    private static boolean cancelledByStun(Entity entity, Barrage barrage) {
        if (!barrage.stunCancels || !STUNNED.has(entity)) {
            return false;
        }
        Stunned stunned = STUNNED.get(entity);
        if (!stunned.active() || !stunned.dazed) {
            return false;
        }
        barrage.phase = Barrage.Phase.IDLE;
        return true;
    }

    private void advance(
            Entity entity,
            Barrage barrage,
            float targetX,
            float targetY,
            boolean inReach,
            float pace,
            float deltaTime) {
        switch (barrage.phase) {
            case IDLE -> {
                if (inReach) {
                    windUp(entity, barrage, pace);
                }
            }
            case WINDING_UP -> {
                barrage.remainingTicks--;
                if (barrage.remainingTicks <= 0) {
                    barrage.phase = Barrage.Phase.FIRING;
                    barrage.salvo = barrage.current().salvos();
                    salvo(entity, barrage, targetX, targetY, pace, deltaTime);
                }
            }
            case FIRING -> {
                barrage.remainingTicks--;
                if (barrage.remainingTicks <= 0) {
                    salvo(entity, barrage, targetX, targetY, pace, deltaTime);
                }
            }
            case RESTING -> {
                barrage.remainingTicks--;
                if (barrage.remainingTicks <= 0) {
                    barrage.pattern++;
                    if (inReach) {
                        windUp(entity, barrage, pace);
                    } else {
                        barrage.phase = Barrage.Phase.IDLE;
                    }
                }
            }
        }
    }

    /**
     * Starts showing the next pattern that is in the barrage, from the one it is on. Going past the
     * last is going round again, which turns every pattern the other way. A barrage that roars shakes the ground
     * as it starts.
     */
    private static void windUp(Entity entity, Barrage barrage, float pace) {
        if (barrage.roarStrength > 0f && SHAKE.has(entity)) {
            SHAKE.get(entity).set(barrage.roarStrength, barrage.roarRadius, barrage.roarTicks);
        }
        for (int looked = 0; looked < BarrageDef.PATTERNS && !barrage.current().enabled(); looked++) {
            barrage.pattern++;
        }
        if (barrage.pattern >= BarrageDef.PATTERNS) {
            barrage.pattern -= BarrageDef.PATTERNS;
            barrage.cycles++;
        }
        barrage.phase = Barrage.Phase.WINDING_UP;
        barrage.phaseTicks = scaled(barrage.windUpTicks, pace);
        barrage.remainingTicks = barrage.phaseTicks;
    }

    /** One salvo of the current pattern, and the wait for the next - or, after the last, the rest. */
    private void salvo(Entity entity, Barrage barrage, float targetX, float targetY, float pace, float deltaTime) {
        PatternDef pattern = barrage.current();
        fire(entity, barrage, pattern, targetX, targetY);
        if (pattern.burstSeconds() > 0f) {
            // The ground its shots cover, drawn round it for a moment: from its middle, past its body and the
            // gap every shot leaves by, to the end of the gun's reach.
            barrage.burstReach = guns.reach(entity);
            barrage.burstTotal = Ticks.of(pattern.burstSeconds(), deltaTime);
            barrage.burstTicks = barrage.burstTotal;
        }
        barrage.salvo--;
        if (barrage.salvo > 0) {
            barrage.remainingTicks = scaled(Ticks.of(pattern.intervalSeconds(), deltaTime), pace);
            return;
        }
        barrage.phase = Barrage.Phase.RESTING;
        barrage.phaseTicks = scaled(barrage.restTicks, pace);
        barrage.remainingTicks = barrage.phaseTicks;
    }

    /**
     * Every shot of one salvo: spread across the pattern's arc round its bearing, turned by how many
     * salvos of it have already gone.
     *
     * <p>A ring spaces its shots a full turn apart with none doubled up; anything narrower puts one at
     * each edge. Rotation is by {@code StrictMath}, never by {@code Math} (pillar 6).
     */
    private void fire(Entity entity, Barrage barrage, PatternDef pattern, float targetX, float targetY) {
        Position at = POSITION.get(entity);
        float bearingX = 1f;
        float bearingY = 0f;
        if (pattern.aimed()) {
            float toX = targetX - at.x;
            float toY = targetY - at.y;
            float distance = (float) Math.sqrt(toX * toX + toY * toY);
            if (distance > EPSILON) {
                bearingX = toX / distance;
                bearingY = toY / distance;
            }
        }
        int shots = pattern.shots();
        double arc = StrictMath.toRadians(pattern.arcDegrees());
        double step = pattern.ring() ? arc / shots : shots > 1 ? arc / (shots - 1) : 0.0;
        double first = pattern.ring() || shots == 1 ? 0.0 : -arc * 0.5;
        int fired = pattern.salvos() - barrage.salvo;
        double turn = StrictMath.toRadians(pattern.turnDegrees()) * fired * barrage.spin();
        if (WEAPON.get(entity).kind != pattern.gun()) {
            spawner.applyWeapon(entity, pattern.gun());
        }
        // A stepped pattern lays its salvos along the way to its aim, the last reaching it (G0073).
        float stopAt = 0f;
        if (pattern.stepped()) {
            float toX = targetX - at.x;
            float toY = targetY - at.y;
            // Never zero, which would be the gun's whole reach: an aim on top of it stops at its mouth.
            stopAt = Math.max(
                    (float) Math.sqrt(toX * toX + toY * toY) * (fired + 1) / pattern.salvos(), Float.MIN_VALUE);
        }
        for (int shot = 0; shot < shots; shot++) {
            double angle = turn + first + step * shot;
            float cos = (float) StrictMath.cos(angle);
            float sin = (float) StrictMath.sin(angle);
            float directionX = bearingX * cos - bearingY * sin;
            float directionY = bearingX * sin + bearingY * cos;
            guns.volley(entity, directionX, directionY, Tally.NOBODY, stopAt);
        }
    }

    /** Whether it has been hurt enough to work faster. Zero never is. */
    private static boolean enraged(Barrage barrage, Health health) {
        return barrage.enragedBelow > 0f && health.fraction() <= barrage.enragedBelow;
    }

    /** A number of ticks at this pace, never less than one: a wait of nothing would fire every tick. */
    private static int scaled(int ticks, float pace) {
        int paced = Math.round(ticks * pace);
        return paced < 1 ? 1 : paced;
    }
}
