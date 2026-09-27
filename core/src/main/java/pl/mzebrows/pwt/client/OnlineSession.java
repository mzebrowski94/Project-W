package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.Family;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.lan.LanAddresses;
import pl.mzebrows.pwt.net.lan.LanBeacon;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.netty.NettyTransport;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.net.session.PeerSession;
import pl.mzebrows.pwt.net.session.Sequencer;
import pl.mzebrows.pwt.net.session.SequencerThread;
import pl.mzebrows.pwt.net.session.SessionSetup;
import pl.mzebrows.pwt.net.transport.ConditionedTransport;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.scenario.Scenario;

/**
 * This machine's place in an online session, as the scenario screen plays it (ADR 0012, G0038).
 *
 * <p>A copy that hosts runs the session's {@link Sequencer} on a thread of its own and joins it over
 * {@code 127.0.0.1} like anybody else, so the host's world is stepped by exactly the bundles every other
 * machine gets. A copy that joins connects to an address - over a {@link ConditionedTransport}, so the
 * latency playtest can give it a bad network both ways and step through latencies with F3.
 *
 * <p>The harness keys mean what they mean offline, where the session allows it. Pause is anybody's;
 * restart and god mode are the host's, and the sequencer ignores them from anyone else (G0038). A single
 * step is offline only.
 */
public final class OnlineSession implements ScenarioPlay {

    private static final Family COMMANDERS = Family.all(Commander.class).get();

    /** How many frames without a tick to play before the overlay says the machine is waiting - 100 ms. */
    private static final int WAITING_FRAMES = 6;

    private final PeerSession peer;
    private final @Nullable SequencerThread sequencer;
    private final @Nullable ConditionedTransport lag;
    private final List<Integer> lagSteps;
    private final NetLog log;
    private final Clock clock;
    private final String where;
    private final @Nullable Invitation invitation;

    private OverlayMode overlayMode = OverlayMode.PANEL;
    private boolean cosmeticLead = true;
    private boolean leaving;
    private boolean toldStepIsOffline;
    private int lagStep = -1;
    private @Nullable Order lastOrder;

    /**
     * @param sequencer the sequencer thread this copy runs, if it hosts
     * @param lag what the peer's link is wrapped in, if its lag can be stepped
     * @param where how the overlay names the session: the port hosted on, or the address joined
     * @param invitation how a hosting copy tells the network about itself and its players how to join
     */
    OnlineSession(
            PeerSession peer,
            @Nullable SequencerThread sequencer,
            @Nullable ConditionedTransport lag,
            List<Integer> lagSteps,
            NetLog log,
            Clock clock,
            String where,
            @Nullable Invitation invitation) {
        this.peer = peer;
        this.sequencer = sequencer;
        this.lag = lag;
        this.lagSteps = List.copyOf(lagSteps);
        this.log = log;
        this.clock = clock;
        this.where = where;
        this.invitation = invitation;
        // The keyboard is always somebody; pads are seated as they are picked up.
        peer.seat(PlayerSeats.KEYBOARD_SLOT);
    }

    /**
     * Hosts {@code scenario}: starts a sequencer on the configured port (the next free one if a copy on
     * this PC already has it), joins it, and announces it to the local network under a room code. What
     * is shipped to peers is this copy's {@code
     * game.properties} as written, and its seed.
     *
     * @throws RuntimeException if no port could be had; nothing is left open
     */
    public static OnlineSession host(Scenario scenario, SimConfig config, NetConfig net, LaunchOptions options) {
        Clock clock = SystemClock.INSTANCE;
        NetLog log = NetLog.open(net.log(), options.logName() + "-host");
        long key = new SecureRandom().nextLong() | 1L;
        Sequencer sequencer = new Sequencer(
                new NettyTransport(net, clock, log, "sequencer"),
                net,
                log,
                new SessionSetup(SimConfigLoader.text(), scenario.name(), config.seed()),
                Players.MAX,
                config.tickSeconds(),
                key,
                BuildId.current());
        int port;
        try {
            port = sequencer.listen(net.port(), net.portSearch());
        } catch (RuntimeException e) {
            sequencer.close();
            log.close();
            throw e;
        }
        SequencerThread thread = new SequencerThread(sequencer, clock, log).start();
        PeerSession peer = new PeerSession(
                new NettyTransport(net, clock, log, "peer"),
                net,
                log,
                new ScenarioWorlds(),
                dumps(net),
                BuildId.current());
        peer.connect(LOCAL, port, key);
        long session = new SecureRandom().nextLong();
        Invitation invitation = new Invitation(
                new LanBeacon(
                        net.lan(),
                        clock,
                        log,
                        LanAddresses.beaconTargets(net.lan().port())),
                session,
                port,
                scenario.name(),
                machineName(options),
                LanAddresses.reachable());
        Log.info("hosting " + scenario.name() + " on port " + port + " as room " + invitation.code() + ", build "
                + BuildId.current());
        return new OnlineSession(peer, thread, null, List.of(), log, clock, "host, port " + port, invitation);
    }

