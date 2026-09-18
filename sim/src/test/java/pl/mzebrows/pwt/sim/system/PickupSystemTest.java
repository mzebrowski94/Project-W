package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A pickup is the first mechanic where two entities on different sides want the same object, so most
 * of what matters here is about exclusivity and about who gets what - not about the grant itself,
 * which {@code EvolutionSystemTest} covers.
 */
class PickupSystemTest {

    private static final float X = 50f;
    private static final float Y = 50f;

    /** Close enough that both units overlap a pickup between them, far enough not to start inside it. */
    private static final float NEIGHBOUR_GAP = 1f;

    @Test
    void feedsAnEnemyThatEatsSomethingLyingOnTheField() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
    }

    @Test
    void takesAPickupOffTheFieldOnceSomethingHasTakenIt() {
        Simulation simulation = simulation();
        simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(count(simulation, Pickup.class)).isZero();
    }

    /**
     * Two units reach the same mushroom on the same tick. Exactly one eats it, and which one is fixed
     * by the collision grid's order rather than by chance - so a replay of the same run agrees.
     */
    @Test
    void givesOnePickupToExactlyOneTaker() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnUnit(UnitKind.GRUNT, X - NEIGHBOUR_GAP, Y);
        Entity second = simulation.spawnUnit(UnitKind.GRUNT, X + NEIGHBOUR_GAP, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(first.getComponent(Feed.class).count + second.getComponent(Feed.class).count)
                .isEqualTo(1);
    }

    @Test
    void givesAUnitOnlyOnePickupPerTick() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X - NEIGHBOUR_GAP, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X + NEIGHBOUR_GAP, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * The commander walks over alien food and leaves it there. A side a pickup offers nothing does not
     * consume it, which is what stops one unit denying another an item it had no use for itself.
     */
    @Test
    void leavesAPickupStandingForASideItOffersNothing() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    /**
     * The contested case, and the reason a pickup carries a grant per side rather than a list of who
     * may take it: the same frozen trooper is a reinforcement to the commander and a meal to whatever
     * gets there first.
     */
    @Test
    void givesTheCommanderAndAnEnemyDifferentThingsForTheSamePickup() {
        Simulation rescued = simulation();
        rescued.spawnCommander(X, Y);
        rescued.spawnPickup(PickupKind.FROZEN_TROOPER, X, Y);

        Simulation eaten = simulation();
        Entity grunt = eaten.spawnUnit(UnitKind.GRUNT, X, Y);
        eaten.spawnPickup(PickupKind.FROZEN_TROOPER, X, Y);

        rescued.step();
        eaten.step();

        assertThat(count(rescued, Trooper.class)).isEqualTo(1);
        assertThat(count(eaten, Trooper.class)).isZero();
        assertThat(grunt.getComponent(Feed.class).count).isEqualTo(1);
    }

    /**
     * A grant that belongs to nobody in particular lands on the level entity, which is how the
     * simulation reaches level-scope state without the message queue G0004 rejected. Nothing reaches
     * the taker at all - {@code ReinforcementSystem} turns the level's count into a man the same
     * tick, which is what {@code ReinforcementSystemTest} is about.
     */
    @Test
    void landsALevelScopeGrantOnTheLevelRatherThanOnTheTaker() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.FROZEN_TROOPER, X, Y);

        simulation.step();

        assertThat(commander.getComponent(Feed.class)).isNull();
        assertThat(count(simulation, Trooper.class)).isEqualTo(1);
    }

    /** A body in its throes is scenery, and scenery does not go shopping. */
    @Test
    void letsNothingInItsDeathThroesTakeAnything() {
        Simulation simulation = simulation();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Dying.class).begin();
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isZero();
        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }
}
