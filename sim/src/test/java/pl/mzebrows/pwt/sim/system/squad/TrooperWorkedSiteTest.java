package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MAINFRAME_WORK_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * A squad goes for a mainframe as for a point, and one man downloads it while the others watch (G0111): one trooper
 * a site, the rest of a crowd round it in their sectors, a spare past the crowd sent on.
 */
class TrooperWorkedSiteTest {

    private static final float MIDDLE = 50f;

    /** Where the squad starts, west of the mainframe; and the commander, out of everybody's way. */
    private static final float WEST = 30f;

    private static final float CORNER = 5f;

    /** The next point the squad would take, east of the mainframe. */
    private static final float EAST = 85f;

    private static final int CROWD = 5;

    /** Long enough to walk over and stand round it, short of downloading it. */
    private static final int SETTLE = ticksIn(2f);

    @Test
    void downloadsAMainframeWithNoCommanderAtIt() {
        Simulation simulation = squadOf(new Simulation(config()), 3);
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);

        steps(simulation, ticksIn(3f + MAINFRAME_WORK_SECONDS * 2f));

        assertThat(mainframe.getComponent(Site.class).taken).isTrue();
    }

    @Test
    void givesTheWorkToOneTrooperAndTheOthersWatchTheirSectors() {
        Simulation simulation = squadOf(slowly(), 3);
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        List<Entity> squad = troopers(simulation);

        steps(simulation, SETTLE);

        long worker = mainframe.getComponent(WorkedSite.class).trooper;
        assertThat(worker).as("gone to one of them").isNotEqualTo(Handle.NOBODY);
        assertThat(mainframe.getComponent(Site.class).taken)
                .as("still being downloaded")
                .isFalse();
        for (Entity man : squad) {
            Trooper trooper = man.getComponent(Trooper.class);
            assertThat(trooper.mode).isEqualTo(Trooper.Mode.SECURE);
            if (Handles.of(man) != worker) {
                assertThat(trooper.sectorSpan).as("watching a sector").isPositive();
            }
        }
    }

    @Test
    void losesTheWorkWhenTheTrooperAtItIsWounded() {
        Simulation simulation = squadOf(slowly(), 1);
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        steps(simulation, SETTLE);
        WorkedSite site = mainframe.getComponent(WorkedSite.class);
        boolean working = site.workedTicks > 0;

        troopers(simulation).getFirst().getComponent(Damage.class).land(0, Harm.WOUND);
        simulation.step();

        assertThat(working).as("at it before the wound").isTrue();
        assertThat(site.workedTicks).isZero();
    }

    @Test
    void sendsTheManPastAFullCrowdOnToTheNextPoint() {
        Simulation simulation = squadOf(crowded(), CROWD + 1);
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        Entity next = simulation.spawnControlPoint(EAST, MIDDLE);

        steps(simulation, SETTLE);

        assertThat(mainframe.getComponent(Site.class).taken).isFalse();
        assertThat(troopers(simulation))
                .filteredOn(man -> man.getComponent(Trooper.class).sentAhead == Handles.of(next))
                .hasSize(1);
    }

    /** The commander in a corner and this many troopers west of the middle, in a column. */
    private static Simulation squadOf(Simulation simulation, int men) {
        simulation.spawnCommander(CORNER, CORNER);
        for (int i = 0; i < men; i++) {
            simulation.spawnTrooper(WEST, MIDDLE - (men - 1) * 2f + i * 4f);
        }
        return simulation;
    }

    /** A download that takes far longer than the test looks, so the squad is caught at it. */
    private static Simulation slowly() {
        return new Simulation(config().withWorkedSites(config().workedSites()
                .map(WorkedSiteKind.MAINFRAME, mainframe -> mainframe.withWorkSeconds(60f))));
    }

    /** The same, with a crowd of five. */
    private static Simulation crowded() {
        return new Simulation(config().withTrooper(config().trooper().withCaptureCrowd(CROWD))
                .withWorkedSites(config().workedSites()
                        .map(WorkedSiteKind.MAINFRAME, mainframe -> mainframe.withWorkSeconds(60f))));
    }

    private static List<Entity> troopers(Simulation simulation) {
        List<Entity> men = new ArrayList<>();
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Trooper.class).get())) {
            men.add(entity);
        }
        return men;
    }
}
