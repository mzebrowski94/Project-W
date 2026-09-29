package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.A_BLOW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * A shield takes the next hit of any harm instead of the man behind it, and breaks (G0053). These write
 * straight to {@link Damage}, as contact resolution and a blast both do.
 */
class ShieldTest {

    private static final float THROW = 20f;

    private static final int A_WHILE = ticksIn(0.5f);

    @ParameterizedTest
    @EnumSource(Harm.class)
    void takesOneHitOfAnyHarmInsteadOfHim(Harm harm) {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        shield(trooper).raise();

        hit(trooper, harm);
        simulation.step();

        assertThat(unhurt(trooper)).isTrue();
        assertThat(shield(trooper).up).isFalse();
    }

    @Test
    void letsTheNextHitThroughOnceItHasBroken() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        shield(trooper).raise();

        hit(trooper, Harm.WOUND);
        simulation.step();
        hit(trooper, Harm.WOUND);
        simulation.step();

        assertThat(unhurt(trooper)).isFalse();
    }

    @Test
    void neverStacks() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        shield(trooper).raise();
        shield(trooper).raise();

        hit(trooper, Harm.WOUND);
        simulation.step();
        hit(trooper, Harm.WOUND);
        simulation.step();

        assertThat(unhurt(trooper)).isFalse();
    }

    @Test
    void turnsAStunAsReadilyAsAWound() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        shield(trooper).raise();

        hit(trooper, Harm.WOUND);
        trooper.getComponent(Damage.class).stun(A_WHILE);
        simulation.step();

        assertThat(trooper.getComponent(Stunned.class).active()).isFalse();
    }

    /** The hit is still felt; only its cost is taken away. */
    @Test
    void stillLetsTheShoveOfTheHitItTookLand() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        shield(trooper).raise();

        hit(trooper, Harm.WOUND);
        trooper.getComponent(Damage.class).shove(1f, 0f, THROW, A_WHILE, THROW);
        simulation.step();

        assertThat(trooper.getComponent(Velocity.class).x).isPositive();
    }

    @Test
    void runsOutOnItsOwnWhenItWasRaisedForAWhile() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(50f, 50f);
        shield(trooper).raiseFor(A_WHILE);

        steps(simulation, A_WHILE + 2);

        assertThat(shield(trooper).up).isFalse();
    }

    @Test
    void showsHowMuchOfItIsLeftWhereThePlayerCanSeeIt() {
        Simulation simulation = simulation();
        Entity lasting = simulation.spawnTrooper(50f, 50f);
        Entity running = simulation.spawnTrooper(20f, 20f);
        shield(lasting).raise();
        shield(running).raiseFor(A_WHILE);

        steps(simulation, A_WHILE / 2);

        assertThat(lasting.getComponent(Appearance.class).shield).isEqualTo(1f);
        assertThat(running.getComponent(Appearance.class).shield).isCloseTo(0.5f, Offset.offset(0.1f));
    }

    private static void hit(Entity entity, Harm harm) {
        entity.getComponent(Damage.class).add(A_BLOW, 0, harm);
    }

    private static Shield shield(Entity entity) {
        return entity.getComponent(Shield.class);
    }
}
