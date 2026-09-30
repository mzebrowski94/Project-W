package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BOSS_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The boss (G0048): it never walks, and it shoots a cycle of patterns - each told by a wind-up, each
 * followed by a rest, and nothing about any of it rolled.
 */
class BarrageTest {

    private static final float MIDDLE = 50f;

    /** Inside the fixture's detection range, and well clear of its body and its claws. */
    private static final float NEAR = 14f;

    /**
     * Where the commander stands for a test that counts shots: up and to the left, between two lines of
     * a twelve-shot ring, so none of them is spent on him before it is counted.
     */
    private static final float OFF_THE_LINES = NEAR * 0.7071f;

    private static final float WIND_UP = 0.5f;

    private static final float REST = 0.5f;

    private static final float INTERVAL = 0.25f;

    private static final int RING_SHOTS = 12;

    private static final float TURN_DEGREES = 30f;

    private static final float FAN_DEGREES = 60f;

    private static final float ENRAGED_BELOW = 0.5f;

    private static final float ENRAGED_FACTOR = 0.5f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    /** Until the first salvo of a pattern the barrage has just started winding up: one tick to start, then the wind-up. */
    private static final int UNTIL_THE_FIRST_SALVO = 1 + ticksIn(WIND_UP);

    // ---- holding fire, and telling what is coming ----

