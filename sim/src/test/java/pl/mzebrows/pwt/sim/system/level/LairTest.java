package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A lair (G0048): what it holds is not on the field at all until every commander on his feet is
 * standing on its ground, and then all of it is, once.
 */
class LairTest {

    /** The right-hand half of the fixture's twenty-tile arena. */
    private static final TileRect GROUND = new TileRect(10, 0, 10, 20);

    private static final float OUTSIDE = 20f;

    private static final float INSIDE = 70f;

    private static final float MIDDLE = 50f;

    private static final float BOSS_X = 90f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    @Test
    void keepsEverythingOffTheFieldUntilSomebodyIsOnItsGround() {
        Simulation simulation = withALair();
        simulation.spawnCommander(OUTSIDE, MIDDLE);

        steps(simulation, 10);

        assertThat(bosses(simulation)).isZero();
        assertThat(count(simulation, Nest.class)).isZero();
    }

    @Test
    void putsDownEverythingItHoldsWhenEveryCommanderIsOnItsGround() {
        Simulation simulation = withALair();
        simulation.spawnCommander(INSIDE, MIDDLE);

        simulation.step();

        assertThat(bosses(simulation)).isEqualTo(1);
        assertThat(count(simulation, Nest.class)).isEqualTo(2);
        assertThat(simulation.lair().woken()).isTrue();
    }

    /** Every one of them: a boss that rises behind a player still in the last biome is met short-handed. */
    @Test
    void laysDownThePickupsItHoldsOnlyWhenItWakes() {
        Simulation simulation = withALair();
        Entity commander = simulation.spawnCommander(OUTSIDE, MIDDLE);
        steps(simulation, 2);

        assertThat(count(simulation, Pickup.class)).isZero();

        commander.getComponent(Position.class).set(INSIDE, MIDDLE);
        simulation.step();

        assertThat(count(simulation, Pickup.class)).isEqualTo(1);
    }

    @Test
    void waitsForEveryPlayerNotJustTheFirst() {
        Simulation simulation = withALair();
        simulation.spawnCommander(INSIDE, MIDDLE, 0);
        simulation.spawnCommander(OUTSIDE, MIDDLE, 1);

        steps(simulation, 10);

        assertThat(bosses(simulation)).isZero();
    }

    /** A dropped link leaves his commander standing idle wherever he was; that must not hold it up for ever (B.1). */
    @Test
    void doesNotWaitForAPlayerWhoseLinkHasDropped() {
        Simulation simulation = withALair();
        simulation.spawnCommander(INSIDE, MIDDLE, 0);
        simulation.spawnCommander(OUTSIDE, MIDDLE, 1);

        simulation.step(new PlayerInputs().away(1));

        assertThat(bosses(simulation)).isEqualTo(1);
    }

    /** While it waits it says how many are on its ground of how many it wants, for the goal line (B.1). */
    @Test
    void saysHowManyPlayersAreOnItsGroundOfHowManyItIsWaitingFor() {
        Simulation simulation = withALair();
        simulation.spawnCommander(INSIDE, MIDDLE, 0);
        simulation.spawnCommander(OUTSIDE, MIDDLE, 1);
        simulation.spawnCommander(INSIDE, MIDDLE + 5f, 2);

        simulation.step();

        assertThat(simulation.lair().present()).isEqualTo(2);
        assertThat(simulation.lair().needed()).isEqualTo(3);
    }

    /** Everything spawns facing east; what rises faces the man who woke it, so a boss never greets them with his back. */
    @Test
    void raisesItsCreaturesFacingTheNearestCommanderOnItsGround() {
        Simulation simulation = withALair();
        simulation.spawnCommander(INSIDE, MIDDLE - 20f, 0);
        simulation.spawnCommander(INSIDE - 15f, MIDDLE + 20f, 1);

        simulation.step();

        Facing facing = boss(simulation).getComponent(Facing.class);
        float diagonal = (float) Math.sqrt(0.5);
        assertThat(facing.x).isCloseTo(-diagonal, CLOSE);
        assertThat(facing.y).isCloseTo(-diagonal, CLOSE);
    }

    @Test
    void wakesOnce() {
        Simulation simulation = withALair();
        simulation.spawnCommander(INSIDE, MIDDLE);

        steps(simulation, 10);

        assertThat(bosses(simulation)).isEqualTo(1);
        assertThat(count(simulation, Nest.class)).isEqualTo(2);
    }

    @Test
    void holdsNothingOnALevelThatNamesNone() {
        Simulation simulation = new Simulation(config());
        simulation.spawnCommander(INSIDE, MIDDLE);

        steps(simulation, 10);

        assertThat(simulation.lair().waiting()).isFalse();
        assertThat(bosses(simulation)).isZero();
    }

    private static Entity boss(Simulation simulation) {
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get())) {
            if (entity.getComponent(Strain.class).kind == UnitKind.BOSS_GRUNT) {
                return entity;
            }
        }
        throw new AssertionError("no boss on the field");
    }

    private static int bosses(Simulation simulation) {
        int bosses = 0;
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get())) {
            if (entity.getComponent(Strain.class).kind == UnitKind.BOSS_GRUNT) {
                bosses++;
            }
        }
        return bosses;
    }

    private static Simulation withALair() {
        Simulation simulation = new Simulation(config());
        simulation
                .lair()
                .at(GROUND)
                .nest(UnitKind.GRUNT, BOSS_X, MIDDLE - 30f)
                .nest(UnitKind.ANT, BOSS_X, MIDDLE + 30f)
                .pickup(PickupKind.ARMOURY_SHOTGUN, BOSS_X - 10f, MIDDLE)
                .unit(UnitKind.BOSS_GRUNT, BOSS_X, MIDDLE);
        return simulation;
    }
}
