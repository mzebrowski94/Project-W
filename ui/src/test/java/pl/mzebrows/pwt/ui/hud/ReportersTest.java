package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.WeaponKind;

class ReportersTest {

    private final Recorder out = new Recorder();

    private static Reporters all() {
        return new Reporters(List.of(new PlayerReporter(), new SquadReporter(5)));
    }

    private static Readout world(long serial) {
        Readout readout = new Readout();
        readout.worldSerial = serial;
        return readout;
    }

    private static Readout squad(int standing) {
        Readout readout = world(1);
        readout.troopersStanding = standing;
        return readout;
    }

    @Test
    void reportsNothingOnTheFrameAWorldArrives() {
        Reporters reporters = all();
        Readout arrived = world(1);
        arrived.troopersStanding = 2;
        arrived.player(0).posture = Posture.CASUALTY;
        arrived.player(1).posture = Posture.STANDING;

        reporters.report(arrived, out);

        assertThat(out.resets).isEqualTo(1);
        assertThat(out.lines).containsExactly("MISSION_STARTED -1 0/0");
        out.lines.clear();
        reporters.report(arrived, out);

        assertThat(out.lines).isEmpty();
    }

    @Test
    void startsOverOnANewWorld() {
        Reporters reporters = all();
        reporters.report(world(1), out);
        Readout next = world(2);
        next.troopersStanding = 9;

        reporters.report(next, out);

        assertThat(out.resets).isEqualTo(2);
        assertThat(out.lines).containsExactly("MISSION_STARTED -1 0/0", "MISSION_STARTED -1 0/0");
    }

    @Test
    void prefixesThePlayerOnlyWhenThereIsMoreThanOne() {
        PlayerReporter reporter = new PlayerReporter();
        Readout was = world(1);
        was.player(1).posture = Posture.STANDING;
        Readout now = world(1);
        now.player(1).posture = Posture.CASUALTY;
        reporter.bind(was);

        reporter.report(was, now, out);
        was.player(3).posture = Posture.STANDING;
        now.player(3).posture = Posture.STANDING;
        reporter.bind(was);
        reporter.report(was, now, out);

        assertThat(out.lines).containsExactly("COMMANDER_DOWN -1 0/0", "COMMANDER_DOWN 1 0/0");
    }

    @Test
    void saysANewCommanderJoinedOnceAndNothingWhenHeComesBack() {
        PlayerReporter reporter = new PlayerReporter();
        Readout alone = world(1);
        alone.player(0).posture = Posture.STANDING;
        Readout joined = world(1);
        joined.player(0).posture = Posture.STANDING;
        joined.player(2).posture = Posture.STANDING;
        Readout respawning = world(1);
        respawning.player(0).posture = Posture.STANDING;
        respawning.player(2).posture = Posture.RESPAWNING;
        reporter.bind(alone);

        reporter.report(alone, joined, out);
        reporter.report(joined, respawning, out);
        reporter.report(respawning, joined, out);

        assertThat(out.lines).containsExactly("COMMANDER_JOINED 2 0/0", "COMMANDER_LOST 2 0/0");
    }

    @Test
    void saysADownedCommanderLostOnceAsHeDies() {
        PlayerReporter reporter = new PlayerReporter();
        Readout standing = world(1);
        standing.player(0).posture = Posture.STANDING;
        Readout down = world(1);
        down.player(0).posture = Posture.CASUALTY;
        Readout dying = world(1);
        dying.player(0).posture = Posture.DYING;
        Readout gone = world(1);
        gone.player(0).posture = Posture.RESPAWNING;
        reporter.bind(standing);

        reporter.report(standing, down, out);
        reporter.report(down, dying, out);
        reporter.report(dying, gone, out);

        assertThat(out.lines).containsExactly("COMMANDER_DOWN -1 0/0", "COMMANDER_LOST -1 0/0");
    }

    @Test
    void saysNothingWhenADownedCommanderIsStabilised() {
        PlayerReporter reporter = new PlayerReporter();
        Readout down = world(1);
        down.player(0).posture = Posture.CASUALTY;
        Readout up = world(1);
        up.player(0).posture = Posture.STANDING;
        reporter.bind(down);

        reporter.report(down, up, out);

        assertThat(out.lines).isEmpty();
    }

    @Test
    @SuppressWarnings("EnumOrdinal") // a dispatch carries an enum as its number
    void saysANewGunButNotTheTurretsWhenHeClimbsAboard() {
        PlayerReporter reporter = new PlayerReporter();
        Readout was = world(1);
        was.player(0).posture = Posture.STANDING;
        Readout armed = world(1);
        armed.player(0).posture = Posture.STANDING;
        armed.player(0).weapon = WeaponKind.FLAMER;
        Readout aboard = world(1);
        aboard.player(0).posture = Posture.ABOARD;
        aboard.player(0).weapon = WeaponKind.TURRET_GUN;
        reporter.bind(was);

        reporter.report(was, armed, out);
        reporter.report(armed, aboard, out);

        assertThat(out.lines).containsExactly("WEAPON_ACQUIRED -1 " + WeaponKind.FLAMER.ordinal() + "/0");
    }

    @Test
    void reportsReinforcementsAndAFewLeftButNotEachLoss() {
        SquadReporter reporter = new SquadReporter(5);
        int[] standing = {4, 6, 5, 4, 3, 7, 2};

        for (int i = 1; i < standing.length; i++) {
            reporter.report(squad(standing[i - 1]), squad(standing[i]), out);
        }

        assertThat(out.lines)
                .containsExactly(
                        "REINFORCED -1 2/0", "FEW_TROOPERS -1 4/0", "REINFORCED -1 4/0", "FEW_TROOPERS -1 2/0");
    }

    private static final class Recorder implements DispatchSink {

        final List<String> lines = new ArrayList<>();
        int resets;

        @Override
        public void offer(DispatchKind kind, int slot, int a, int b) {
            lines.add(kind + " " + slot + " " + a + "/" + b);
        }

        @Override
        public void reset() {
            resets++;
        }
    }
}
