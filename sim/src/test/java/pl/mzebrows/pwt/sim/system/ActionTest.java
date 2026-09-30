package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ACTION_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CASUALTY_STABILISE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_GRACE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;

/**
 * One key, and the game decides what it means: what is within arm's length is what is on offer. The
 * only thing on offer so far is picking a man up, which is why these read as revive tests - but what
 * is under test is the offer, the hold, and the giving up.
 */
class ActionTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 2f;

    private static final CommanderInput STILL = CommanderInput.IDLE;

    @Test
    void offersNothingWhenThereIsNothingToDo() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.NONE);
    }

    @Test
    void offersToPickUpAManWhoIsDownBesideHim() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.STABILISE);
    }

    @Test
    void offersNothingForAManWhoIsDownOutOfReach() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + ACTION_REACH * 4f, MIDDLE);

        putDown(trooper);
        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.NONE);
    }

    @Test
    void putsHimBackOnHisFeetOnceTheKeyHasBeenHeldLongEnough() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE) + 2, STILL.acting(true));

        assertThat(trooper.getComponent(Vitality.class).begun).isFalse();
        assertThat(trooper.getComponent(Dying.class).begun).isFalse();
    }

    /** Being put back on his feet clears every wound he was carrying (G0053). */
    @Test
    void clearsEveryWoundWhenHeIsPutBackOnHisFeet() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);
        trooper.getComponent(Damage.class).land(0, Harm.WOUND);
        steps(simulation, ticksIn(WOUND_GRACE) + 2);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE) + 2, STILL.acting(true));

        assertThat(trooper.getComponent(Vitality.class).wounds).isZero();
        assertThat(trooper.getComponent(Vitality.class).begun).isFalse();
    }

    @Test
    void doesNothingAtAllWhileTheKeyIsNotHeld() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE) + 2, STILL);

        assertThat(trooper.getComponent(Vitality.class).begun).isTrue();
    }

    /**
     * The work is a commitment, not a total. A version that banked the seconds would let the player
     * tap the key on every pass instead of standing there, which is the whole price of the rescue.
     */
    @Test
    void losesTheWorkDoneSoFarWhenHeLetsGo() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE) - 4, STILL.acting(true));
        simulation.step(STILL);

        assertThat(trooper.getComponent(Vitality.class).stabilised).isZero();
    }

    /** Walking away is letting go: the offer is re-decided from where he is standing, every tick. */
    @Test
    void stopsOfferingWhenHeWalksOutOfReach() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        simulation.step(STILL);
        steps(simulation, 30, new CommanderInput(-1f, 0f, 0f, 0f, false, false));

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.NONE);
    }

    /** Nothing is on offer over a corpse, or the key would do nothing and say it was doing something. */
    @Test
    void offersNothingOverAManWhoIsAlreadyGone() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        trooper.getComponent(Damage.class).land(0, Harm.KILL);
        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.NONE);
    }

    /** Pillar 5: how far through the work he is shows on the man doing it, not on the man on the ground. */
    @Test
    void showsHowFarThroughTheWorkHeIs() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE * 0.5f), STILL.acting(true));

        assertThat(commander.getComponent(Action.class).progress)
                .isGreaterThan(0.3f)
                .isLessThan(0.7f);
    }

    /**
     * The two circles that must not be confused. A man on the ground and the man working on him are
     * both drawn as "filling up", and one is a clock running out while the other is the only good
     * news on the field - so the work names a colour of its own and the bleed-out does not.
     */
    @Test
    void drawsTheWorkInItsOwnColourAndTheBleedOutInTheOrdinaryOne() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE * 0.5f), STILL.acting(true));

        assertThat(commander.getComponent(Appearance.class).chargeLook).isEqualTo(Look.MENDING);
        assertThat(trooper.getComponent(Appearance.class).chargeLook).isEqualTo(Look.NONE);
    }

    @Test
    void stopsColouringHimGreenTheMomentHeLetsGo() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity trooper = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);

        putDown(trooper);
        steps(simulation, ticksIn(CASUALTY_STABILISE * 0.5f), STILL.acting(true));
        simulation.step(STILL);

        assertThat(commander.getComponent(Appearance.class).chargeLook).isEqualTo(Look.NONE);
    }

    private static void putDown(Entity entity) {
        entity.getComponent(Damage.class).land(0, Harm.DOWN);
    }
}
