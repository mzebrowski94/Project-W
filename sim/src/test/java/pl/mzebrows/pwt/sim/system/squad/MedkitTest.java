package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MEDKIT_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WOUND_GRACE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A medkit clears every wound, and is worth nothing to anybody without one (G0053, G0006 as amended).
 * A wounded trooper with nothing better to do walks to one within reach.
 */
class MedkitTest {

    private static final float MIDDLE = 50f;

    private static final int A_MOMENT = 5;

    /** Long enough for a trooper to cross the whole of his reach at his pace, and then some. */
    private static final int A_WALK = ticksIn(3f);

    @Test
    void clearsEveryWoundOfAWoundedManWhoWalksOntoIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        wound(simulation, commander);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        steps(simulation, A_MOMENT);

        assertThat(commander.getComponent(Vitality.class).wounds).isZero();
        assertThat(count(simulation, Pickup.class)).isZero();
    }

    @Test
    void isLeftStandingByAManWithoutAWound() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        steps(simulation, A_MOMENT);

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    @Test
    void isLeftStandingByTheAliens() {
        Simulation simulation = harmless();
        simulation.spawnEnemy(MIDDLE, MIDDLE);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        steps(simulation, A_MOMENT);

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * A man it is worth nothing to never claims it, so the one standing on it cannot take it from under
     * the one it would mend.
     */
    @Test
    void goesToTheWoundedManOfTwoStandingOnIt() {
        Simulation simulation = simulation();
        Entity whole = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity wounded = simulation.spawnTrooper(MIDDLE + 4f, MIDDLE);
        wound(simulation, wounded);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        steps(simulation, A_WALK);

        assertThat(wounded.getComponent(Vitality.class).wounds).isZero();
        assertThat(whole.getComponent(Vitality.class).wounds).isZero();
    }

    @Test
    void walksAWoundedTrooperToAMedkitWithinReach() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(MIDDLE - MEDKIT_REACH * 0.5f, MIDDLE);
        wound(simulation, trooper);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        steps(simulation, A_WALK);

        assertThat(trooper.getComponent(Vitality.class).wounds).isZero();
    }

    @Test
    void leavesAMedkitOutOfReachWhereItLies() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(MIDDLE - MEDKIT_REACH * 1.5f, MIDDLE);
        wound(simulation, trooper);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        simulation.step();

        assertThat(trooper.getComponent(Trooper.class).mode).isNotEqualTo(Trooper.Mode.MEND);
    }

    @Test
    void sendsNobodyWithoutAWoundForOne() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(MIDDLE - MEDKIT_REACH * 0.5f, MIDDLE);

        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        simulation.step();

        assertThat(trooper.getComponent(Trooper.class).mode).isNotEqualTo(Trooper.Mode.MEND);
    }

    /** Somebody else got there first: he drops the walk rather than finishing it and standing over nothing. */
    @Test
    void dropsTheWalkOnceTheMedkitIsGone() {
        Simulation simulation = simulation();
        Entity trooper = simulation.spawnTrooper(MIDDLE - MEDKIT_REACH * 0.8f, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE + 6f);
        wound(simulation, trooper);
        wound(simulation, commander);
        simulation.spawnPickup(PickupKind.MEDKIT, MIDDLE, MIDDLE);
        simulation.step();

        commander.getComponent(Position.class).set(MIDDLE, MIDDLE);
        steps(simulation, A_MOMENT);

        assertThat(commander.getComponent(Vitality.class).wounds).isZero();
        assertThat(trooper.getComponent(Trooper.class).mode).isNotEqualTo(Trooper.Mode.MEND);
    }

    private static void wound(Simulation simulation, Entity man) {
        man.getComponent(Damage.class).land(0, Harm.WOUND);
        steps(simulation, ticksIn(WOUND_GRACE) + 2);
    }
}
