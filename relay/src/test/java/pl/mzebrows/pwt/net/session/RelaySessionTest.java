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
import pl.mzebrows.pwt.sim.WorldCodec;

/**
 * Whole sessions through a relay (ADR 0023): the sequencer is a room's, every machine - the host's peer too -
 * reaches it through the door, and the host's peer is one link among the others: the reference world, the
 * snapshot source, the owner of START. The scenarios are the ones of {@code SessionTest} that a room could break.
 */
class RelaySessionTest {

    private static final Conditions PERFECT = Conditions.NONE;

    /** Across a city: 40 ms each way give or take 10, one message in ten lost, one in twenty doubled. */
    private static final Conditions ACROSS_A_CITY = new Conditions(40, 10, 0.1f, 0.05f, 7L);

    /** About 20 ms from every machine to a relay in the same region: ADR 0023's premise, half each way. */
    private static final Conditions TEN_MS_EACH_WAY = new Conditions(10, 0, 0f, 0f, 17L);

    private static SessionHarness through(String scenario, long seed, Conditions everyLink) {
        return new SessionHarness(NetConfig.defaults(), scenario, seed, everyLink, RelayFront::new);
    }

    @Test
    void playsTheSameWorldOnEveryMachineThroughTheRoom() {
        try (SessionHarness session = through("Skirmish", 3L, PERFECT)) {
            Machine host = session.host();
            Machine joiner = session.join("joiner", PERFECT);
            session.run(300);
            session.agreedHash();

            assertThat(joiner.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(joiner.session.slotOf(0)).isEqualTo(1);
            assertThat(host.session.slotOf(0)).isEqualTo(0);
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    @Test
    void holdsEightPlayersFromFourMachines() {
        try (SessionHarness session = through("Skirmish", 23L, PERFECT)) {
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
            assertThat(ninth.session.slotOf(0)).isEqualTo(-1);
        }
    }

    @Test
    void dropsAPlayerIntoAGameInProgressWithTheHostsWorldCarriedByTheRelay() {
        try (SessionHarness session = through("Adventure", 11L, PERFECT)) {
            Machine host = session.host();
            session.run(900);

            Machine late = session.join("late", ACROSS_A_CITY);
            session.run(600);
            long agreed = session.agreedHash();

            assertThat(late.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(late.session.slotOf(0)).isEqualTo(1);
            assertThat(agreed).isEqualTo(WorldCodec.hash(SessionHarness.world(late)));
            assertThat(SessionHarness.world(host).capacity()).isEqualTo(Players.MAX);
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    @Test
    void findsAMachineKnockedOutOfStepAndPutsItBackWithTheHostsSnapshot(@TempDir Path dumps) throws IOException {
        try (SessionHarness session = through("Skirmish", 13L, PERFECT)) {
            session.join("host", PERFECT, SessionHarness.HOST_KEY, dumps, BuildId.current(), 0)
                    .session
                    .start(session.setup, 1f / 60f);
            Machine knocked = session.join("knocked", PERFECT, 0L, dumps, BuildId.current(), 37);
            session.run(200);

            Simulation world = SessionHarness.world(knocked);
            world.step(new PlayerInputs(world.capacity()));
            session.run(200);
            session.agreedHash();

            assertThat(session.sequencer().desyncs()).isEqualTo(1);
            try (Stream<Path> files = Files.list(dumps)) {
                assertThat(files.filter(file -> file.toString().endsWith(".bin")))
                        .as("the host's machine and the diverged one each wrote their dump")
                        .hasSize(2);
            }
        }
    }

    @Test
    void staysInStepWithLagOnEveryLink() {
        try (SessionHarness session = through("Skirmish", 5L, ACROSS_A_CITY)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            session.run(900);
            session.agreedHash();

            assertThat(List.of(host, second, third)).allMatch(m -> m.session.state() == PeerSession.State.PLAYING);
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    /**
     * ADR 0023's headline: with about 20 ms from every machine to the relay, a host and a joiner alike play at a
     * lead of 3 ticks - the plain relay to a host's sequencer would leave the host at the floor of 2 and the
     * joiner near 4. Measured here at 10 ms each way on a 60 Hz harness: 3 and 3 when both start together.
     */
    @Test
    void putsTheHostAndAJoinerAtTheSameThreeTickLead() {
        try (SessionHarness session = through("Skirmish", 9L, TEN_MS_EACH_WAY)) {
            Machine host = session.hostOnBriefing();
            Machine joiner = session.join("joiner", PERFECT);
            session.run(30);
            host.session.start(session.setup, 1f / 60f);
            session.run(900);

            assertThat(new int[] {host.session.lead(), joiner.session.lead()})
                    .as("leads at 10 ms each way: host %d, joiner %d", host.session.lead(), joiner.session.lead())
                    .containsExactly(3, 3);
        }
    }

    /**
     * A joiner dropping into a running game comes down from the lead its load put it at, and the lead only steps
     * down when its margin is more than a tick to spare - so it may rest one above the host's. Measured: host 3,
     * joiner 4.
     */
    @Test
    void putsADropInJoinerWithinATickOfTheHostsLead() {
        try (SessionHarness session = through("Skirmish", 9L, TEN_MS_EACH_WAY)) {
            Machine host = session.host();
            session.run(300);
            Machine joiner = session.join("joiner", PERFECT);
            session.run(900);

            assertThat(host.session.lead()).isEqualTo(3);
            assertThat(joiner.session.lead())
                    .as("the drop-in's lead beside the host's %d", host.session.lead())
                    .isBetween(3, 4);
        }
    }

    @Test
    void endsTheRoomWhenTheHostsPeerLeavesAndTellsEveryLinkWhy() {
        try (SessionHarness session = through("Skirmish", 7L, PERFECT)) {
            Machine host = session.host();
            Machine joiner = session.join("joiner", PERFECT);
            session.run(120);

            session.leave(host);
            session.run(30);

            assertThat(joiner.session.state()).isEqualTo(PeerSession.State.CLOSED);
            assertThat(joiner.session.closedBecause()).contains("the host left the game");
        }
    }
}
