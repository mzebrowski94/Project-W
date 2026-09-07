package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Intent;

/**
 * Components are pooled, so one that forgets a field in {@code reset()} leaks it into the next
 * entity that reuses the instance - a bug that shows up far from its cause and is miserable to
 * track down.
 *
 * <p>{@link Intent} is the one worth guarding: every other component is fully overwritten by a
 * {@code set(...)} at spawn, but an intent is spawned bare and cleared only by {@code reset()}.
 *
 * <p>The first assertion checks that the pool really did hand back the same instance. Without it a
 * change in Ashley's recycling would leave this test passing while testing nothing.
 */
class ComponentPoolingTest {

    @Test
    void handsBackAClearedIntentWhenAComponentIsRecycled() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Intent used = commander.getComponent(Intent.class);
        simulation.step(new CommanderInput(1f, 1f, 60f, 60f, true));

        simulation.engine().removeEntity(commander);
        Entity respawned = simulation.spawnCommander(20f, 20f);

        Intent recycled = respawned.getComponent(Intent.class);
        assertThat(recycled).isSameAs(used);
        assertThat(recycled.firing).isFalse();
        assertThat(recycled.moveX).isZero();
        assertThat(recycled.moveY).isZero();
        assertThat(recycled.aimX).isZero();
        assertThat(recycled.aimY).isZero();
    }
}
