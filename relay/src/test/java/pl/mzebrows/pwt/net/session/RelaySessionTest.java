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

    @Test
    void bringsAMachineBackToItsSlotWithTheHostsWorldAfterABlip() {
        try (SessionHarness session = through("Skirmish", 3L, PERFECT)) {
            session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(200);
            int slot = friend.session.slotOf(0);

            session.dropLink(friend);
            session.run(1);
            assertThat(friend.session.state()).isEqualTo(PeerSession.State.RECONNECTING);
            session.run(300);

            assertThat(friend.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(friend.session.slotOf(0)).isEqualTo(slot);
            session.agreedHash();
        }
    }

    @Test
    void keepsTheSessionAndPutsTheHostBackWhenItsLinkBlipsWithinTheWindow() {
        try (SessionHarness session = through("Skirmish", 3L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(200);

            session.dropLink(host);
            session.run(120);
            assertThat(friend.session.state())
                    .as("the session waits for its host")
                    .isEqualTo(PeerSession.State.PLAYING);
            session.run(300);

            assertThat(host.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(host.session.slotOf(0)).isZero();
            session.agreedHash();
        }
    }

    @Test
    void freesTheSeatWhenTheWindowEndsAndTurnsTheLateMachineAwayToJoinAgain() {
        NetConfig brief = NetConfig.defaults().with(new NetConfig.Rejoin(2, 200));
        try (SessionHarness session = new SessionHarness(brief, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(200);
            int slot = friend.session.slotOf(0);

            session.dropLink(friend);
            friend.frozenFrames = 400;
            session.run(300);
            Machine newcomer = session.join("newcomer", PERFECT);
            session.run(200);
            assertThat(newcomer.session.slotOf(0)).as("the lowest free slot").isEqualTo(slot);

            session.run(400);
            assertThat(friend.session.state()).isEqualTo(PeerSession.State.REFUSED);
            assertThat(friend.session.refusal()).contains("your place in the room was given up - join again");
        }
    }

    private static final NetConfig TWO_SECOND_WINDOW = NetConfig.defaults().with(new NetConfig.Rejoin(2, 200));

    /** The host's link is cut and his machine never answers: his window passes, 300 frames being 5 s of a 2 s window. */
    private static void loseTheHost(SessionHarness session, Machine host) {
        session.dropLink(host);
        host.frozenFrames = 10_000;
        session.machines.remove(host);
        session.run(300);
    }

    @Test
    void handsTheSessionToThePeerLongestInTheRoomWhenTheHostsLinkIsGonePastTheWindow() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            session.run(200);
            assertThat(second.session.hostPeer())
                    .as("who the host is, told from the first")
                    .isZero();
            assertThat(second.session.hosting()).isFalse();

            loseTheHost(session, host);

            assertThat(second.session.hosting()).as("the next oldest").isTrue();
            assertThat(third.session.hosting()).isFalse();
            assertThat(third.session.hostPeer()).isEqualTo(second.session.peerId());
            assertThat(third.session.hostSlot()).isEqualTo(second.session.slotOf(0));
            assertThat(session.sequencer().desyncs()).isZero();
            session.agreedHash();
        }
    }

    @Test
    void tellsEveryMachineOfTheNewHostOnTheSameTick() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            session.run(200);

            loseTheHost(session, host);

            assertThat(second.session.hostChanges()).isEqualTo(1);
            assertThat(third.session.hostChanges()).isEqualTo(1);
            assertThat(second.session.hostChangedAt())
                    .as("a tick of the mission")
                    .isPositive();
            assertThat(third.session.hostChangedAt()).isEqualTo(second.session.hostChangedAt());
        }
    }

    @Test
    void letsTheNewHostRestartTheMissionAndRefusesTheOthers() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            session.run(200);
            loseTheHost(session, host);

            Simulation before = SessionHarness.world(third);
            third.session.restart();
            session.run(30);
            assertThat(SessionHarness.world(third))
                    .as("a peer's restart is ignored")
                    .isSameAs(before);

            second.session.restart();
            session.run(30);
            assertThat(SessionHarness.world(third))
                    .as("the new host's restart is sealed")
                    .isNotSameAs(before);
            session.agreedHash();
        }
    }

    @Test
    void servesAJoinerTheWorldFromTheNewHost() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            session.join("second", PERFECT);
            session.run(200);
            loseTheHost(session, host);

            Machine late = session.join("late", PERFECT);
            session.run(300);

            assertThat(late.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(late.session.hostPeer()).isEqualTo(1);
            assertThat(session.sequencer().desyncs()).isZero();
            session.agreedHash();
        }
    }

    @Test
    void handsTheBriefingOverAndLetsOnlyTheNewHostStartTheMission() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.hostOnBriefing();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            session.run(60);
            session.dropLink(host);
            host.frozenFrames = 10_000;
            session.machines.remove(host);
            session.run(300);

            assertThat(second.session.hosting()).isTrue();
            assertThat(third.session.hostPeer()).isEqualTo(second.session.peerId());
            assertThat(third.session.players().get(0).host())
                    .as("the briefing lists him first")
                    .isTrue();
            assertThat(third.session.players().get(0).slot()).isEqualTo(second.session.slotOf(0));

            second.session.note("DAILY");
            session.run(10);
            assertThat(third.session.note()).isEqualTo("DAILY");

            Machine old = session.join("oldhost", PERFECT);
            session.run(30);
            old.session.start(session.setup, 1f / 60f);
            third.session.start(session.setup, 1f / 60f);
            session.run(30);
            assertThat(second.session.state())
                    .as("neither a peer's START began it")
                    .isEqualTo(PeerSession.State.BRIEFING);

            second.session.start(session.setup, 1f / 60f);
            session.run(60);
            assertThat(third.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(old.session.state()).isEqualTo(PeerSession.State.PLAYING);
        }
    }

    @Test
    void doesNotGiveTheSessionBackToTheOldHostWhoComesBackAPeer() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            session.run(200);
            session.dropLink(host);
            host.frozenFrames = 400;
            session.run(300);
            assertThat(second.session.hosting()).isTrue();

            session.run(300);
            assertThat(host.session.state()).as("his token is gone").isEqualTo(PeerSession.State.REFUSED);

            Machine again = session.join("oldhost", PERFECT);
            session.run(300);
            assertThat(again.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(again.session.hosting()).isFalse();
            assertThat(again.session.hostPeer()).isEqualTo(second.session.peerId());
        }
    }

    @Test
    void endsTheRoomForEveryoneWhenTheHostLeavesOnPurposeEvenWithPeersToTakeOver() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            Machine second = session.join("second", PERFECT);
            Machine third = session.join("third", PERFECT);
            session.run(200);

            session.leave(host);
            session.run(300);

            assertThat(second.session.state()).isEqualTo(PeerSession.State.CLOSED);
            assertThat(third.session.state()).isEqualTo(PeerSession.State.CLOSED);
            assertThat(third.session.closedBecause()).contains("the host left the game");
        }
    }

    @Test
    void closesTheRoomWhenTheHostIsGoneAndNobodyIsLeftToTakeOver() {
        try (SessionHarness session = new SessionHarness(TWO_SECOND_WINDOW, "Skirmish", 3L, PERFECT, RelayFront::new)) {
            Machine host = session.host();
            session.run(100);
            session.dropLink(host);
            host.frozenFrames = 400;
            session.run(300);
            session.run(300);

            assertThat(host.session.state()).as("the room is gone").isNotEqualTo(PeerSession.State.PLAYING);
        }
    }
}
