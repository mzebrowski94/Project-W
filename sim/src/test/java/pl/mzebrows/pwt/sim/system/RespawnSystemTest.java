package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_MARGIN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Respawning;

/**
 * Dying and coming back. The commander is genuinely removed in between - no half-alive state that
 * every other family would then have to exclude - and a ticket entity holds the countdown for it.
 *
 * <p>A killing blow no longer removes the body at once: it lies where it fell for its death throes
 * first, and only then does the respawn countdown start. The two waits are sequential, not
 * concurrent, so everything here measures from the blow to the return.
 */
class RespawnSystemTest {

    /** From the killing blow to being back on his feet: the body lingers, and then he is away. */
    private static final float RETURN_DELAY = DEATH_THROES + RESPAWN_DELAY;

    @Test
    void takesTheCommanderOffTheFieldOnceItHasFinishedDying() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        kill(commander);
        steps(simulation, ticksIn(DEATH_THROES) + 2);

        assertThat(count(simulation, Commander.class)).isZero();
    }

    @Test
    void leavesItOffTheFieldUntilTheDelayHasPassed() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(RETURN_DELAY) - 2);

        assertThat(count(simulation, Commander.class)).isZero();
    }

    @Test
    void bringsItBackOnceTheDelayHasPassed() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(RETURN_DELAY) + 2);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    @Test
    void bringsItBackAtTheLeftOfTheArena() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(90f, 90f));

        steps(simulation, ticksIn(RETURN_DELAY) + 2);

        Position position = commander(simulation).getComponent(Position.class);
        assertThat(position.x).isEqualTo(RESPAWN_MARGIN);
        assertThat(position.y).isEqualTo(ARENA * 0.5f);
    }

    @Test
    void bringsItBackAtFullHealth() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(RETURN_DELAY) + 2);

        assertThat(commander(simulation).getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH);
    }

    /** Otherwise it reappears inside whatever killed it and dies again before the player can move. */
    @Test
    void bringsItBackBrieflyInvulnerable() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(RETURN_DELAY) + 2);

        assertThat(commander(simulation).getComponent(Invulnerable.class).active())
                .isTrue();
    }

    /** The countdown outlives the unit that started it, so it cannot be carried by a dead entity. */
    @Test
    void leavesNothingBehindOnceTheCommanderIsBack() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(RETURN_DELAY) + 4);

        assertThat(count(simulation, Respawning.class)).isZero();
        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    @Test
    void bringsItBackAgainEveryTimeItDies() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));
        steps(simulation, ticksIn(RETURN_DELAY) + 2);

        kill(commander(simulation));
        steps(simulation, ticksIn(RETURN_DELAY) + 2);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        entity.getComponent(Invulnerable.class).remainingSeconds = 0f;
    }

    private static Entity commander(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first();
    }
}
