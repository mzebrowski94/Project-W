package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.loopback.LoopbackNetwork;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.net.session.PeerSession;
import pl.mzebrows.pwt.net.session.Sequencer;
import pl.mzebrows.pwt.net.session.SessionSetup;
import pl.mzebrows.pwt.net.transport.ConditionedTransport;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * Two copies of the game in one test: the host's {@link OnlineSession} and a joining one, over the
 * loopback network, with the sequencer pumped by hand where the host's thread would run it.
 */
class OnlineSessionTest {

    private static final long KEY = 0x70057L;
    private static final long FRAME = 16_666_667L;

    private final ManualClock clock = new ManualClock();
    private final NetConfig config = NetConfig.defaults();
    private final LoopbackNetwork network = new LoopbackNetwork(config);
    private final Sequencer sequencer = new Sequencer(
            network.endpoint("host"),
            config,
            NetLog.silent(64),
            new SessionSetup(SimConfigLoader.text(), "Skirmish", 5L),
            Players.MAX,
            1f / 60f,
            KEY,
            BuildId.current());
    private final int port = sequencer.listen(config.port(), 0);
    private final ConditionedTransport lag =
            new ConditionedTransport(network.endpoint("joiner"), NetConfig.Conditions.NONE, clock);
    private final OnlineSession host = new OnlineSession(
            peer(network.endpoint("host"), KEY),
            null,
            null,
            List.of(),
            NetLog.silent(64),
            clock,
            "host, port " + port,
            null);
    private final OnlineSession joiner = new OnlineSession(
            peer(lag, 0L), null, lag, List.of(0, 25, 50), NetLog.silent(64), clock, "host:" + port, null);
    private final PlayerInputs walking = new PlayerInputs(Players.MAX);

    @AfterEach
    void close() {
        host.close();
        joiner.close();
        sequencer.close();
    }

    @Test
    void saysWhereItIsConnectingUntilTheWorldArrives() {
        assertThat(joiner.simulation()).isNull();
        assertThat(joiner.scenarioName()).isNull();
        assertThat(joiner.connection()).startsWith("connecting to host:");
    }

    @Test
    void playsTheHostsScenarioInASlotOfItsOwn() {
        frames(120);

        assertThat(joiner.scenarioName()).isEqualTo("Skirmish");
        assertThat(joiner.simulation()).isNotNull();
        assertThat(host.slotOf(PlayerSeats.KEYBOARD_SLOT)).isZero();
        assertThat(joiner.slotOf(PlayerSeats.KEYBOARD_SLOT)).isEqualTo(1);
        assertThat(joiner.local(1)).isTrue();
        assertThat(joiner.local(0))
                .as("the host's commander is not this machine's to watch")
                .isFalse();
        assertThat(joiner.connection()).startsWith("online host:").contains("input delay");
    }

    @Test
    void pausesEveryMachineButLeavesSingleStepOffline() {
        frames(120);

        joiner.apply(HarnessCommand.PAUSE, walking);
        frames(20);
        long pausedAt = world(joiner).tick();
        joiner.apply(HarnessCommand.STEP, walking);
        frames(20);

        assertThat(host.paused()).isTrue();
        assertThat(joiner.paused()).isTrue();
        assertThat(world(joiner).tick()).isEqualTo(pausedAt);
        assertThat(joiner.renderAlpha()).isEqualTo(1f);
    }

    @Test
    void switchesGodModeOnlyFromTheHost() {
        frames(120);

        joiner.apply(HarnessCommand.TOGGLE_GOD_MODE, walking);
        frames(20);
        assertThat(world(host).commanderInvulnerable()).isFalse();

        host.apply(HarnessCommand.TOGGLE_GOD_MODE, walking);
        frames(20);
        assertThat(world(joiner).commanderInvulnerable()).isTrue();
    }

    @Test
    void stepsTheSimulatedLagOfAJoiningCopyOnly() {
        frames(120);

        joiner.apply(HarnessCommand.NEXT_LAG, walking);
        joiner.apply(HarnessCommand.NEXT_LAG, walking);
        host.apply(HarnessCommand.NEXT_LAG, walking);
        frames(60);

        assertThat(lag.conditions().latencyMillis()).isEqualTo(25);
        assertThat(joiner.connection()).contains("simulated lag 25 ms each way");
        assertThat(host.connection()).doesNotContain("simulated lag");
    }

    @Test
    void drawsItsOwnCommandersAheadUntilToldNotTo() {
        assertThat(joiner.cosmeticLead()).isTrue();

        joiner.apply(HarnessCommand.TOGGLE_COSMETIC_LEAD, walking);
        frames(120);

        assertThat(joiner.cosmeticLead()).isFalse();
        assertThat(joiner.connection()).contains("cosmetic lead off");
    }

    @Test
    void endsSayingWhyWhenTheHostLeaves() {
        frames(120);
        assertThat(joiner.ended()).isNull();

        sequencer.close();
        joiner.advance(1f / 60f, walking);

        assertThat(joiner.ended()).isNotNull().contains("the host left the game");
    }

    @Test
    void endsSayingWhyWhenTheHostWillNotHaveIt() {
        PeerSession old = new PeerSession(
                network.endpoint("stranger"), config, NetLog.silent(64), new ScenarioWorlds(), null, "old");
        old.connect("host", port, 0L);
        try (OnlineSession stranger =
                new OnlineSession(old, null, null, List.of(), NetLog.silent(64), clock, "host:" + port, null)) {
            for (int i = 0; i < 10; i++) {
                frames(1);
                stranger.advance(1f / 60f, walking);
            }

            assertThat(stranger.ended()).isNotNull().contains("refused").contains("old");
        }
    }

    @Test
    void remembersTheLastOrderGivenHere() {
        frames(120);
        walking.order(PlayerSeats.KEYBOARD_SLOT, pl.mzebrows.pwt.sim.def.Order.DEFENCE);
        frames(1);

        assertThat(joiner.lastOrder()).isEqualTo(pl.mzebrows.pwt.sim.def.Order.DEFENCE);
    }

    private void frames(int count) {
        for (int i = 0; i < count; i++) {
            clock.advanceNanos(FRAME);
            sequencer.pump(clock.nanos());
            host.advance(1f / 60f, walking);
            joiner.advance(1f / 60f, walking);
            walking.clear();
            walking.set(PlayerSeats.KEYBOARD_SLOT, new CommanderInput(1f, 0f, 60f, 60f, false, false));
        }
    }

    private static Simulation world(OnlineSession play) {
        Simulation world = play.simulation();
        assertThat(world).isNotNull();
        return world;
    }

    private PeerSession peer(pl.mzebrows.pwt.net.transport.Transport transport, long key) {
        PeerSession peer =
                new PeerSession(transport, config, NetLog.silent(64), new ScenarioWorlds(), null, BuildId.current());
        peer.connect("host", port, key);
        return peer;
    }
}
