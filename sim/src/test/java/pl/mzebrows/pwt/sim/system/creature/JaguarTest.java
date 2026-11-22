package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.inRuinedGround;
import static pl.mzebrows.pwt.sim.AlphaWorlds.killed;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The jaguar (G0112), on its shipped numbers: a runner's line on a grunt's body, a quicker leap, and a burst of three
 * globs that hurt nobody and leave glue.
 */
class JaguarTest {

    private static final float MIDDLE = 50f;

    /** Inside its leap's trigger and its barrage's reach. */
    private static final float NEAR = 16f;

    /** Close enough that the glob arrives before anything else happens. */
    private static final float POINT_BLANK = 6f;

    @Test
    void leapsFasterThanARunner() {
        assertThat(flightSpeedOf(UnitKind.JAGUAR)).isGreaterThan(flightSpeedOf(UnitKind.RUNNER));
    }

    @Test
    void survivesWhatKillsARunner() {
        Simulation simulation = new Simulation(shipped());
        Entity runner = simulation.spawnUnit(UnitKind.RUNNER, MIDDLE - NEAR, MIDDLE);
        Entity jaguar = simulation.spawnUnit(UnitKind.JAGUAR, MIDDLE + NEAR, MIDDLE);
        int enough = simulation.config().healthOf(UnitKind.RUNNER);

        runner.getComponent(Damage.class).land(enough, Harm.KILL);
        jaguar.getComponent(Damage.class).land(enough, Harm.KILL);
        steps(simulation, 2);

        assertThat(killed(runner)).isTrue();
        assertThat(killed(jaguar)).isFalse();
    }

    @Test
    void hurtsNobodyItsGlobHitsAndLeavesGlueUnderHim() {
        Simulation simulation = new Simulation(shipped());
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);

        simulation.spawnProjectile(MIDDLE - POINT_BLANK, MIDDLE, 1f, 0f, WeaponKind.JAGUAR_GLOB, Side.ALIEN);
        steps(simulation, ticksIn(0.5f));

        assertThat(unhurt(man)).isTrue();
        assertThat(inRuinedGround(simulation, man)).as("glue under him").isTrue();
        assertThat(lookOfTheGroundUnder(simulation)).isEqualTo(Look.GLUE);
    }

    @Test
    void firesABurstOfThreeGlobsAtWhatItHunts() {
        Simulation simulation = new Simulation(shipped());
        simulation.spawnCommander(MIDDLE - NEAR, MIDDLE);
        godMode(simulation);
        simulation.spawnUnit(UnitKind.JAGUAR, MIDDLE + NEAR * 0.5f, MIDDLE);

        int most = 0;
        for (int tick = 0; tick < ticksIn(2f); tick++) {
            simulation.step();
            most = Math.max(most, globsInTheAir(simulation));
        }

        assertThat(most)
                .isEqualTo(simulation.config().units().of(UnitKind.JAGUAR).barrage().stream()
                        .salvos());
    }

    /** How fast one of these flies at a commander standing inside its leap's trigger. */
    private static float flightSpeedOf(UnitKind kind) {
        Simulation simulation = new Simulation(shipped());
        simulation.spawnCommander(MIDDLE - NEAR, MIDDLE);
        godMode(simulation);
        Leap leap = simulation.spawnUnit(kind, MIDDLE, MIDDLE).getComponent(Leap.class);
        for (int tick = 0; tick < ticksIn(3f) && !leap.airborne(); tick++) {
            simulation.step();
        }
        assertThat(leap.airborne()).as(kind + " in the air").isTrue();
        return leap.flightSpeed;
    }

    /** The fixture's, with the runner and the jaguar as they ship - the fixture's are plain strains. */
    private static SimConfig shipped() {
        return config().withUnit(
                        UnitKind.RUNNER, runner -> SimConfig.defaults().units().of(UnitKind.RUNNER))
                .withUnit(
                        UnitKind.JAGUAR, jaguar -> SimConfig.defaults().units().of(UnitKind.JAGUAR));
    }

    private static int globsInTheAir(Simulation simulation) {
        ImmutableArray<Entity> shots = simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class, Appearance.class).get());
        int globs = 0;
        for (int i = 0; i < shots.size(); i++) {
            globs += shots.get(i).getComponent(Appearance.class).look == Look.JAGUAR_GLOB ? 1 : 0;
        }
        return globs;
    }

    private static Look lookOfTheGroundUnder(Simulation simulation) {
        ImmutableArray<Entity> ground = simulation
                .engine()
                .getEntitiesFor(Family.all(pl.mzebrows.pwt.sim.component.Slick.class, Appearance.class)
                        .get());
        return ground.size() == 0 ? Look.NONE : ground.first().getComponent(Appearance.class).look;
    }
}
