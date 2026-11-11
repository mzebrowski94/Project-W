package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POP_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Pop;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The pop: a take by touch made visible for a moment (G0078) - the taken thing's look, rising off whoever on the
 * player's side took it, and gone again. Feedback, so a spawned entity rather than a message (G0004).
 */
class PopTest {

    private static final float X = 50f;
    private static final float Y = 50f;

    @Test
    void leavesAPopWearingThePickupsLookWhereAManTookIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        Entity pop = onlyPop(simulation);
        assertThat(pop.getComponent(Appearance.class).look)
                .isEqualTo(simulation.config().pickup(PickupKind.SUPPLY_DROP).look());
        assertThat(pop.getComponent(Position.class).x).isEqualTo(X);
        assertThat(pop.getComponent(Position.class).y).isEqualTo(Y);
    }

    /** A trooper's take is his side's take: the squad's pickups pop too. */
    @Test
    void leavesAPopWhereATrooperTookSomething() {
        Simulation simulation = simulation();
        simulation.spawnTrooper(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        assertThat(onlyPop(simulation).getComponent(Appearance.class).look)
                .isEqualTo(simulation.config().pickup(PickupKind.SUPPLY_DROP).look());
    }

    @Test
    void isGoneOnceItsMomentIsOver() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);
        simulation.step();

        steps(simulation, ticksIn(POP_SECONDS) + 1);

        assertThat(count(simulation, Pop.class)).isZero();
    }

    /** Publishes how much of it is left, for the renderer to raise and fade it by (G0003). */
    @Test
    void publishesHowMuchOfItIsLeft() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);
        simulation.step();
        float fresh = onlyPop(simulation).getComponent(Appearance.class).pop;

        steps(simulation, ticksIn(POP_SECONDS * 0.5f));

        assertThat(fresh).isGreaterThan(0.9f);
        assertThat(onlyPop(simulation).getComponent(Appearance.class).pop).isBetween(0.3f, 0.7f);
    }

    /** The aliens' meals have their tell already - the strain they grow into. */
    @Test
    void leavesNoneWhereAnAlienAteSomething() {
        Simulation simulation = simulation();
        simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        simulation.spawnPickup(PickupKind.MUSHROOM, X, Y);

        simulation.step();

        assertThat(count(simulation, Pop.class)).isZero();
    }

    private static Entity onlyPop(Simulation simulation) {
        var pops = simulation.engine().getEntitiesFor(Family.all(Pop.class).get());
        assertThat(pops.size()).isEqualTo(1);
        return pops.first();
    }
}
