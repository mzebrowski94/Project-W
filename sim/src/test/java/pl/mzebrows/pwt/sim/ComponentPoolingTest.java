package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Strike;

/**
 * Components are pooled, so one that forgets a field in {@code reset()} leaks it into the next
 * entity that reuses the instance - a bug that shows up far from its cause and is miserable to
 * track down.
 *
 * <p>{@link Intent} is one worth guarding: most components are fully overwritten by a {@code set(...)}
 * at spawn, but an intent is spawned bare and cleared only by {@code reset()}.
 *
 * <p>{@link Strike} is the second, for a different reason: it is the only pooled component in the
 * codebase holding a reference to an entity. A {@code reset()} that forgot that field would keep a
 * dead commander reachable from the pool for as long as the game ran, and nothing else would notice.
 *
 * <p>{@link Melee} is the third: its swing count decides which way the next sweep goes, and one kept
 * past its man would start the next commander on the wrong side of his aim.
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
        simulation.step(new CommanderInput(1f, 1f, 60f, 60f, true, false));

        simulation.engine().removeEntity(commander);
        Entity respawned = simulation.spawnCommander(20f, 20f);

        Intent recycled = respawned.getComponent(Intent.class);
        assertThat(recycled).isSameAs(used);
        assertThat(recycled.firing).isFalse();
        assertThat(recycled.moveX).isZero();
        assertThat(recycled.moveY).isZero();
        assertThat(recycled.aimX).isZero();
        assertThat(recycled.aimY).isZero();
        assertThat(recycled.striking).isFalse();
    }

    @Test
    void letsGoOfTheOwnerOfASwingWhenItsComponentIsRecycled() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        simulation.step(new CommanderInput(0f, 0f, 60f, 50f, false, true));
        Strike used = simulation
                .engine()
                .getEntitiesFor(Family.all(Strike.class).get())
                .first()
                .getComponent(Strike.class);

        simulation
                .engine()
                .removeEntity(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Strike.class).get())
                        .first());

        assertThat(used.owner).isEqualTo(Handle.NOBODY);
    }

    @Test
    void letsGoOfTheSwingCountWhenAWeaponIsRecycled() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Melee weapon = commander.getComponent(Melee.class);
        simulation.step(new CommanderInput(0f, 0f, 60f, 50f, false, true));
        assertThat(weapon.swingsReleased).as("he swung").isEqualTo(1);

        simulation.engine().removeEntity(commander);

        assertThat(weapon.swingsReleased).isZero();
    }
}