    /** Joins the session at {@code host:port}, over the configured simulated lag. */
    public static OnlineSession join(
            NetConfig net, LaunchOptions options, List<Integer> lagSteps, String host, int port) {
        Clock clock = SystemClock.INSTANCE;
        NetLog log = NetLog.open(net.log(), options.logName() + "-join");
        ConditionedTransport lag =
                new ConditionedTransport(new NettyTransport(net, clock, log, "peer"), net.conditions(), clock);
        PeerSession peer = new PeerSession(lag, net, log, new ScenarioWorlds(), dumps(net), BuildId.current());
        peer.connect(host, port, 0L);
        Log.info("joining " + host + ":" + port + ", build " + BuildId.current());
        return new OnlineSession(peer, null, lag, lagSteps, log, clock, host + ":" + port, null);
    }

    /** This machine as the join list shows it: its name on Windows, then which copy it is. */
    private static String machineName(LaunchOptions options) {
        String name = System.getenv("COMPUTERNAME");
        return (name == null || name.isBlank() ? "host" : name)
                + (options.instanceGiven() ? " #" + options.instance() : "");
    }

    /** Where a hosting copy's own peer connects: its sequencer, on this machine. */
    static final String LOCAL = "127.0.0.1";

    private static Path dumps(NetConfig net) {
        return Path.of(net.log().dir());
    }

    @Override
    public void seat(int seat) {
        peer.seat(seat);
    }

    @Override
    public void apply(HarnessCommand command, PlayerInputs local) {
        switch (command) {
            case PAUSE -> peer.pause(!peer.paused());
            case STEP -> {
                if (!toldStepIsOffline) {
                    Log.info("single-step is offline only: every machine would have to step with you");
                    toldStepIsOffline = true;
                }
            }
            case RESTART -> peer.restart();
            case TOGGLE_OVERLAY -> overlayMode = overlayMode.next();
            case TOGGLE_GOD_MODE -> {
                Simulation world = peer.simulation();
                if (world != null) {
                    peer.godMode(!world.commanderInvulnerable());
                }
            }
            case TOGGLE_COSMETIC_LEAD -> cosmeticLead = !cosmeticLead;
            case NEXT_LAG -> nextLag();
            case LEAVE -> leaving = true;
        }
    }

    @Override
    public void advance(float deltaSeconds, PlayerInputs local) {
        for (int seat = 0; seat < PlayerSeats.SEATS && seat < local.capacity(); seat++) {
            Order given = local.orderOf(seat);
            if (given != null && peer.slotOf(seat) >= 0) {
                lastOrder = given;
            }
        }
        peer.pump(clock.nanos(), local);
        Invitation hosting = invitation;
        Simulation world = peer.simulation();
        if (hosting != null) {
            hosting.pump(
                    world == null
                            ? 0
                            : world.engine().getEntitiesFor(COMMANDERS).size());
        }
    }

    /** What a hosting copy tells its players so their friends can join: its room code and addresses. */
    @Override
    public @Nullable String invitation() {
        Invitation hosting = invitation;
        return hosting == null ? null : hosting.line();
    }

    @Override
    public @Nullable Simulation simulation() {
        return peer.simulation();
    }

