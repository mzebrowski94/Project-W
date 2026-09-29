package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.A_BLOW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_BLEED_OUT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_STABILISE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.willing;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * A trooper goes and picks somebody up - sometimes.
 *
 * <p>Every test that wants a rescue rigs the dice, because the base fixture has every occasional
 * behaviour switched off; the one test that wants nobody to come leaves them alone. That split is the
 * mechanic: whether a man is fetched is a roll, and only the ranking above it - a fight first, then a
 * standing order, then the body, then the level - is a rule.
 */
class AidTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 3f;

    /** Well out of reach and well inside what a trooper can see, so it has to walk. */
    private static final float ACROSS_THE_ROOM = 20f;

    /** Enough to cross that and still do the work. */
    private static final int LONG_ENOUGH_TO_WALK_AND_WORK = 150;

    private static final int LONG_ENOUGH_TO_WORK = ticksIn(CASUALTY_STABILISE) + 4;

    /** Long enough for the man to be down and for the fight to have been chosen over him. */
    private static final int WHILE_THE_ENEMY_IS_STILL_UP = 10;

    @Test
    void putsAFallenComradeBackOnHisFeet() {
        Simulation simulation = new Simulation(willing(AiChance.AID_A_TROOPER));
        Entity down = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        wound(down, A_BLOW);
        steps(simulation, LONG_ENOUGH_TO_WORK);

        assertThat(down.getComponent(Vitality.class).begun).isFalse();
        assertThat(down.getComponent(Dying.class).begun).isFalse();
    }

    @Test
    void walksAcrossToAManWhoIsDownOutOfReach() {
        Simulation simulation = new Simulation(willing(AiChance.AID_A_TROOPER));
        Entity down = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE + ACROSS_THE_ROOM, MIDDLE);

        wound(down, A_BLOW);
        steps(simulation, LONG_ENOUGH_TO_WALK_AND_WORK);

        assertThat(down.getComponent(Vitality.class).begun).isFalse();
    }

    /** The whole reason he can be a casualty at all: without this he is promised a rescue nobody makes. */
    @Test
    void picksTheCommanderUpToo() {
        Simulation simulation = new Simulation(willing(AiChance.AID_THE_COMMANDER));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        wound(commander, A_BLOW);
        steps(simulation, LONG_ENOUGH_TO_WORK);

        assertThat(commander.getComponent(Vitality.class).begun).isFalse();
        assertThat(commander.getComponent(Dying.class).begun).isFalse();
    }

    @Test
    void leavesHimWhereHeLiesWhenNobodyIsWilling() {
        Simulation simulation = simulation();
        Entity down = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        wound(down, A_BLOW);
        steps(simulation, ticksIn(CASUALTY_BLEED_OUT) + 2);

        assertThat(down.getComponent(Dying.class).begun).isTrue();
    }

    /** A fight outranks a body. A squad that turned its back on something shooting at it is a dead squad. */
    @Test
    void neverBreaksOffAFightToFetchSomebody() {
        Simulation simulation = new Simulation(willing(AiChance.AID_A_TROOPER));
        Entity down = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity helper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);
        // A brute, so it is still standing at the end of this: a fight that the trooper
        // simply wins is not a fight it stayed in.
        simulation.spawnBrute(MIDDLE + BESIDE + 10f, MIDDLE);

        wound(down, A_BLOW);
        steps(simulation, WHILE_THE_ENEMY_IS_STILL_UP);

        assertThat(mode(helper)).isEqualTo(Trooper.Mode.FIGHT);
    }

    /** An order a body on the ground could override would not be an order (G0017). */
    @Test
    void holdsWhereItWasToldToRatherThanGoingToFetchSomebody() {
        Simulation simulation = new Simulation(willing(AiChance.AID_A_TROOPER));
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity down = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);
        Entity helper = simulation.spawnTrooper(MIDDLE + BESIDE * 2f, MIDDLE);

        simulation.step(ordering(Order.DEFENCE));
        wound(down, A_BLOW);
        steps(simulation, LONG_ENOUGH_TO_WORK);

        assertThat(mode(helper)).isEqualTo(Trooper.Mode.HOLD);
        assertThat(down.getComponent(Vitality.class).begun).isTrue();
    }

    private static void wound(Entity entity, int amount) {
        entity.getComponent(Damage.class).add(amount, 0, Harm.DOWN);
    }

    private static Trooper.Mode mode(Entity trooper) {
        return trooper.getComponent(Trooper.class).mode;
    }
}
