package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
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
}
