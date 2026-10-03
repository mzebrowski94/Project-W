package pl.mzebrows.pwt.sim.system.query;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ACTION_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * Whatever a man reaches for - a man on the ground, an empty gun, a shut crate - is measured one way,
 * surface to surface, and the nearest of what is on offer wins.
 */
class ReachTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 2f;

    /** Further than {@link #BESIDE}, and still inside arm's length. */
    private static final float NEARBY = 3f;

    private static final int CARRIES = 2;

    @Test
    void measuresTheGapSurfaceToSurface() {
        Simulation simulation = simulation();
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity other = simulation.spawnTrooper(MIDDLE + 10f, MIDDLE);

        float radii = man.getComponent(Body.class).radius + other.getComponent(Body.class).radius;

        assertThat(Reach.gap(man, other)).isCloseTo(10f - radii, Offset.offset(1e-4f));
    }

    @Test
    void picksTheNearestOfWhatIsOnOffer() {
        Simulation simulation = simulation();
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity further = simulation.spawnTurret(MIDDLE - NEARBY, MIDDLE);
        Entity nearer = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        assertThat(Reach.nearest(all(simulation, Turret.class), man, ACTION_REACH, ActionKind.MAN_TURRET))
                .isSameAs(nearer)
                .isNotSameAs(further);
    }

    @Test
    void offersNothingOutOfReach() {
        Simulation simulation = simulation();
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTurret(MIDDLE + ACTION_REACH * 4f, MIDDLE);

        assertThat(Reach.nearest(all(simulation, Turret.class), man, ACTION_REACH, ActionKind.MAN_TURRET))
                .isNull();
    }

    @Test
    void passesOverAGunSomebodyIsOn() {
        Simulation simulation = simulation();
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crewed = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);
        Entity free = simulation.spawnTurret(MIDDLE - NEARBY, MIDDLE);
        crewed.getComponent(Turret.class).crewed = true;

        assertThat(Reach.nearest(all(simulation, Turret.class), man, ACTION_REACH, ActionKind.MAN_TURRET))
                .isSameAs(free);
    }

    @Test
    void passesOverACrateAlreadyOpen() {
        Simulation simulation = simulation();
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity open = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        Entity shut = simulation.spawnPickup(PickupKind.CRATE, MIDDLE - NEARBY, MIDDLE);
        open.getComponent(Crate.class).openedBy = 1;

        assertThat(Reach.nearest(all(simulation, Crate.class), man, ACTION_REACH, ActionKind.OPEN_CRATE))
                .isSameAs(shut);
    }

    @Test
    void offersOnlyACasualtyAndNeverTheManHimself() {
        Simulation simulation = simulation();
        Entity man = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity standing = simulation.spawnTrooper(MIDDLE + BESIDE, MIDDLE);
        Entity dead = simulation.spawnTrooper(MIDDLE, MIDDLE + BESIDE);
        Entity casualty = simulation.spawnTrooper(MIDDLE - NEARBY, MIDDLE);
        man.getComponent(Vitality.class).take(Harm.DOWN, CARRIES);
        dead.getComponent(Vitality.class).take(Harm.DOWN, CARRIES);
        dead.getComponent(Dying.class).begin();
        casualty.getComponent(Vitality.class).take(Harm.DOWN, CARRIES);

        assertThat(Reach.nearest(all(simulation, Vitality.class), man, ACTION_REACH, ActionKind.STABILISE))
                .isSameAs(casualty)
                .isNotSameAs(standing);
    }

    private static ImmutableArray<Entity> all(
            Simulation simulation, Class<? extends com.badlogic.ashley.core.Component> type) {
        return simulation.engine().getEntitiesFor(Family.all(type).get());
    }
}
