package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MAINFRAME_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MAINFRAME_ROUSE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MAINFRAME_WORK_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * A mainframe is a site you download (G0111): the action key held at arm's length of it for its work seconds, the work
 * lost the moment he lets go or is wounded, each fresh attempt loud, and taking it the one step every site goes
 * through - once.
 */
class WorkedSiteTest {

    private static final float MIDDLE = 50f;

    /** At arm's length of the mainframe, east of it: a unit between his edge and its. */
    private static final float AT_IT = MIDDLE + MAINFRAME_RADIUS + COMMANDER_RADIUS + 1f;

    /** Far enough away that a unit put here is neither at it nor within earshot. */
    private static final float ELSEWHERE = 95f;

    private static final CommanderInput DOWNLOADING = CommanderInput.IDLE.acting(true);

    @Test
    void offersADownloadAtArmsLengthNamingTheMainframe() {
        Simulation simulation = harmless();
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(AT_IT, MIDDLE);

        simulation.step();

        Action action = commander.getComponent(Action.class);
        assertThat(action.available).isEqualTo(ActionKind.DOWNLOAD);
        assertThat(action.subject).isEqualTo(Handles.of(mainframe));
    }

    @Test
    void isTakenOnTheActionKeyHeldAtItForItsWorkSeconds() {
        Simulation simulation = harmless();
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        simulation.spawnCommander(AT_IT, MIDDLE);
        int work = ticksIn(MAINFRAME_WORK_SECONDS);

        steps(simulation, work - 1, DOWNLOADING);
        boolean takenEarly = mainframe.getComponent(Site.class).taken;
        simulation.step(DOWNLOADING);

        assertThat(takenEarly).as("a tick short").isFalse();
        assertThat(mainframe.getComponent(Site.class).taken).isTrue();
    }

    @Test
    void losesTheWorkWhenHeLetsGo() {
        Simulation simulation = harmless();
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        simulation.spawnCommander(AT_IT, MIDDLE);
        int work = ticksIn(MAINFRAME_WORK_SECONDS);

        steps(simulation, work - 1, DOWNLOADING);
        simulation.step();
        steps(simulation, 1, DOWNLOADING);

        assertThat(mainframe.getComponent(Site.class).taken).isFalse();
        assertThat(mainframe.getComponent(WorkedSite.class).progress()).isLessThan(0.1f);
    }

    @Test
    void losesTheWorkWhenAWoundLandsOnHim() {
        Simulation simulation = harmless();
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(AT_IT, MIDDLE);
        int work = ticksIn(MAINFRAME_WORK_SECONDS);

        steps(simulation, work - 1, DOWNLOADING);
        commander.getComponent(Damage.class).land(0, Harm.WOUND);
        simulation.step(DOWNLOADING);

        assertThat(mainframe.getComponent(Site.class).taken).isFalse();
        assertThat(mainframe.getComponent(WorkedSite.class).workedTicks).isZero();
    }

    @Test
    void publishesItsWorkAsAChargeOnItForEveryoneToSee() {
        Simulation simulation = harmless();
        Entity mainframe = simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(AT_IT, MIDDLE);

        steps(simulation, ticksIn(MAINFRAME_WORK_SECONDS) / 2, DOWNLOADING);

        assertThat(mainframe.getComponent(Appearance.class).charge).isGreaterThan(0.3f);
        assertThat(commander.getComponent(Appearance.class).charge)
                .as("nothing fills on him")
                .isZero();
    }

    @Test
    void rousesWhatIsNearOnTheTickAnAttemptStarts() {
        Simulation simulation = watching();
        simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        simulation.spawnCommander(AT_IT, MIDDLE);
        Entity near = simulation.spawnEnemy(MIDDLE - MAINFRAME_ROUSE_RADIUS + 2f, MIDDLE);
        Entity far = simulation.spawnEnemy(MIDDLE, ELSEWHERE);

        simulation.step(DOWNLOADING);

        Perception heard = near.getComponent(Perception.class);
        assertThat(heard.provoked || heard.aggro).isTrue();
        assertThat(far.getComponent(Perception.class).provoked).isFalse();
    }

    @Test
    void rousesOnceAnAttemptAndAgainOnTheNext() {
        Simulation simulation = watching();
        simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        simulation.spawnCommander(AT_IT, MIDDLE);
        Entity near = simulation.spawnEnemy(MIDDLE - MAINFRAME_ROUSE_RADIUS + 2f, MIDDLE);
        Perception heard = near.getComponent(Perception.class);

        simulation.step(DOWNLOADING);
        heard.provoked = false;
        simulation.step(DOWNLOADING);
        boolean againMidAttempt = heard.provoked;
        simulation.step();
        simulation.step(DOWNLOADING);

        assertThat(againMidAttempt).as("once an attempt").isFalse();
        assertThat(heard.provoked).as("a fresh attempt").isTrue();
    }

    @Test
    void laysWhatATakenSiteLeavesAndCreditsTheManWhoTookIt() {
        Simulation simulation = harmless();
        simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(AT_IT, MIDDLE);

        steps(simulation, ticksIn(MAINFRAME_WORK_SECONDS) + 1, DOWNLOADING);

        assertThat(lying(simulation, PickupKind.CRATE)).isPositive();
        assertThat(lying(simulation, PickupKind.ARMOURY_GRENADES)).isPositive();
        assertThat(commander.getComponent(Tally.class).goals).isOne();
    }

    @Test
    void isNeverTakenTwice() {
        Simulation simulation = harmless();
        simulation.spawnWorkedSite(WorkedSiteKind.MAINFRAME, MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(AT_IT, MIDDLE);
        int work = ticksIn(MAINFRAME_WORK_SECONDS);
        steps(simulation, work + 1, DOWNLOADING);
        int crates = lying(simulation, PickupKind.CRATE);

        steps(simulation, 2 * work, DOWNLOADING);

        assertThat(lying(simulation, PickupKind.CRATE)).isEqualTo(crates);
        assertThat(commander.getComponent(Tally.class).goals).isOne();
        assertThat(commander.getComponent(Action.class).available).isNotEqualTo(ActionKind.DOWNLOAD);
    }

    private static int lying(Simulation simulation, PickupKind kind) {
        int count = 0;
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Pickup.class).get())) {
            if (entity.getComponent(Pickup.class).kind == kind) {
                count++;
            }
        }
        return count;
    }
}