    @Override
    public @Nullable String scenarioName() {
        SessionSetup setup = peer.setup();
        return setup == null ? null : setup.scenario();
    }

    @Override
    public float renderAlpha() {
        return peer.paused() ? 1f : peer.alpha();
    }

    @Override
    public boolean paused() {
        return peer.paused();
    }

    @Override
    public void setPaused(boolean on) {
        peer.pause(on);
    }

    @Override
    public boolean canRestart() {
        return invitation != null;
    }

    @Override
    public boolean online() {
        return true;
    }

    @Override
    public OverlayMode overlayMode() {
        return overlayMode;
    }

    @Override
    public boolean cosmeticLead() {
        return cosmeticLead;
    }

    @Override
    public boolean leaving() {
        return leaving;
    }

    @Override
    public @Nullable Order lastOrder() {
        return lastOrder;
    }

    /**
     * Why this machine is out of the session, once it is: refused at the door, or the link closed - the
     * host leaving closes it for everyone. Reconnecting is for later, with the relay; for now the game goes
     * back to the menu and says why (G0038).
     */
    @Override
    public @Nullable String ended() {
        return switch (peer.state()) {
            case REFUSED -> "refused by " + where + ": " + peer.refusal();
            case CLOSED -> "the game at " + where + " is over: " + reason(peer.closedBecause());
            case CONNECTING, JOINING, PLAYING -> null;
        };
    }

    /** A link's close reason as a player reads it: the transport's "remote:" says only who said it. */
    private static String reason(@Nullable String closed) {
        if (closed == null) {
            return "the link closed";
        }
        return closed.startsWith("remote: ") ? closed.substring("remote: ".length()) : closed;
    }

    @Override
    public int slotOf(int seat) {
        return peer.slotOf(seat);
    }

    @Override
    public String connection() {
        return switch (peer.state()) {
            case CONNECTING -> "connecting to " + where + " ...";
            case JOINING -> "joined " + where + ", waiting for the world ...";
            case REFUSED -> "refused by " + where + ": " + peer.refusal();
            case CLOSED ->
                "disconnected from " + where + (peer.closedBecause() == null ? "" : ": " + peer.closedBecause());
            case PLAYING -> playing();
        };
    }

    private String playing() {
        long delayMillis = peer.lead() * peer.tickNanos() / 1_000_000L;
        StringBuilder line = new StringBuilder("online ")
                .append(where)
                .append("   peer ")
                .append(peer.peerId())
                .append("   input delay ")
                .append(peer.lead())
                .append(" ticks, ")
                .append(delayMillis)
                .append(" ms");
        ConditionedTransport lagged = lag;
        if (lagged != null && lagged.conditions().any()) {
            Conditions now = lagged.conditions();
            line.append("   simulated lag ").append(now.latencyMillis()).append(" ms each way");
            if (now.jitterMillis() > 0) {
                line.append(" +").append(now.jitterMillis());
            }
        }
        if (!cosmeticLead) {
            line.append("   cosmetic lead off");
        }
        if (peer.starvedFrames() >= WAITING_FRAMES) {
            line.append("   WAITING FOR THE HOST");
        }
        return line.toString();
    }

    private void nextLag() {
        ConditionedTransport lagged = lag;
        if (lagged == null || lagSteps.isEmpty()) {
            Log.info("simulated lag is a joining copy's to step");
            return;
        }
        lagStep = (lagStep + 1) % lagSteps.size();
        Conditions was = lagged.conditions();
        lagged.conditions(
                new Conditions(lagSteps.get(lagStep), was.jitterMillis(), was.loss(), was.duplicate(), was.seed()));
        log.info("peer", "event=lag latencyMillis=" + lagSteps.get(lagStep));
        Log.info("simulated lag: " + lagSteps.get(lagStep) + " ms each way");
    }

    @Override
    public void close() {
        peer.close();
        Invitation hosting = invitation;
        if (hosting != null) {
            hosting.close();
        }
        SequencerThread running = sequencer;
        if (running != null) {
            running.close();
        }
        log.close();
    }
}
