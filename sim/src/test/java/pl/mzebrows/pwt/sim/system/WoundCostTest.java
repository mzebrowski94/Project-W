package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.A_BLOW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_GRACE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_PACE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * What a wound costs a man still on his feet (G0053): some of his pace, and time on his gun's trigger -
 * each in the one place it is read. Never his hand strike.
 */
class WoundCostTest {

    private static final CommanderInput WALKING_EAST = new CommanderInput(1f, 0f, 0f, 0f, false, false);

    private static final CommanderInput FIRING_EAST = new CommanderInput(0f, 0f, 90f, 50f, true, false);

    private static final CommanderInput SWINGING_EAST = new CommanderInput(0f, 0f, 90f, 50f, false, true);

    private static final int WALK = 30;

    @Test
    void slowsAWoundedMan() {
        Simulation whole = simulation();
        Entity fit = whole.spawnCommander(20f, 50f);
        Simulation hurt = simulation();
        Entity wounded = hurt.spawnCommander(20f, 50f);
        wound(hurt, wounded);

        steps(whole, WALK, WALKING_EAST);
        steps(hurt, WALK, WALKING_EAST);

        float fitCovered = fit.getComponent(Position.class).x - 20f;
        float woundedCovered = wounded.getComponent(Position.class).x - 20f;
        assertThat(woundedCovered).isCloseTo(fitCovered * WOUND_PACE, Offset.offset(fitCovered * 0.05f));
    }

    @Test
    void makesAWoundedManWaitLongerBetweenShots() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(20f, 50f);
        wound(simulation, commander);
        Weapon gun = commander.getComponent(Weapon.class);

        simulation.step(FIRING_EAST);

        assertThat(gun.remainingTicks).isEqualTo(Math.round(gun.cooldownTicks * WOUND_COOLDOWN));
    }

    /** The sweep is the answer to a leap; a wounded man who cannot answer one is already dead. */
    @Test
    void leavesAWoundedMansHandStrikeAlone() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(20f, 50f);
        wound(simulation, commander);
        Melee melee = commander.getComponent(Melee.class);

        simulation.step(SWINGING_EAST);

        assertThat(melee.remainingTicks).isEqualTo(melee.cooldownTicks);
    }

    @Test
    void costsAManWithNoWoundNothing() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(20f, 50f);
        Weapon gun = commander.getComponent(Weapon.class);

        simulation.step(FIRING_EAST);

        assertThat(gun.remainingTicks).isEqualTo(gun.cooldownTicks);
    }

    private static void wound(Simulation simulation, Entity man) {
        man.getComponent(Damage.class).add(A_BLOW, 0, Harm.WOUND);
        steps(simulation, ticksIn(WOUND_GRACE) + 2);
    }
}
