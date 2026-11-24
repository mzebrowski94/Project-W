package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ASSAULT_STAND_OFF;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ordering;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Once a lair has risen, the squad assaults it (G0122): a trooper with nothing better to do walks to his gun's reach of
 * the nearest nest it raised, then of its boss, and fights from there - an order and anything inside his all-round
 * range first, as they come before the post's ring.
 */
class TrooperAssaultTest {

    /** The lair's ground, the east half of the arena. */
    private static final TileRect GROUND = new TileRect(10, 0, 10, 20);

    private static final float LAIR_X = 90f;
    private static final float SOUTH_NEST_Y = 20f;
    private static final float NORTH_NEST_Y = 80f;
    private static final float BOSS_Y = 50f;

    /** Far west, nearer the south nest than the north one and well beyond what he would pick either out from. */
    private static final float MAN_X = 5f;

    private static final float MAN_Y = 15f;

    @Test
    void staysOffTheLairUntilItRises() {
        Simulation simulation = withALair();
        simulation.spawnCommander(20f, 50f);
        Entity man = simulation.spawnTrooper(MAN_X, MAN_Y);

        steps(simulation, 30);

        assertThat(simulation.lair().woken()).isFalse();
        assertThat(trooper(man).mode).isNotEqualTo(Trooper.Mode.ASSAULT);
    }

    @Test
    void walksForTheNearestNestOnceTheLairRises() {
        Simulation simulation = risen();
        Entity man = simulation.spawnTrooper(MAN_X, MAN_Y);

        steps(simulation, 2);

        assertThat(trooper(man).mode).isEqualTo(Trooper.Mode.ASSAULT);
        assertThat(trooper(man).hasTarget).isTrue();
        assertThat(trooper(man).targetX).isEqualTo(LAIR_X);
        assertThat(trooper(man).targetY).isEqualTo(SOUTH_NEST_Y);
    }

    @Test
    void goesForTheBossOnceItsNestsAreGone() {
        Simulation simulation = risen();
        Entity man = simulation.spawnTrooper(MAN_X, MAN_Y);
        steps(simulation, 1);
        List<Entity> nests = new ArrayList<>();
        for (Entity nest :
                simulation.engine().getEntitiesFor(Family.all(Nest.class).get())) {
            nests.add(nest);
        }
        nests.forEach(simulation.engine()::removeEntity);

        steps(simulation, 2);

        assertThat(trooper(man).mode).isEqualTo(Trooper.Mode.ASSAULT);
        assertThat(trooper(man).targetX).isEqualTo(LAIR_X);
        assertThat(trooper(man).targetY).isEqualTo(BOSS_Y);
    }

    /** From reach, not on top: inside his stand-off he stops, and walks no closer. */
    @Test
    void stopsAtHisReachRatherThanWalkingOntoIt() {
        Simulation simulation = risen();
        Entity man = simulation.spawnTrooper(LAIR_X - ASSAULT_STAND_OFF * 0.75f, SOUTH_NEST_Y);
        // Facing away, so he has not picked the nest out and the assault is what moves him.
        trooper(man).lookX = -1f;
        trooper(man).lookY = 0f;

        steps(simulation, 2);

        assertThat(trooper(man).mode).isEqualTo(Trooper.Mode.ASSAULT);
        assertThat(trooper(man).hasTarget).isFalse();
    }

    @Test
    void holdsAnOrderOverTheAssault() {
        Simulation simulation = risen();
        // In earshot of his commander, facing away from the lair.
        Entity man = simulation.spawnTrooper(64f, BOSS_Y);
        trooper(man).lookX = -1f;
        trooper(man).lookY = 0f;

        simulation.step(ordering(Order.DEFENCE));
        steps(simulation, 2);

        assertThat(trooper(man).mode).isEqualTo(Trooper.Mode.HOLD);
    }

    @Test
    void fightsWhatIsInsideHisAllRoundRangeFirst() {
        Simulation simulation = risen();
        Entity man = simulation.spawnTrooper(MAN_X, MAN_Y);
        simulation.spawnUnit(UnitKind.GRUNT, MAN_X + 3f, MAN_Y);

        steps(simulation, 2);

        assertThat(trooper(man).mode).isEqualTo(Trooper.Mode.FIGHT);
    }

    /** A level with no lair - the redoubt - never sends anybody after one. */
    @Test
    void neverAssaultsOnALevelWithNoLair() {
        Simulation simulation = new Simulation(config());
        simulation.spawnCommander(70f, 50f);
        Entity man = simulation.spawnTrooper(MAN_X, MAN_Y);

        steps(simulation, 30);

        assertThat(trooper(man).mode).isNotEqualTo(Trooper.Mode.ASSAULT);
    }

    private static Trooper trooper(Entity man) {
        return man.getComponent(Trooper.class);
    }

    /** A lair that has risen: its commander inside its ground, one tick on. */
    private static Simulation risen() {
        Simulation simulation = withALair();
        simulation.spawnCommander(70f, BOSS_Y);
        simulation.step();
        assertThat(simulation.lair().woken()).isTrue();
        return simulation;
    }

    private static Simulation withALair() {
        Simulation simulation = new Simulation(config());
        simulation
                .lair()
                .at(GROUND)
                .nest(UnitKind.GRUNT, LAIR_X, SOUTH_NEST_Y)
                .nest(UnitKind.ANT, LAIR_X, NORTH_NEST_Y)
                .unit(UnitKind.BOSS_GRUNT, LAIR_X, BOSS_Y);
        return simulation;
    }
}
