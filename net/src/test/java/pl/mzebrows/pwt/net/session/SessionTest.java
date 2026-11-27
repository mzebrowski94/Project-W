package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Family;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.SessionHarness.Machine;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeView;

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
            assertThat(session.sequencer().desyncs()).isZero();
            assertThat(host.session.lead()).isEqualTo(session.config.pacing().leadMinTicks());
        }
    }

    /** Every machine's slots sit at that machine in every world, so the leash holds a screen together (B). */
    @Test
    void seatsEachPlayerAtTheMachineHePlaysFrom() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);

            session.run(120);

            Presence presence = SessionHarness.world(friend)
                    .engine()
                    .getEntitiesFor(Family.all(Presence.class).get())
                    .first()
                    .getComponent(Presence.class);
            assertThat(presence.machineOf(host.session.slotOf(0)))
                    .isNotEqualTo(presence.machineOf(friend.session.slotOf(0)));
        }
    }

    @Test
    void staysInStepOverALaggyLossyNetwork() {
        try (SessionHarness session = new SessionHarness("Skirmish", 5L, new Conditions(0, 0, 0.05f, 0.02f, 3L))) {
            Machine host = session.host();
            Machine far = session.join("far", ACROSS_A_CITY);

            session.run(1200);
            session.agreedHash();

            assertThat(session.sequencer().desyncs()).isZero();
            assertThat(far.session.lead())
                    .as("a peer 40 ms away plays further ahead than the host")
                    .isGreaterThan(host.session.lead())
                    .isLessThanOrEqualTo(session.config.pacing().leadMaxTicks());
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
     * A machine drawing fewer frames than the world has ticks sends its input once a frame, so the
     * sequencer seals a frame's worth of ticks between two of its inputs, and its lead has to cover that
     * as well as the round trip. At 12 frames a second it also holds more ticks every frame than a
     * catch-up of {@code catchUpTicks} plays. The 2026-10-01 playtest: a Hamachi joiner drawing 31 frames had
     * 17 inputs a second repeated, and at 24 the lead never left its least and every input was.
     */
    @ParameterizedTest
    @ValueSource(strings = {"x..", "x.x..", "xx...", "x....", "xxx...."})
    void sealsTheInputOfAMachineDrawingFewerFramesThanTicks(String cadence) {
        try (SessionHarness session = new SessionHarness("Skirmish", 29L, PERFECT)) {
            session.host();
            Machine slow = session.join("slow", STEADY_40_MS);
            slow.cadence = cadence;
            session.run(600);
            int before = session.sequencer().repeatedFor(slow.session.peerId());

            session.run(600);

            assertThat(session.sequencer().repeatedFor(slow.session.peerId()) - before)
                    .as("its ticks sealed with a repeated input, of 600")
                    .isZero();
            assertThat(session.sequencer().sealedTick() - slow.session.tick() - slow.session.cushion())
                    .as("ticks it is behind, beyond those it keeps in hand")
                    .isLessThanOrEqualTo(12);
        }
    }

    /**
     * A link that stops now and then - a relay, a busy radio - holds a machine's input and lets it all go at
     * once. That input is late, and repeated for the spike; but a lead raised to cover every spike is that
     * much input delay all the time, and arcade play is judged by it (pillar 1). Measured on 2026-10-01: 120 ms
     * once a second took the lead from 8 ticks to 14.
     */
    @Test
    void keepsTheInputDelayOfAMachineOnASpikyLinkWhereItsRoundTripPutsIt() {
        double steady = meanLead(STEADY_40_MS);
        double spiky = meanLead(new Conditions(40, 0, 0f, 0f, 1000, 120, 13L));

        // What it keeps in hand against the spikes the lead covers too, and follows a moment late.
        assertThat(spiky)
                .as("mean lead beyond what it keeps in hand, under spikes, against %.1f without", steady)
                .isLessThanOrEqualTo(steady + 2);
    }

    /**
     * After a spike a machine holds a few ticks it could not play, and catching up on them one extra a
     * frame is a short speed-up; played as fast as a frame may it is a jump. Only a backlog no round trip
     * could have made - a world that came late, a game that stopped - is played that fast.
     */
    @Test
    void catchesUpAfterASpikeWithoutJumping() {
        try (SessionHarness session = new SessionHarness("Skirmish", 79L, PERFECT)) {
            session.host();
            Machine spiky = session.join("spiky", new Conditions(40, 0, 0f, 0f, 1000, 200, 13L));
            session.run(600);

            int most = 0;
            for (int frame = 0; frame < 1800; frame++) {
                int before = spiky.session.tick();
                session.run(1);
                most = Math.max(most, spiky.session.tick() - before);
            }

            assertThat(most).as("most ticks played in one frame").isLessThanOrEqualTo(2);
        }
    }

    /**
     * What a host sends a far machine, eight seats between them, every one moving all the time: each tick
     * goes in redundantTicks datagrams, the first copy of a tick whole and the rest as they changed, and
     * movement in two bytes rather than four. Measured on 2026-10-01: 28.5 kB a second per machine with
     * every seat's whole input in every copy, 8.8 kB after.
     */
    @Test
    void sendsAFarMachineOfEightSeatsLittleMoreThanItsChanges() {
        try (SessionHarness session = new SessionHarness("Skirmish", 89L, PERFECT)) {
            Machine host = session.host();
            Machine far = session.join("far", new Conditions(40, 5, 0.01f, 0f, 17L));
            for (int seat = 1; seat < 4; seat++) {
                host.session.seat(seat);
                far.session.seat(seat);
            }
            session.run(600);
            long before = session.unreliableBytesSent;

            session.run(600);

            assertThat((session.unreliableBytesSent - before) / 2 / 10)
                    .as("bytes a second to each machine")
                    .isLessThanOrEqualTo(9_500);
        }
    }

    /**
     * A machine whose link delivers ticks unevenly keeps a few of them in hand, so a late one is covered
     * rather than frozen on. The 2026-10-01 playtest's Hamachi joiner at 145 frames a second held none,
     * and its world stood still a tenth to a quarter of the time over a link that swung 80 to 175 ms.
     */
    @Test
    void keepsTicksInHandOnALinkThatDeliversThemUnevenly() {
        try (SessionHarness session = new SessionHarness("Skirmish", 97L, PERFECT)) {
            session.frameNanos = 1_000_000_000L / 145;
            session.host();
            Machine far = session.join("far", new Conditions(45, 40, 0f, 0f, 1000, 120, 7L));
            session.run(145 * 15);

            int frozen = 0;
            int frames = 145 * 30;
            for (int frame = 0; frame < frames; frame++) {
                session.run(1);
                frozen += far.session.starvedFrames() > 0 ? 1 : 0;
            }

            assertThat(100.0 * frozen / frames)
                    .as("percent of frames with nothing to play")
                    .isLessThan(2.5);
        }
    }

    /** A link that delivers evenly needs nothing in hand, and is given nothing: no delay for nothing. */
    @Test
    void keepsNothingInHandOnALinkThatDeliversEvenly() {
        try (SessionHarness session = new SessionHarness("Skirmish", 101L, PERFECT)) {
            session.host();
            Machine far = session.join("far", STEADY_40_MS);

            int most = 0;
            for (int frame = 0; frame < 1800; frame++) {
                session.run(1);
                most = Math.max(most, far.session.cushion());
            }

            assertThat(most).as("most ticks it ever kept in hand").isZero();
        }
    }

    private static double meanLead(Conditions link) {
        try (SessionHarness session = new SessionHarness("Skirmish", 83L, PERFECT)) {
            session.host();
            Machine far = session.join("far", link);
            session.run(900);
            long sum = 0;
            for (int frame = 0; frame < 1800; frame++) {
                session.run(1);
                sum += far.session.lead() - far.session.cushion();
            }
            return sum / 1800.0;
        }
    }

    /**
     * A drop-in whose world reached it late starts hundreds of ticks behind, and none of its input counts
     * until it has played them. Playing one extra a frame, the 2026-10-01 playtest's Hamachi joiner at 31
     * frames a second took seven seconds over 214. Further behind than any round trip, a frame plays as many
     * as it may; the last leadMaxTicks it plays one extra a frame, as after a spike.
     */
    @ParameterizedTest
    @CsvSource({"x, 110", "x., 270"})
    void catchesUpADropInWhoseWorldCameLate(String cadence, int mostFrames) {
        try (SessionHarness session = new SessionHarness("Skirmish", 67L, PERFECT)) {
            session.host();
            session.run(300);
            Machine late = session.join("late", STEADY_40_MS);
            late.cadence = cadence;
            while (late.session.peerId() < 0) {
                session.run(1);
            }
            late.frozenFrames = 210;
            session.run(210);
            while (late.session.state() != PeerSession.State.PLAYING) {
                session.run(1);
            }
            assertThat(session.sequencer().sealedTick() - late.session.tick())
                    .as("ticks behind once it has built its world")
                    .isGreaterThan(200);

            int frames = 0;
            while (session.sequencer().sealedTick() - late.session.tick() > 8 + late.session.cushion()
                    && frames < 1000) {
                session.run(1);
                frames++;
            }

            assertThat(frames).as("60 Hz frames until it is in step").isLessThanOrEqualTo(mostFrames);
        }
    }

    /**
     * What a machine's own log says of its frames, so a slow one can be told from a slow network: how many
     * it drew, the longest, and the time the session's own work took of them. The 2026-10-01 playtest could
     * tell a joiner drew 31 frames a second only from how many inputs it sent.
     */
    @Test
    void logsHowManyFramesAMachineDrewAndHowLongItsLongestWas(@TempDir Path dir) throws IOException {
        NetConfig logged = NetConfig.defaults().with(new NetConfig.LogConfig(dir.toString(), "INFO", 64, 1));
        NetLog log = NetLog.open(logged.log(), "slow");
        try (SessionHarness session = new SessionHarness(logged, "Skirmish", 71L, PERFECT, NetLog.silent(64))) {
            session.host();
            session.peerLog = log;
            Machine slow = session.join("slow", PERFECT);
            slow.cadence = "x.";

            session.run(300);
        }
        log.close();

        assertThat(Files.readString(Objects.requireNonNull(log.file()), StandardCharsets.UTF_8))
                .containsPattern("cat=stats peer=1 .* frames=30 frameMostMs=33 workMs=\\d+ workMostMs=\\d+");
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

            assertThat(session.sequencer().sealedTick() - far.session.tick())
                    .as("ticks the far machine is behind")
                    .isLessThanOrEqualTo(24);
            assertThat(far.session.lead())
                    .as("the far machine's input delay")
                    .isLessThan(session.config.pacing().leadMaxTicks());
            session.agreedHash();
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    /**
     * The welcome goes by the stream and carries the host's sim config, so over a real network it
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
            assertThat(session.sequencer().desyncs()).isZero();
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

            assertThat(session.sequencer().sealedTick() - stalled.session.tick())
                    .as("ticks behind three seconds after a stall of nearly seven")
                    .isLessThanOrEqualTo(session.config.pacing().bufferTicks() + 2);
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
                .containsPattern("event=welcome peer=1 .* setupBytes=\\d+ wireBytes=\\d+")
                .containsPattern("event=snapshot-sent peer=1 tick=\\d+ bytes=\\d+ wireBytes=\\d+ generation=1")
                .containsPattern("event=loaded peer=1 generation=1 ms=\\d+ behind=\\d+")
                .containsPattern("event=caught-up peer=1 generation=1 ms=\\d+");
    }

    /**
     * Every machine's log starts with its machine id, and the host's log says which machine each peer is:
     * the 2026-10-01 playtest's three computers each wrote logs called instance0, lined up only by addresses.
     */
    @Test
    void namesTheMachineBehindEachPeerInTheHostsLog(@TempDir Path dir) throws IOException {
        NetConfig logged = NetConfig.defaults().with(new NetConfig.LogConfig(dir.toString(), "INFO", 64, 0));
        NetLog log = NetLog.open(logged.log(), "host");
        try (SessionHarness session = new SessionHarness(logged, "Skirmish", 61L, PERFECT, log)) {
            session.host();
            session.join("friend", PERFECT);

            session.run(60);
        }
        log.close();

        assertThat(Files.readString(Objects.requireNonNull(log.file()), StandardCharsets.UTF_8))
                .containsPattern("event=welcome peer=0 machine=host ")
                .containsPattern("event=welcome peer=1 machine=friend ");
    }

    /**
     * A machine whose game stops - its window held by its title bar, its console clicked into - goes on
     * answering its link, and once it has fallen behind and not built the world it was sent it costs the
     * others nothing: it is sent nothing more. It is kept rather than let go, its commander standing idle
     * and shown away the while, and it comes back by itself when its game runs again. The 2026-10-01
     * playtest let such a joiner go after 20 s as "too slow", and its own log said nothing of the stop.
     */
    @Test
    void keepsAMachineWhoseGameStoppedAndTakesItBackWhenItRunsAgain(@TempDir Path dir) throws IOException {
        NetConfig logged = NetConfig.defaults().with(new NetConfig.LogConfig(dir.toString(), "INFO", 64, 1));
        NetLog log = NetLog.open(logged.log(), "stopping");
        try (SessionHarness session = new SessionHarness(logged, "Skirmish", 59L, PERFECT, NetLog.silent(64))) {
            Machine host = session.host();
            session.peerLog = log;
            Machine stopping = session.join("stopping", PERFECT);
            session.run(300);
            int slot = stopping.session.slotOf(0);

            stopping.frozenFrames = 26 * 60;
            session.run(24 * 60);
            boolean awayWhileStopped = presence(host).isAway(slot);
            session.run(2 * 60 + 600);

            assertThat(awayWhileStopped).as("away while its game is stopped").isTrue();
            assertThat(stopping.session.closedBecause()).as("let go because").isNull();
            assertThat(stopping.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(session.sequencer().sealedTick() - stopping.session.tick())
                    .as("ticks behind ten seconds after its game ran again")
                    .isLessThanOrEqualTo(8);
            assertThat(presence(host).isAway(slot)).as("away once back").isFalse();
        }
        log.close();
        assertThat(Files.readString(Objects.requireNonNull(log.file()), StandardCharsets.UTF_8))
                .containsPattern("event=frame-stall ms=26\\d{3}");
    }

    /** A restart makes a new world with nobody away in it - except a machine whose game is still stopped. */
    @Test
    void restartsWithAStoppedMachinesCommanderAway() {
        try (SessionHarness session = new SessionHarness("Skirmish", 73L, PERFECT)) {
            Machine host = session.host();
            Machine stopping = session.join("stopping", PERFECT);
            session.run(300);
            int slot = stopping.session.slotOf(0);
            stopping.frozenFrames = 60 * 60;
            session.run(24 * 60);

            host.session.restart();
            session.run(60);

            assertThat(presence(host).isAway(slot)).as("away in the new world").isTrue();
        }
    }

    @Test
    void leavesAJoinerWaitingRatherThanFailWhenTheWorldIsTooBigToSend() {
        try (SessionHarness session = new SessionHarness("Advance", 3L, PERFECT)) {
            session.snapshotLimit = 20_000;
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
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    @Test
    void findsAMachineKnockedOutOfStepDumpsItAndPutsItBack(@TempDir Path dumps) throws IOException {
        try (SessionHarness session = new SessionHarness("Skirmish", 13L, PERFECT)) {
            session.join("host", PERFECT, SessionHarness.HOST_KEY, dumps, BuildId.current(), 0)
                    .session
                    .start(session.setup, 1f / 60f);
            Machine knocked = session.join("knocked", PERFECT, 0L, dumps, BuildId.current(), 37);
            session.run(200);

            // A tick nobody sealed: the one thing lockstep cannot survive, done on purpose.
            Simulation world = SessionHarness.world(knocked);
            world.step(new PlayerInputs(world.capacity()));
            session.run(200);
            session.agreedHash();

            assertThat(session.sequencer().desyncs()).isEqualTo(1);
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
    void comparesAJoinerAtTheHostsChecksumTicksWhateverItsOwnFileSays() {
        NetConfig own = NetConfig.defaults().with(new NetConfig.Session(37));
        try (SessionHarness session = new SessionHarness("Skirmish", 13L, PERFECT)) {
            session.host();
            Machine knocked = session.join("knocked", PERFECT, own);
            session.run(200);

            Simulation world = SessionHarness.world(knocked);
            world.step(new PlayerInputs(world.capacity()));
            session.run(200);

            assertThat(session.sequencer().desyncs()).isEqualTo(1);
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
            int pausedAt = session.sequencer().sealedTick();
            session.run(60);
            assertThat(session.sequencer().sealedTick()).isEqualTo(pausedAt);
            assertThat(host.session.paused()).isTrue();
            friend.session.pause(false);
            session.run(10);
            assertThat(session.sequencer().sealedTick()).isGreaterThan(pausedAt);

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
            assertThat(session.sequencer().desyncs()).isZero();
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

    private static Presence presence(Machine machine) {
        return SessionHarness.world(machine)
                .engine()
                .getEntitiesFor(Family.all(Presence.class).get())
                .first()
                .getComponent(Presence.class);
    }

    private static int mostLeadOver(SessionHarness session, Machine machine, int frames) {
        int most = machine.session.lead();
        for (int frame = 0; frame < frames; frame++) {
            session.run(1);
            most = Math.max(most, machine.session.lead());
        }
        return most;
    }

    /** A card taken on a joining machine is sealed like an order and lands in every world (upgrades S8). */
    @Test
    void carriesACardTakenOnAJoiningMachineToEveryMachine() {
        try (SessionHarness session = new SessionHarness("Thicket" + TestWorlds.DRAFTING, 23L, ACROSS_A_CITY)) {
            session.host();
            Machine friend = session.join("friend", ACROSS_A_CITY);
            int serial = runUntilDraftFor(session, friend, 1);
            UpgradeKind kind = view(friend).cardOf(serial, 0);
            assertThat(kind).isNotNull();

            friend.pick(serial, kind);
            session.run(120);
            session.agreedHash();

            for (Machine machine : session.machines) {
                assertThat(ranks(machine, kind))
                        .as("%s holds %s", machine.name, kind)
                        .isPositive();
            }
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    /** What is held crosses in the snapshot: a machine that drops in after three cards holds all three. */
    @Test
    void dropsAPlayerInWithEveryCardTakenBeforeHeCame() {
        try (SessionHarness session = new SessionHarness("Thicket" + TestWorlds.DRAFTING, 29L, PERFECT)) {
            Machine host = session.host();
            int taken = 0;
            for (int frame = 0; frame < 2400 && taken < 3; frame++) {
                session.run(1);
                if (host.session.simulation() == null) {
                    continue;
                }
                int serial = view(host).oldestDraftOf(0);
                if (serial != 0 && view(host).cardOf(serial, 0) != null) {
                    host.pick(serial, view(host).cardOf(serial, 0));
                    session.run(10);
                    taken++;
                }
            }
            assertThat(taken).isEqualTo(3);

            Machine late = session.join("late", ACROSS_A_CITY);
            session.run(600);
            long agreed = session.agreedHash();

            assertThat(agreed).isEqualTo(pl.mzebrows.pwt.sim.WorldCodec.hash(SessionHarness.world(late)));
            assertThat(view(late).heldCount()).isEqualTo(view(host).heldCount()).isPositive();
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    /**
     * A dropped link is sealed as away on every machine, and his slot goes on being dealt its own drafts and keeps
     * them (G0118); a machine seated in the slot again is sealed as back, and finds them waiting (G0052).
     */
    @Test
    void keepsADroppedPlayersDraftsAndTakesHimBackWhenSomebodySitsInHisSlot() {
        try (SessionHarness session = new SessionHarness("Thicket" + TestWorlds.DRAFTING, 31L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(300);

            session.leave(friend);
            session.run(60);
            assertThat(view(host).away(1))
                    .as("slot 1 away once his link dropped")
                    .isTrue();
            runUntilDraftCount(session, host, 0, 3);
            int held = view(host).draftsOf(1);
            assertThat(held)
                    .as("an away slot is dealt his own, as the present one is")
                    .isEqualTo(view(host).draftsOf(0));

            Machine back = session.join("back", PERFECT);
            session.run(300);
            session.agreedHash();

            assertThat(back.session.slotOf(0)).isEqualTo(1);
            assertThat(view(back).draftsOf(1))
                    .as("the drafts he was dealt while away are his on his return")
                    .isGreaterThanOrEqualTo(held);
            for (Machine machine : session.machines) {
                assertThat(view(machine).away(1))
                        .as("%s sees slot 1 back", machine.name)
                        .isFalse();
            }
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    /** Whether a pick counts is the simulation's to say, on every machine alike: one for no draft changes nothing. */
    @Test
    void changesNothingAnywhereForACardFromADraftThatIsGone() {
        try (SessionHarness session = new SessionHarness("Thicket" + TestWorlds.DRAFTING, 37L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            runUntilDraftFor(session, host, 0);

            friend.pick(9_999, UpgradeKind.MARKSMEN);
            host.pick(9_998, UpgradeKind.FIRST_AID);
            session.run(60);
            session.agreedHash();

            for (Machine machine : session.machines) {
                assertThat(view(machine).heldCount())
                        .as("%s holds nothing", machine.name)
                        .isZero();
            }
            assertThat(session.sequencer().desyncs()).isZero();
        }
    }

    /** A solo player's draft pauses the session with its reason, so no machine covers the view (D2). */
    @Test
    void tellsEveryMachineAPauseIsForADraft() {
        try (SessionHarness session = new SessionHarness("Skirmish", 41L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(120);

            host.session.pauseForDraft(true);
            session.run(10);
            assertThat(friend.session.paused()).isTrue();
            assertThat(friend.session.pausedForDraft()).isTrue();

            host.session.pauseForDraft(false);
            session.run(10);
            friend.session.pause(true);
            session.run(10);
            assertThat(host.session.paused()).isTrue();
            assertThat(host.session.pausedForDraft()).isFalse();
        }
    }

    /**
     * A friend coming back while the one player left is paused for his draft ends the pause: the game is
     * co-op again, and co-op never pauses (D2). Nothing is sealed while paused, so the host lifts it.
     */
    @Test
    void liftsADraftPauseWhenASecondPlayerSitsDown() {
        try (SessionHarness session = new SessionHarness("Thicket" + TestWorlds.DRAFTING, 43L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(120);
            session.leave(friend);
            session.run(60);

            host.session.pauseForDraft(true);
            session.run(10);
            assertThat(host.session.paused()).isTrue();
            Machine back = session.join("back", PERFECT);
            session.run(300);

            assertThat(host.session.paused()).isFalse();
            assertThat(back.session.slotOf(0)).isEqualTo(1);
            assertThat(view(host).away(1)).isFalse();
            session.agreedHash();
        }
    }

    /** A hand's pause is nobody's to lift but a hand's: somebody sitting down leaves it on. */
    @Test
    void keepsAPlayersPauseWhenSomebodySitsDown() {
        try (SessionHarness session = new SessionHarness("Thicket", 47L, PERFECT)) {
            Machine host = session.host();
            session.run(120);

            host.session.pause(true);
            session.run(10);
            session.join("friend", PERFECT);
            session.run(120);

            assertThat(host.session.paused()).isTrue();
        }
    }

    /** Runs until this machine's world shows a draft for this slot, and returns its serial. */
    private static int runUntilDraftFor(SessionHarness session, Machine machine, int slot) {
        for (int frame = 0; frame < 2400; frame++) {
            session.run(1);
            Simulation world = machine.session.simulation();
            if (world != null && world.upgradeView().oldestDraftOf(slot) != 0) {
                return world.upgradeView().oldestDraftOf(slot);
            }
        }
        throw new AssertionError(machine.name + " never saw a draft for slot " + slot);
    }

    private static void runUntilDraftCount(SessionHarness session, Machine machine, int slot, int drafts) {
        for (int frame = 0; frame < 2400; frame++) {
            session.run(1);
            Simulation world = machine.session.simulation();
            if (world != null && world.upgradeView().draftsOf(slot) >= drafts) {
                return;
            }
        }
        Simulation world = SessionHarness.world(machine);
        int captured = 0;
        for (com.badlogic.ashley.core.Entity point : world.engine()
                .getEntitiesFor(com.badlogic.ashley.core.Family.all(pl.mzebrows.pwt.sim.component.ControlPoint.class)
                        .get())) {
            captured += point.getComponent(pl.mzebrows.pwt.sim.component.ControlPoint.class).captured ? 1 : 0;
        }
        throw new AssertionError(machine.name + " never saw " + drafts + " drafts for slot " + slot + ": slot 0 "
                + world.upgradeView().draftsOf(0) + ", slot 1 "
                + world.upgradeView().draftsOf(1) + ", captured "
                + captured + ", tick " + machine.session.tick() + ", held "
                + world.upgradeView().heldCount());
    }

    private static UpgradeView view(Machine machine) {
        return SessionHarness.world(machine).upgradeView();
    }

    /** The ranks of this upgrade held, whoever holds them. */
    private static int ranks(Machine machine, UpgradeKind kind) {
        UpgradeView view = view(machine);
        int ranks = 0;
        for (int i = 0; i < view.heldCount(); i++) {
            if (view.heldKind(i) == kind) {
                ranks += view.heldRanks(i);
            }
        }
        return ranks;
    }

    // The briefing (G0101): a hosted session is open before its first tick.

    @Test
    void waitsOnItsBriefingWithNoWorldUntilTheHostStartsAndThenPutsEverySeatedSlotOnTheField() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.hostOnBriefing();
            Machine friend = session.join("friend", PERFECT);

            session.run(120);

            assertThat(host.session.state()).isEqualTo(PeerSession.State.BRIEFING);
            assertThat(friend.session.state()).isEqualTo(PeerSession.State.BRIEFING);
            assertThat(friend.session.simulation()).isNull();
            assertThat(session.sequencer().sealedTick()).isEqualTo(-1);
            assertThat(friend.session.players())
                    .extracting(BriefingPlayer::slot, BriefingPlayer::machine, BriefingPlayer::host)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(0, "host", true),
                            org.assertj.core.groups.Tuple.tuple(1, "friend", false));

            host.session.start(session.setup, 1f / 60f);
            session.run(20);

            assertThat(host.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(friend.session.tick()).as("the first ticks").isBetween(0, 30);
            assertThat(friend.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(commanders(friend))
                    .as("on the field at the first ticks, not dropped in later: tick %d", friend.session.tick())
                    .isEqualTo(2);
            session.run(300);
            session.agreedHash();
        }
    }

    @Test
    void aMachineArrivingAfterTheStartDropsInAndOneBeforeLandsOnTheBriefing() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.hostOnBriefing();
            session.run(30);
            Machine early = session.join("early", PERFECT);
            session.run(30);
            assertThat(early.session.state()).isEqualTo(PeerSession.State.BRIEFING);

            host.session.start(session.setup, 1f / 60f);
            session.run(120);
            Machine late = session.join("late", PERFECT);
            session.run(300);

            assertThat(late.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(late.session.slotOf(0)).isEqualTo(2);
            session.agreedHash();
            assertThat(commanders(late)).isEqualTo(3);
        }
    }

    @Test
    void goesBackToItsBriefingWithTheSlotsKeptAndStartsAnotherMission() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(200);

            host.session.toBriefing();
            session.run(30);

            assertThat(host.session.state()).isEqualTo(PeerSession.State.BRIEFING);
            assertThat(friend.session.state()).isEqualTo(PeerSession.State.BRIEFING);
            assertThat(friend.session.simulation()).isNull();
            assertThat(friend.session.setup()).isNull();
            assertThat(friend.session.slotOf(0)).as("his slot is kept").isEqualTo(1);
            assertThat(host.session.players()).hasSize(2);

            Machine newcomer = session.join("newcomer", PERFECT);
            session.run(30);
            assertThat(newcomer.session.state()).as("lands on the briefing").isEqualTo(PeerSession.State.BRIEFING);

            host.session.start(new SessionSetup("", "Skirmish", 9L), 1f / 60f);
            session.run(30);
            // Checked before the fight can take a man: this test is about the slots coming back, and a
            // scripted commander walking about with his hand out dies now and again.
            assertThat(commanders(newcomer))
                    .as("every player's man is on the field again")
                    .isEqualTo(3);
            session.run(270);
            session.agreedHash();
            assertThat(friend.session.setup().seed()).isEqualTo(9L);
        }
    }

    @Test
    void showsAMachineWhoseLinkIsGoneOfflineAndGivesItsSlotBackWhenItReturnsBeforeTheStart() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.hostOnBriefing();
            Machine friend = session.join("friend", PERFECT);
            session.run(60);

            session.vanish(friend);
            session.run(30);
            assertThat(host.session.players())
                    .extracting(BriefingPlayer::machine, BriefingPlayer::online)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("host", true),
                            org.assertj.core.groups.Tuple.tuple("friend", false));

            Machine back = session.join("friend", PERFECT);
            session.run(60);
            assertThat(back.session.slotOf(0)).isEqualTo(1);
            assertThat(host.session.players()).hasSize(2).allMatch(BriefingPlayer::online);

            host.session.start(session.setup, 1f / 60f);
            session.run(120);
            session.agreedHash();
        }
    }

    @Test
    void putsAMachineThatIsStillGoneOnTheFieldAwayWhenTheHostStarts() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.hostOnBriefing();
            Machine friend = session.join("friend", PERFECT);
            session.run(60);
            session.vanish(friend);
            session.run(30);

            host.session.start(session.setup, 1f / 60f);
            session.run(120);

            assertThat(commanders(host)).isEqualTo(2);
            Presence presence = SessionHarness.world(host)
                    .engine()
                    .getEntitiesFor(Family.all(Presence.class).get())
                    .first()
                    .getComponent(Presence.class);
            assertThat(presence.isAway(1)).isTrue();
        }
    }

    @Test
    void showsEveryMachineOnTheBriefingWhatTheHostPutThereAndOnlyTheHost() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
            Machine host = session.hostOnBriefing();
            Machine early = session.join("early", PERFECT);
            session.run(30);

            host.session.note("MJGF-6ABCD");
            early.session.note("not the host's to say");
            session.run(30);
            Machine late = session.join("late", PERFECT);
            session.run(30);

            assertThat(early.session.note()).isEqualTo("MJGF-6ABCD");
            assertThat(late.session.note()).as("a machine that arrives is told").isEqualTo("MJGF-6ABCD");
        }
    }

    private static int commanders(Machine machine) {
        return SessionHarness.world(machine)
                .engine()
                .getEntitiesFor(Family.all(pl.mzebrows.pwt.sim.component.Commander.class)
                        .get())
                .size();
    }

    @Test
    void bringsAMachineBackToItsSlotWithTheHostsWorldAfterABlip() {
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
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
        try (SessionHarness session = new SessionHarness("Skirmish", 3L, PERFECT)) {
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
        try (SessionHarness session = new SessionHarness(brief, "Skirmish", 3L, PERFECT)) {
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

    @Test
    void endsAnUnroomedSessionWithItsHostWhoseLinkIsGonePastTheWindow() {
        NetConfig brief = NetConfig.defaults().with(new NetConfig.Rejoin(2, 200));
        try (SessionHarness session = new SessionHarness(brief, "Skirmish", 3L, PERFECT)) {
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(200);

            session.dropLink(host);
            host.frozenFrames = 10_000;
            session.run(300);

            assertThat(friend.session.state()).isEqualTo(PeerSession.State.CLOSED);
            assertThat(friend.session.closedBecause()).contains("the host left the game");
        }
    }

    @Test
    void handsAnUnroomedSessionOverWhenItsSequencerIsToldTo() {
        NetConfig brief = NetConfig.defaults().with(new NetConfig.Rejoin(2, 200));
        try (SessionHarness session = new SessionHarness(brief, "Skirmish", 3L, PERFECT)) {
            session.sequencer().handsOver(true);
            Machine host = session.host();
            Machine friend = session.join("friend", PERFECT);
            session.run(200);

            session.dropLink(host);
            host.frozenFrames = 10_000;
            session.machines.remove(host);
            session.run(300);

            assertThat(friend.session.state()).isEqualTo(PeerSession.State.PLAYING);
            assertThat(friend.session.hosting()).isTrue();
            assertThat(friend.session.hostChanges()).isEqualTo(1);
        }
    }
}
