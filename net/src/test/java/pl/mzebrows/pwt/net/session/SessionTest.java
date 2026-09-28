package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.SessionHarness.Machine;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * Whole sessions, host and peers in one test: every machine ends on the same world, whatever the
 * network does, whoever drops in, and a machine knocked out of step is found and put back (ADR 0012).
 */
class SessionTest {

    private static final Conditions PERFECT = Conditions.NONE;

    /** Across a city: 40 ms each way give or take 10, one message in ten lost, one in twenty doubled. */
    private static final Conditions ACROSS_A_CITY = new Conditions(40, 10, 0.1f, 0.05f, 7L);

    /** 40 ms each way, every time: whatever the lead does on it, the network did not ask for. */
    private static final Conditions STEADY_40_MS = new Conditions(40, 0, 0f, 0f, 13L);

    /** A link whose messages take anything up to a quarter of a second, so a later one often arrives first. */
    private static final Conditions OVERTAKING = new Conditions(0, 250, 0f, 0f, 11L);

    @Test
    void playsTheSameWorldOnEveryMachine() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            Machine other = session.join("other", PERFECT);

            session.run(600);
            session.agreedHash();

            assertThat(host.session.slotOf(0)).isZero();
            assertThat(friend.session.slotOf(0)).isEqualTo(1);
            assertThat(other.session.slotOf(0)).isEqualTo(2);
            assertThat(session.sequencer.desyncs()).isZero();
            assertThat(host.session.lead()).isEqualTo(session.config.session().leadMinTicks());
        }
    }

    @Test
    void staysInStepOverALaggyLossyNetwork() {
        try (SessionHarness session = new SessionHarness("Skirmish", 5L, new Conditions(0, 0, 0.05f, 0.02f, 3L))) {
            Machine host = session.host();
            Machine far = session.join("far", ACROSS_A_CITY);

            session.run(1200);
            session.agreedHash();

            assertThat(session.sequencer.desyncs()).isZero();
            assertThat(far.session.lead())
                    .as("a peer 40 ms away plays further ahead than the host")
                    .isGreaterThan(host.session.lead())
                    .isLessThanOrEqualTo(session.config.session().leadMaxTicks());
        }
    }

    /**
     * A machine that stalls on its own - its window dragged, its garbage collected - comes back behind,
     * and its input is late only because it is. That is not the network's round trip, and must not leave
     * the machine playing with a long input delay after it has caught up. Found in the first playtest:
     * a 0.4 s hitch left a lead of 27 ticks on a perfect link for 25 seconds.
     */
    @Test
    void comesBackToItsInputDelaySoonAfterAStallOfItsOwn() {
        try (SessionHarness session = new SessionHarness("Skirmish", 29L, PERFECT)) {
            session.host();
            Machine hitching = session.join("hitching", PERFECT);
            session.run(300);
            int before = hitching.session.lead();

            hitching.frozenFrames = 25;
            session.run(25 + 120);

            assertThat(hitching.session.lead())
                    .as("lead two seconds after a hitch")
                    .isLessThanOrEqualTo(before + 1);
        }
    }

    /**
     * A drop-in loads a dozen ticks behind the host and catches up, and what it tags while it does is late
     * for the same reason as after a stall. The third playtest: the lead jumped to 17 and 18 on joining
     * over a steady 60 ms link, and took three seconds to settle at 6.
     */
    @Test
    void dropsInWithTheInputDelayItsLinkNeedsAndNoMore() {
        try (SessionHarness session = new SessionHarness("Skirmish", 53L, PERFECT)) {
            session.host();
            session.run(300);
            Machine late = session.join("late", STEADY_40_MS);

            int most = mostLeadOver(session, late, 240);

            assertThat(most).as("the most lead in its first four seconds").isLessThanOrEqualTo(late.session.lead() + 1);
        }
    }

    /**
     * Eight seats make a bundle 152 bytes, so a datagram holds seven, and a machine 120 ms away always has
     * more than seven ticks unacknowledged. It still gets every tick about half a round trip after it is
     * sealed.
     */
    @Test
    void keepsAFarMachineInStepWhenEverySeatIsTaken() {
        try (SessionHarness session = new SessionHarness("Skirmish", 31L, PERFECT)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            Machine far = session.join("far", new Conditions(120, 10, 0.05f, 0f, 9L));
            for (Machine machine : List.of(host, second, third, far)) {
                machine.session.seat(1);
            }

            session.run(1800);

            assertThat(session.sequencer.sealedTick() - far.session.tick())
                    .as("ticks the far machine is behind")
                    .isLessThanOrEqualTo(24);
            assertThat(far.session.lead())
                    .as("the far machine's input delay")
                    .isLessThan(session.config.session().leadMaxTicks());
            session.agreedHash();
            assertThat(session.sequencer.desyncs()).isZero();
        }
    }

    /**
     * The welcome goes by the stream and carries the host's game.properties, so over a real network it
     * can take several round trips while the session's ticks go by datagram and get there first. Both
     * joiners in the first Hamachi playtest were dropped on the spot: "slot 0 in a session of 0".
     */
    @Test
    void joinsARunningSessionWhenItsTicksOvertakeTheWelcome() {
        try (SessionHarness session = new SessionHarness("Skirmish", 41L, PERFECT)) {
            session.host();
            session.run(60);
            Machine late = session.join("late", OVERTAKING);

            session.run(300);

            assertThat(late.session.state()).isEqualTo(PeerSession.State.PLAYING);
            session.agreedHash();
            assertThat(session.sequencer.desyncs()).isZero();
        }
    }

    /**
     * A machine that stops for longer than its tick ring holds - a window held by its title bar, a slow
     * load - comes back to a gap only resends can fill, and acknowledges them late. Timed from their first
     * send, those late acknowledgements made the round trip read as seconds, so the gap was resent ever
     * more slowly until the machine fell out of the history and was sent the world again, every ten
     * seconds. The second Hamachi playtest: 9.9 s of round trip over a 60 ms link.
     */
    @Test
    void catchesUpSoonAfterAStallLongerThanItsTickRing() {
        try (SessionHarness session = new SessionHarness("Skirmish", 43L, PERFECT)) {
            session.host();
            Machine stalled = session.join("stalled", PERFECT);
            session.run(300);

            stalled.frozenFrames = 400;
            session.run(400 + 180);

            assertThat(session.sequencer.sealedTick() - stalled.session.tick())
                    .as("ticks behind three seconds after a stall of nearly seven")
                    .isLessThanOrEqualTo(session.config.session().bufferTicks() + 2);
        }
    }

    /**
     * What the host's log says about a drop-in is what the second Hamachi playtest could not tell: how long
     * the world took to reach the joiner and be built there, and how long it then took to catch up.
     */
    @Test
    void logsHowLongAJoinerTookToLoadItsWorldAndToCatchUp(@TempDir Path dir) throws IOException {
        NetConfig logged = NetConfig.defaults().with(new NetConfig.LogConfig(dir.toString(), "INFO", 64, 0));
        NetLog log = NetLog.open(logged.log(), "host");
        try (SessionHarness session = new SessionHarness(logged, "Skirmish", 47L, PERFECT, log)) {
            session.host();
            session.run(120);
            session.join("late", ACROSS_A_CITY);

            session.run(240);
        }
        log.close();

        String written = Files.readString(Objects.requireNonNull(log.file()), StandardCharsets.UTF_8);
        assertThat(written)
                .containsPattern("event=welcome peer=1 .* configBytes=\\d+ wireBytes=\\d+")
                .containsPattern("event=snapshot-sent peer=1 tick=\\d+ bytes=\\d+ wireBytes=\\d+ generation=1")
                .containsPattern("event=loaded peer=1 generation=1 ms=\\d+ behind=\\d+")
                .containsPattern("event=caught-up peer=1 generation=1 ms=\\d+");
    }

    @Test
    void leavesAJoinerWaitingRatherThanFailWhenTheWorldIsTooBigToSend() {
        NetConfig d = NetConfig.defaults();
        NetConfig small = new NetConfig(
                d.port(),
                d.portSearch(),
                d.connectTimeoutMillis(),
                d.linkTimeoutMillis(),
                d.heartbeatMillis(),
                d.maxDatagramBytes(),
                20_000,
                d.conditions(),
                d.latencySteps(),
                d.log(),
                d.session(),
                d.lan());
        try (SessionHarness session = new SessionHarness(small, "Advance", 3L, PERFECT)) {
            Machine host = session.host();
            session.run(120);
            Machine late = session.join("late", PERFECT);

            session.run(300);

            assertThat(host.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(host.session.tick()).isGreaterThan(300);
            assertThat(late.session.state()).isEqualTo(PeerSession.State.JOINING);
        }
    }

    @Test
    void dropsAPlayerIntoAGameInProgress() {
        try (SessionHarness session = new SessionHarness("Adventure", 11L, PERFECT)) {
            session.host();
            session.run(900);

            Machine late = session.join("late", ACROSS_A_CITY);
            session.run(600);
            long agreed = session.agreedHash();

            assertThat(late.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(late.session.slotOf(0)).isEqualTo(1);
            assertThat(agreed).isEqualTo(pl.mzebrows.pwt.sim.WorldCodec.hash(SessionHarness.world(late)));
            assertThat(session.sequencer.desyncs()).isZero();
        }
    }

    @Test
    void findsAMachineKnockedOutOfStepDumpsItAndPutsItBack(@TempDir Path dumps) throws IOException {
        try (SessionHarness session = new SessionHarness("Skirmish", 13L, PERFECT)) {
            session.join("host", PERFECT, SessionHarness.HOST_KEY, dumps, BuildId.current(), 0);
            Machine knocked = session.join("knocked", PERFECT, 0L, dumps, BuildId.current(), 37);
            session.run(200);

            // A tick nobody sealed: the one thing lockstep cannot survive, done on purpose.
            Simulation world = SessionHarness.world(knocked);
            world.step(new PlayerInputs(world.capacity()));
            session.run(200);
            session.agreedHash();

            assertThat(session.sequencer.desyncs()).isEqualTo(1);
            List<Path> written;
            try (Stream<Path> files = Files.list(dumps)) {
                written = files.filter(file -> file.toString().endsWith(".bin"))
                        .sorted()
                        .toList();
            }
            assertThat(written).hasSize(2);
            Path reference = written.stream()
                    .filter(f -> f.toString().contains("reference"))
                    .findFirst()
                    .orElseThrow();
            Path desynced = written.stream()
                    .filter(f -> f.toString().contains("desynced"))
                    .findFirst()
                    .orElseThrow();
            assertThat(DesyncDump.summaryOf(desynced)).exists();

            String report = DesyncReport.compare(reference, desynced, new TestWorlds());

            assertThat(report)
                    .contains("fields that differ")
                    .contains("the reference's world - the desynced peer computed differently")
                    .contains("no hidden state");
        }
    }

    @Test
    void refusesAMachineRunningAnotherBuild() {
        try (SessionHarness session = new SessionHarness("Skirmish", 17L, PERFECT)) {
            session.host();
            Machine stranger = session.join("stranger", PERFECT, 0L, null, "somebody-elses", 0);

            session.run(30);

            assertThat(stranger.session.state()).isEqualTo(PeerSession.State.REFUSED);
            assertThat(stranger.session.refusal()).contains("somebody-elses").contains(BuildId.current());
        }
    }

    @Test
    void letsAnyonePauseButOnlyTheHostRestartOrSwitchGodMode() {
        try (SessionHarness session = new SessionHarness("Skirmish", 19L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(120);

            friend.session.pause(true);
            session.run(10);
            int pausedAt = session.sequencer.sealedTick();
            session.run(60);
            assertThat(session.sequencer.sealedTick()).isEqualTo(pausedAt);
            assertThat(host.session.paused()).isTrue();
            friend.session.pause(false);
            session.run(10);
            assertThat(session.sequencer.sealedTick()).isGreaterThan(pausedAt);

            Simulation before = SessionHarness.world(host);
            friend.session.restart();
            friend.session.godMode(true);
            session.run(30);
            assertThat(SessionHarness.world(host)).isSameAs(before);
            assertThat(SessionHarness.world(friend).commanderInvulnerable()).isFalse();

            host.session.restart();
            host.session.godMode(true);
            session.run(30);
            session.agreedHash();
            assertThat(SessionHarness.world(host)).isNotSameAs(before);
            assertThat(SessionHarness.world(friend).commanderInvulnerable()).isTrue();
            assertThat(session.sequencer.desyncs()).isZero();
        }
    }

    @Test
    void holdsEightPlayersFromFourMachines() {
        try (SessionHarness session = new SessionHarness("Skirmish", 23L, PERFECT)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            Machine fourth = session.join("fourth", PERFECT);
            for (Machine machine : List.of(host, second, third, fourth)) {
                machine.session.seat(1);
            }
            session.run(60);
            Machine ninth = session.join("ninth", PERFECT);
            session.run(120);
            session.agreedHash();

            int[] seen = new int[Players.MAX];
            for (Machine machine : List.of(host, second, third, fourth)) {
                seen[machine.session.slotOf(0)]++;
                seen[machine.session.slotOf(1)]++;
            }
            assertThat(seen).containsOnly(1);
            assertThat(ninth.session.slotOf(0))
                    .as("nobody's slot left for a ninth player")
                    .isEqualTo(-1);
        }
    }

    private static int mostLeadOver(SessionHarness session, Machine machine, int frames) {
        int most = machine.session.lead();
        for (int frame = 0; frame < frames; frame++) {
            session.run(1);
            most = Math.max(most, machine.session.lead());
        }
        return most;
    }
}
