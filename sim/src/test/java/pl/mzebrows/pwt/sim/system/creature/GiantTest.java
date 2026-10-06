package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.PatternDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The giants with their shipped numbers (G0071): a thumper's slam wounds and stuns a man inside its circle and
 * nobody outside it, and spares the swarm; a searer's cone wounds and slows a man it lands on.
 */
class GiantTest {

    private static final float MIDDLE = 60f;

    private static final SimConfig SHIPPED = SimConfig.defaults();

    @Test
    void slamsAManInsideItsCircleWoundedAndStunned() {
        Simulation simulation = new Simulation(giants());
        Entity man = simulation.spawnCommander(MIDDLE - inside(), MIDDLE);
        simulation.spawnUnit(UnitKind.THUMPER, MIDDLE, MIDDLE);

        steps(simulation, untilItHasFired(UnitKind.THUMPER));

        assertThat(man.getComponent(Vitality.class).wounds).isPositive();
        assertThat(man.getComponent(Stunned.class).dazed).isTrue();
    }

    @Test
    void leavesAManJustOutsideItsCircleAlone() {
        Simulation simulation = new Simulation(giants());
        float circle = SHIPPED.unit(UnitKind.THUMPER).radius() * 4f;
        Entity man = simulation.spawnCommander(MIDDLE - circle - 3f, MIDDLE);
        simulation.spawnUnit(UnitKind.THUMPER, MIDDLE, MIDDLE);

        steps(simulation, untilItHasFired(UnitKind.THUMPER));

        assertThat(man.getComponent(Vitality.class).wounds).isZero();
        assertThat(man.getComponent(Stunned.class).dazed).isFalse();
    }

    /** A shot, not a blast (G0018): what stands beside it on its own side is untouched. */
    @Test
    void sparesTheSwarmStandingInItsCircle() {
        Simulation simulation = new Simulation(giants());
        simulation.spawnCommander(MIDDLE - inside(), MIDDLE);
        simulation.spawnUnit(UnitKind.THUMPER, MIDDLE, MIDDLE);
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, MIDDLE + inside());
        int health = grunt.getComponent(Health.class).current;

        steps(simulation, untilItHasFired(UnitKind.THUMPER));

        assertThat(grunt.getComponent(Health.class).current).isEqualTo(health);
        assertThat(grunt.getComponent(Stunned.class).dazed).isFalse();
    }

    @Test
    void searsAManInItsConeWoundedAndSlowed() {
        Simulation simulation = new Simulation(giants());
        Entity man = simulation.spawnCommander(
                MIDDLE - SHIPPED.unit(UnitKind.SEARER).barrage().reach() * 0.7f, MIDDLE);
        simulation.spawnUnit(UnitKind.SEARER, MIDDLE, MIDDLE);
        PatternDef cone = SHIPPED.unit(UnitKind.SEARER).barrage().fan();

        int limit = untilItHasFired(UnitKind.SEARER) + ticksIn(cone.salvos() * cone.intervalSeconds());
        for (int tick = 0; tick < limit && man.getComponent(Vitality.class).wounds == 0; tick++) {
            simulation.step();
        }

        assertThat(man.getComponent(Vitality.class).wounds).isPositive();
        assertThat(man.getComponent(Slowed.class).factor()).isLessThan(1f);
    }

    /** The fixture's world with both giants as shipped, claws off so only the barrage can reach a man. */
    private static SimConfig giants() {
        return config().withUnit(
                        UnitKind.SEARER,
                        ignored -> SHIPPED.unit(UnitKind.SEARER).withContactDamage(0))
                .withUnit(
                        UnitKind.THUMPER,
                        ignored -> SHIPPED.unit(UnitKind.THUMPER).withContactDamage(0));
    }

    /** A man's middle inside the circle and clear of the thumper's body. */
    private static float inside() {
        return SHIPPED.unit(UnitKind.THUMPER).radius() * 3f;
    }

    /** The roar, the first salvo and a few ticks for its shots to land - a slam's flies 10 at 80. */
    private static int untilItHasFired(UnitKind giant) {
        return 2 + ticksIn(SHIPPED.unit(giant).barrage().windUpSeconds()) + ticksIn(0.25f);
    }
}
