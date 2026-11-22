package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIRE_PERIOD;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIRE_WAKE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Emplacement;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Cycle;
import pl.mzebrows.pwt.sim.def.CycleDef;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * An emplacement fires a fixed cycle (G0113): one step of unaimed shots a period while a man on his feet is near, the
 * same pattern for the whole level; its shots hurt only the men; and it is never an enemy - nothing to clear, nothing
 * to aim at.
 */
class EmplacementTest {

    private static final float MIDDLE = 50f;

    /** Inside its wake, off every bearing of the cycles below. */
    private static final float NEAR_X = MIDDLE + 9f;

    private static final float NEAR_Y = MIDDLE + 5f;

    /** North, then east: compass points 0 and 2. */
    private static final Cycle NORTH_THEN_EAST = new Cycle(1L | (4L << Cycle.POINTS), 2);

    private static final Cycle NORTH = new Cycle(1L, 1);

    @Test
    void firesOneShotAPeriodAlongTheNextBearingOfItsCycle() {
        Simulation simulation = harmless();
        simulation.spawnEmplacement(EmplacementKind.SPIRE, MIDDLE, MIDDLE, NORTH_THEN_EAST);
        simulation.spawnCommander(NEAR_X, NEAR_Y);
        int period = ticksIn(SPIRE_PERIOD);

        steps(simulation, period - 1);
        int early = shots(simulation).size();
        simulation.step();
        List<Entity> first = shots(simulation);
        steps(simulation, period);
        List<Entity> both = shots(simulation);

        assertThat(early).as("a tick short of a period").isZero();
        assertThat(first).hasSize(1);
        assertThat(heading(first.getFirst())).containsExactly(0f, 1f);
        assertThat(both).hasSize(2);
        assertThat(both).extracting(EmplacementTest::heading).contains(new float[] {1f, 0f});
    }

    @Test
    void goesRoundItsCycleAndStartsAgain() {
        Simulation simulation = harmless();
        Entity spire = simulation.spawnEmplacement(EmplacementKind.SPIRE, MIDDLE, MIDDLE, NORTH_THEN_EAST);
        simulation.spawnCommander(NEAR_X, NEAR_Y);

        steps(simulation, ticksIn(SPIRE_PERIOD) * 2);

        assertThat(spire.getComponent(Emplacement.class).next).isZero();
        assertThat(spire.getComponent(Emplacement.class).nextPoints()).isEqualTo(NORTH_THEN_EAST.points(0));
    }

    @Test
    void idlesWithNobodyNearAndKeepsItsPlaceInTheCycle() {
        Simulation simulation = harmless();
        Entity spire = simulation.spawnEmplacement(EmplacementKind.SPIRE, MIDDLE, MIDDLE, NORTH_THEN_EAST);
        simulation.spawnCommander(MIDDLE - SPIRE_WAKE_RADIUS - 5f, MIDDLE);

        steps(simulation, ticksIn(SPIRE_PERIOD) * 3);

        Emplacement emplacement = spire.getComponent(Emplacement.class);
        assertThat(shots(simulation)).isEmpty();
        assertThat(emplacement.next).isZero();
        assertThat(emplacement.remainingTicks).isEqualTo(emplacement.periodTicks);
    }

    @Test
    void itsShotWoundsACommanderAndPassesAnAlien() {
        Simulation simulation = new Simulation(
                everyStrain(config(), strain -> strain.withContactDamage(0).withSpeed(0f)));
        simulation.spawnEmplacement(EmplacementKind.SPIRE, MIDDLE, MIDDLE, NORTH);
        Entity alien = simulation.spawnEnemy(MIDDLE, MIDDLE + 5f);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE + 12f);

        steps(simulation, ticksIn(SPIRE_PERIOD) + ticksIn(1f));

        assertThat(alien.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(unhurt(commander)).isFalse();
    }

    @Test
    void isNeverAnEnemyToClearNorATargetATrooperShootsAt() {
        Simulation simulation = harmless();
        simulation.spawnEmplacement(EmplacementKind.SPIRE, MIDDLE, MIDDLE, NORTH);
        simulation.spawnTrooper(NEAR_X, NEAR_Y);
        simulation.level().require(Objective.CLEAR_ENEMIES);

        steps(simulation, ticksIn(SPIRE_PERIOD) * 3);

        assertThat(simulation.level().isMet(Objective.CLEAR_ENEMIES)).isTrue();
        assertThat(sides(simulation)).doesNotContain(Side.PLAYER).contains(Side.ALIEN);
    }

    @Test
    void aVolleyFiresEveryBearingTogetherOnceSomethingWakesIt() {
        SimConfig config = config();
        config = config.withEmplacements(config.emplacements()
                .map(
                        EmplacementKind.SPIRE,
                        def -> def.withWakeRadius(0f).withCycle(new CycleDef.Volley(List.of(0, 90, 180, 270)))));
        Simulation simulation = new Simulation(config);
        Cycle volley = config.emplacement(EmplacementKind.SPIRE).cycle().deal(new Rng(1L));
        Entity warden = simulation.spawnEmplacement(EmplacementKind.SPIRE, MIDDLE, MIDDLE, volley);
        simulation.spawnCommander(NEAR_X, NEAR_Y);
        int period = ticksIn(SPIRE_PERIOD);

        steps(simulation, period);
        int asleep = shots(simulation).size();
        warden.getComponent(Emplacement.class).awake = true;
        steps(simulation, period);

        assertThat(asleep).as("a man near does not wake it").isZero();
        assertThat(shots(simulation))
                .extracting(EmplacementTest::heading)
                .containsExactlyInAnyOrder(
                        new float[] {0f, 1f}, new float[] {1f, 0f}, new float[] {0f, -1f}, new float[] {-1f, 0f});
    }

    private static List<Entity> shots(Simulation simulation) {
        List<Entity> shots = new ArrayList<>();
        for (Entity shot :
                simulation.engine().getEntitiesFor(Family.all(Projectile.class).get())) {
            shots.add(shot);
        }
        return shots;
    }

    private static List<Side> sides(Simulation simulation) {
        List<Side> sides = new ArrayList<>();
        for (Entity shot : shots(simulation)) {
            sides.add(shot.getComponent(Faction.class).side);
        }
        return sides;
    }

    /** Which way a shot flies, rounded to the nearest thousandth of a unit vector so a compass point compares. */
    private static float[] heading(Entity shot) {
        Velocity velocity = shot.getComponent(Velocity.class);
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return new float[] {round(velocity.x / speed), round(velocity.y / speed)};
    }

    private static float round(float value) {
        return Math.round(value * 1000f) / 1000f + 0f;
    }
}
