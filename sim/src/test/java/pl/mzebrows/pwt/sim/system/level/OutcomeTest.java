package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Objective;

/**
 * A level ends on its first outcome (G0066): the tick its objectives are first all met is the win, the
 * tick G0055 loses it is the loss, and whichever lands first stands - the world runs on, and nothing that
 * happens in it afterwards changes the verdict or its time.
 */
class OutcomeTest {

    /** From the killing blow to the verdict: the body lingers, and then the return that never comes. */
    private static final int RETURN = ticksIn(DEATH_THROES + RESPAWN_DELAY) + 3;

    @Test
    void winsOnTheTickItsObjectivesAreFirstMet() {
        Simulation simulation = new Simulation(config());
        simulation.level().require(Objective.CLEAR_ENEMIES);
        Entity enemy = simulation.spawnEnemy(90f, 90f);
        steps(simulation, 5);
        assertThat(simulation.level().won).isFalse();

        simulation.engine().removeEntity(enemy);
        simulation.step();

        assertThat(simulation.level().won).isTrue();
        assertThat(simulation.level().outcomeTick).isEqualTo(simulation.level().tick);
    }

    @Test
    void losesOnTheTickTheLastCommanderIsBeyondBringingBack() {
        Simulation simulation = losing();

        assertThat(simulation.level().won).isFalse();
        assertThat(simulation.level().outcomeTick).isEqualTo(simulation.level().tick);
    }

    /** A wave walking onto a cleared field after the finale does not take the win back, nor move its time. */
    @Test
    void staysWonWhenItsObjectivesComeUndone() {
        Simulation simulation = new Simulation(config());
        simulation.level().require(Objective.CLEAR_ENEMIES);
        simulation.step();
        long won = simulation.level().outcomeTick;

        simulation.spawnEnemy(90f, 90f);
        steps(simulation, 5);

        assertThat(simulation.level().isMet(Objective.CLEAR_ENEMIES)).isFalse();
        assertThat(simulation.level().won).isTrue();
        assertThat(simulation.level().outcomeTick).isEqualTo(won);
    }

    /** What happens to the squad after the finale fell is not the level. */
    @Test
    void staysWonWhenTheSideIsWipedAfterwards() {
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.level().require(Objective.CLEAR_ENEMIES);
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.step();

        kill(commander);
        steps(simulation, RETURN);

        assertThat(simulation.level().won).isTrue();
        assertThat(simulation.level().failed).isFalse();
    }

    @Test
    void staysLostWhenItsObjectivesAreMetAfterwards() {
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.level().require(Objective.CLEAR_ENEMIES);
        Entity enemy = simulation.spawnEnemy(90f, 90f);
        kill(simulation.spawnCommander(50f, 50f));
        steps(simulation, RETURN);
        long lost = simulation.level().outcomeTick;

        simulation.engine().removeEntity(enemy);
        steps(simulation, 5);

        Level level = simulation.level();
        assertThat(level.complete()).isTrue();
        assertThat(level.won).isFalse();
        assertThat(level.failed).isTrue();
        assertThat(level.outcomeTick).isEqualTo(lost);
    }

    /**
     * Both on one tick: the loss is judged with the dead ({@code RespawnSystem}), before the objectives are
     * asked ({@code ObjectiveSystem}), so it is the one that landed first.
     */
    @Test
    void losesWhenTheLastCommanderGoesOnTheTickTheObjectivesAreMet() {
        long lostOn = losing().level().outcomeTick;
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.level().require(Objective.CLEAR_ENEMIES);
        Entity enemy = simulation.spawnEnemy(90f, 90f);
        kill(simulation.spawnCommander(50f, 50f));
        steps(simulation, (int) lostOn - 1);

        simulation.engine().removeEntity(enemy);
        simulation.step();

        assertThat(simulation.level().complete()).isTrue();
        assertThat(simulation.level().failed).isTrue();
        assertThat(simulation.level().outcomeTick).isEqualTo(lostOn);
    }

    /** A level that names an objective, with its only commander dead and nobody to bring him back - run to the loss. */
    private static Simulation losing() {
        Simulation simulation = new Simulation(reserveOf(0));
        simulation.level().require(Objective.CLEAR_ENEMIES);
        simulation.spawnEnemy(90f, 90f);
        kill(simulation.spawnCommander(50f, 50f));
        while (!simulation.level().failed) {
            simulation.step();
        }
        return simulation;
    }

    private static SimConfig reserveOf(int reserve) {
        SimConfig base = config();
        return base.withReserve(base.reserve().withStart(reserve));
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).land(0, Harm.KILL);
        entity.getComponent(Invulnerable.class).remainingTicks = 0;
    }
}
