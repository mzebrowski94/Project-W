package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;

/**
 * Which way a thing is pointing, published for the renderer.
 *
 * <p>Pillar 5, and a prerequisite rather than a decoration: a trooper that engages what is in front
 * of it is only legible if the player can see which way "in front" is (G0015). A facing nobody can
 * see would make the arc something the player could learn only by dying to it.
 *
 * <p>The commander's facing is his <em>aim</em> and never his heading, which is G0002 restated at the
 * one place where the two would be easiest to conflate.
 */
class FacingTest {

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);

    @Test
    void facesWhereTheCommanderIsAimingRatherThanWhereHeIsWalking() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0f, 1f, 50f, 20f, false, false));

        assertFacing(commander, 0f, -1f);
    }

    @Test
    void facesTheWayAUnitThatSteersItselfIsWalking() {
        Simulation simulation = watching();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);

        steps(simulation, 2);

        assertThat(facingX(enemy)).isLessThan(0f);
    }

    @Test
    void keepsItsFacingWhenItStopsMoving() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(60f, 50f);
        steps(simulation, 2);
        float facedX = facingX(enemy);
        float facedY = facingY(enemy);

        enemy.getComponent(pl.mzebrows.pwt.sim.component.Velocity.class).set(0f, 0f);
        simulation.step();

        assertFacing(enemy, facedX, facedY);
    }

    @Test
    void publishesAFacingOfUnitLength() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0f, 0f, 80f, 90f, false, false));

        assertThat(length(commander)).isCloseTo(1f, TOLERANCE);
    }

    /**
     * A bullet has a heading and no business showing one. The renderer draws a facing whenever there
     * is one, so anything that does not steer itself has to report none rather than be filtered out
     * by the client asking what it is (G0003).
     */
    @Test
    void publishesNoFacingForSomethingThatDoesNotSteerItself() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        Entity projectile = simulation.spawnProjectile(60f, 50f, 1f, 0f);

        simulation.step();

        assertThat(length(projectile)).isZero();
    }

    @Test
    void pointsSomewhereFromTheFirstTickSoNothingIsDrawnFacingNowhere() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        assertThat(length(commander)).isCloseTo(1f, TOLERANCE);
    }

    private static void assertFacing(Entity entity, float x, float y) {
        assertThat(facingX(entity)).isCloseTo(x, TOLERANCE);
        assertThat(facingY(entity)).isCloseTo(y, TOLERANCE);
    }

    private static float facingX(Entity entity) {
        return entity.getComponent(Appearance.class).facingX;
    }

    private static float facingY(Entity entity) {
        return entity.getComponent(Appearance.class).facingY;
    }

    private static float length(Entity entity) {
        Appearance appearance = entity.getComponent(Appearance.class);
        return (float) Math.sqrt(appearance.facingX * appearance.facingX + appearance.facingY * appearance.facingY);
    }
}
