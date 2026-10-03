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
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.Guns;

/**
 * Runs a boss's barrage: winds up, fires a pattern salvo by salvo, rests, and goes on to the next
 * (G0048).
 *
 * <ul>
 *   <li><strong>It shoots what {@code PerceptionSystem} published</strong> (G0026) - whoever it is
 *       hunting, or, with nobody in range, the place it was last hurt from - and holds fire with
 *       neither. Idle keeps its place in the cycle.
 *   <li><strong>A blow does not interrupt it.</strong> A fist holds a grunt still for a moment; a boss
 *       held still by every swing would be a boss beaten by standing next to it and pressing one key.
 *       Only going down stops it.
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
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);

    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final Guns guns;

    private ImmutableArray<Entity> bosses = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

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
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < bosses.size(); i++) {
            Entity entity = bosses.get(i);
            Barrage barrage = BARRAGE.get(entity);
            if (!barrage.enabled) {
                continue;
            }
            if (Life.down(entity)) {
                fall(entity, barrage);
                continue;
            }
            Perception perception = PERCEPTION.get(entity);
            if (!perception.aggro && !perception.provoked) {
                barrage.phase = Barrage.Phase.IDLE;
                continue;
            }
            float targetX = perception.aggro ? perception.targetX : perception.provokedX;
            float targetY = perception.aggro ? perception.targetY : perception.provokedY;
            float pace = enraged(barrage, HEALTH.get(entity)) ? barrage.enragedFactor : 1f;
            advance(entity, barrage, targetX, targetY, pace, deltaTime);
        }
    }

    /**
     * Counts a boss down on the level, once, on the tick its throes begin - a source of drafts
     * (G0049). A boss is a unit whose barrage is not off, so nothing else needs to know it is one.
     */
    private void fall(Entity entity, Barrage barrage) {
        if (barrage.fallen || !Life.dying(entity)) {
            return;
        }
        barrage.fallen = true;
        for (int i = 0; i < levels.size(); i++) {
            LEVEL.get(levels.get(i)).bossesDown++;
        }
    }

    private void advance(Entity entity, Barrage barrage, float targetX, float targetY, float pace, float deltaTime) {
        switch (barrage.phase) {
            case IDLE -> windUp(barrage, pace);
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
                    windUp(barrage, pace);
                }
            }
        }
    }

    /**
     * Starts showing the next pattern that is in the barrage, from the one it is on. Going past the
     * last is going round again, which turns every pattern the other way.
     */
    private static void windUp(Barrage barrage, float pace) {
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
        for (int shot = 0; shot < shots; shot++) {
            double angle = turn + first + step * shot;
            float cos = (float) StrictMath.cos(angle);
            float sin = (float) StrictMath.sin(angle);
            float directionX = bearingX * cos - bearingY * sin;
            float directionY = bearingX * sin + bearingY * cos;
            guns.volley(entity, directionX, directionY, Tally.NOBODY);
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