    @Test
    void holdsFireWithNobodyToShootAt() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO * 4);

        assertThat(alienShots(simulation)).isEmpty();
    }

    @Test
    void showsItIsWindingUpBeforeAnythingLeaves() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO - 1);

        assertThat(alienShots(simulation)).isEmpty();
        assertThat(boss.getComponent(Appearance.class).state).isEqualTo(Appearance.State.WINDING_UP);

        simulation.step();

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    // ---- the shapes ----

    /** A full ring: evenly all the way round, so the shots cancel out and none leaves twice. */
    @Test
    void firesARingEvenlyAllTheWayRound() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        float sumX = 0f;
        float sumY = 0f;
        for (Entity shot : alienShots(simulation)) {
            Velocity velocity = shot.getComponent(Velocity.class);
            sumX += velocity.x;
            sumY += velocity.y;
        }
        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
        assertThat(sumX).isCloseTo(0f, Offset.offset(0.01f));
        assertThat(sumY).isCloseTo(0f, Offset.offset(0.01f));
    }

    /** An aimed fan leaves centred on whoever it is hunting, and its two edges are the arc apart. */
    @Test
    void centresAnAimedFanOnWhoeverItIsHunting() {
        Simulation simulation = new Simulation(barraging(fan(3)));
        simulation.spawnCommander(MIDDLE + NEAR, MIDDLE + NEAR);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        List<Entity> shots = alienShots(simulation);
        assertThat(shots).hasSize(3);
        float[] middle = direction(shots.get(1));
        float diagonal = (float) Math.sqrt(0.5);
        assertThat(middle[0]).isCloseTo(diagonal, CLOSE);
        assertThat(middle[1]).isCloseTo(diagonal, CLOSE);
        float[] left = direction(shots.getFirst());
        float[] right = direction(shots.getLast());
        float cosine = left[0] * right[0] + left[1] * right[1];
        assertThat(cosine).isCloseTo((float) StrictMath.cos(StrictMath.toRadians(FAN_DEGREES)), CLOSE);
    }

    // ---- the cycle ----

    @Test
    void goesOnToTheNextPatternAfterItsRest() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1), fan(3)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        Barrage barrage = boss.getComponent(Barrage.class);

        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(barrage.phase).isEqualTo(Barrage.Phase.RESTING);

        steps(simulation, ticksIn(REST));

        assertThat(barrage.phase).isEqualTo(Barrage.Phase.WINDING_UP);
        assertThat(barrage.pattern).isEqualTo(1);
    }

    @Test
    void firesEverySalvoOfAPatternAtItsInterval() {
        int salvos = 3;
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, salvos)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_THE_FIRST_SALVO + ticksIn(INTERVAL) - 1);

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);

        simulation.step();

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS * 2);
    }

    /**
     * A turning pattern turns one way the first time through the cycle and the other way the next, so a
     * spiral is never learnt as a single direction to circle in.
     */
    @Test
    void turnsThePatternTheOtherWayEachTimeRound() {
        Simulation simulation = new Simulation(barraging(ring(1, 2)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        int cycle = ticksIn(INTERVAL) + ticksIn(REST) + ticksIn(WIND_UP);

        steps(simulation, UNTIL_THE_FIRST_SALVO + ticksIn(INTERVAL));
        float firstTimeRound = direction(newest(simulation))[1];
        steps(simulation, cycle);
        float secondTimeRound = direction(newest(simulation))[1];

        assertThat(firstTimeRound).isPositive();
        assertThat(secondTimeRound).isNegative();
        assertThat(secondTimeRound).isCloseTo(-firstTimeRound, CLOSE);
    }

    // ---- what does not stop it, and what makes it faster ----

    /** A fist holds a grunt still; a boss held still by every swing would be beaten by one key. */
    @Test
    void keepsFiringWhileSomethingHoldsItStill() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        boss.getComponent(Stunned.class).apply(UNTIL_THE_FIRST_SALVO * 10, 0f);
        steps(simulation, UNTIL_THE_FIRST_SALVO);

        assertThat(alienShots(simulation)).isNotEmpty();
    }

    @Test
    void windsUpFasterOnceItIsBadlyHurt() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        boss.getComponent(Health.class).current = (int) (BOSS_HEALTH * ENRAGED_BELOW * 0.5f);

        steps(simulation, 1 + Math.round(ticksIn(WIND_UP) * ENRAGED_FACTOR));

        assertThat(alienShots(simulation)).hasSize(RING_SHOTS);
    }

    // ---- standing still, and saying how much is left ----

    @Test
    void neverWalksAndCannotBePushed() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - NEAR, MIDDLE);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);

        steps(simulation, 90, new CommanderInput(1f, 0f, MIDDLE, MIDDLE, false, false));

        Position at = boss.getComponent(Position.class);
        assertThat(at.x).isEqualTo(MIDDLE);
        assertThat(at.y).isEqualTo(MIDDLE);
    }

    /** Pillar 5: nobody can count thousands of points of damage by eye, so the core is what is left. */
    @Test
    void showsHowMuchOfItIsLeftOnItsCharge() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        boss.getComponent(Health.class).current = BOSS_HEALTH / 4;

        simulation.step();

        assertThat(boss.getComponent(Appearance.class).charge).isCloseTo(0.25f, CLOSE);
    }

    /** A boss going down is counted on the level once, on the tick its throes begin - a source of drafts (G0049). */
    @Test
    void countsABossDownOnceWhenItsThroesBegin() {
        Simulation simulation = new Simulation(barraging(ring(RING_SHOTS, 1)));
        simulation.spawnCommander(MIDDLE - OFF_THE_LINES, MIDDLE + OFF_THE_LINES);
        Entity boss = simulation.spawnUnit(UnitKind.BOSS_GRUNT, MIDDLE, MIDDLE);
        steps(simulation, 2);
        int before = simulation.level().bossesDown;

        boss.getComponent(Damage.class).land(BOSS_HEALTH * 10, Harm.KILL);
        steps(simulation, 10);

        assertThat(before).isZero();
        assertThat(simulation.level().bossesDown).isEqualTo(1);
    }

    private static SimConfig barraging(PatternDef first) {
        return barraging(first, PatternDef.off());
    }

    private static SimConfig barraging(PatternDef first, PatternDef second) {
        BarrageDef barrage = new BarrageDef(
                true, WIND_UP, REST, ENRAGED_BELOW, ENRAGED_FACTOR, first, second, PatternDef.off(), PatternDef.off());
        return config().withUnit(
                        UnitKind.BOSS_GRUNT, boss -> boss.withContactDamage(0).withBarrage(barrage));
    }

    private static PatternDef ring(int shots, int salvos) {
        return new PatternDef(true, WeaponKind.SPEW, shots, 360f, false, TURN_DEGREES, salvos, INTERVAL);
    }

    private static PatternDef fan(int shots) {
        return new PatternDef(true, WeaponKind.SPEW, shots, FAN_DEGREES, true, 0f, 1, INTERVAL);
    }

    private static List<Entity> alienShots(Simulation simulation) {
        List<Entity> shots = new ArrayList<>();
        for (Entity entity : simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class, Faction.class).get())) {
            if (entity.getComponent(Faction.class).side == Side.ALIEN) {
                shots.add(entity);
            }
        }
        return shots;
    }

    private static Entity newest(Simulation simulation) {
        return alienShots(simulation).getLast();
    }

    private static float[] direction(Entity shot) {
        Velocity velocity = shot.getComponent(Velocity.class);
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return new float[] {velocity.x / speed, velocity.y / speed};
    }
}
