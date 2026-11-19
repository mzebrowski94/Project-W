package pl.mzebrows.pwt.client.play;

import com.badlogic.ashley.core.Family;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.LaunchOptions;
import pl.mzebrows.pwt.client.Log;
import pl.mzebrows.pwt.client.input.PlayerSeats;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.NetConfig.Conditions;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.door.DoorClient;
import pl.mzebrows.pwt.net.lan.LanAddresses;
import pl.mzebrows.pwt.net.lan.LanBeacon;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.netty.NettyTransport;
import pl.mzebrows.pwt.net.session.BriefingPlayer;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.net.session.PeerSession;
import pl.mzebrows.pwt.net.session.RoomWire;
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
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.page.Briefing;

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
    private String where;
    private final @Nullable Invitation invitation;

    /** This copy reaches its session through a room on the relay (G0103), and the password the room has - or was joined with. */
    private final boolean relayed;

    private final String password;

    /** The relay has named the room, and {@link #where} says so. */
    private boolean roomNamed;

    /** This copy opened the session on a briefing, and its host takes the machines back to it after a mission. */
    private final boolean briefed;

    private OverlayMode overlayMode = OverlayMode.OFF;
    private boolean cosmeticLead = true;
    private boolean leaving;
    private boolean toldStepIsOffline;

    /**
     * Cards taken and crate choices on this machine, by seat, offered to the session every frame until it
     * takes them - which it does only once it plays.
     */
    private final HeldEvents held = new HeldEvents();

    /**
     * Where {@link #held} offers a seat's events: at the seat itself, which is how the peer reads input - once
     * the sequencer has given that seat a slot. Before then the peer would drop them, so they stay held.
     */
    private final IntUnaryOperator seated = this::seatOnceSeated;

    private int lagStep = -1;
    private @Nullable Order lastOrder;

    /**
     * @param sequencer the sequencer thread this copy runs, if it hosts
     * @param lag what the peer's link is wrapped in, if its lag can be stepped
     * @param where how the overlay names the session: the port hosted on, or the address joined
     * @param invitation how a hosting copy tells the network about itself and its players how to join
     * @param briefed the session opened on a briefing, which a mission goes back to (G0101)
     */
    OnlineSession(
            PeerSession peer,
            @Nullable SequencerThread sequencer,
            @Nullable ConditionedTransport lag,
            List<Integer> lagSteps,
            NetLog log,
            Clock clock,
            String where,
            @Nullable Invitation invitation,
            boolean briefed) {
        this(peer, sequencer, lag, lagSteps, log, clock, where, invitation, briefed, false, "");
    }

    /**
     * @param relayed the session is a room on the relay: its code is the relay's, and there is no sequencer here
     * @param password the room's password - the host's, or the one this machine joined with; empty for none
     */
    OnlineSession(
            PeerSession peer,
            @Nullable SequencerThread sequencer,
            @Nullable ConditionedTransport lag,
            List<Integer> lagSteps,
            NetLog log,
            Clock clock,
            String where,
            @Nullable Invitation invitation,
            boolean briefed,
            boolean relayed,
            String password) {
        this.relayed = relayed;
        this.password = password;
        this.peer = peer;
        this.sequencer = sequencer;
        this.lag = lag;
        this.lagSteps = List.copyOf(lagSteps);
        this.log = log;
        this.clock = clock;
        this.where = where;
        this.invitation = invitation;
        this.briefed = briefed;
        // The keyboard is always somebody; pads are seated as they are picked up.
        peer.seat(PlayerSeats.KEYBOARD_SLOT);
    }

    /**
     * Opens a session on its briefing (G0101): starts a sequencer on the configured port (the next free one if a copy
     * on this PC already has it), joins it, and announces it to the local network under a room code. There is no world
     * and no mission yet; the host starts one with {@link #startMission}, and friends who arrive meanwhile land on the
     * briefing.
     *
     * @param machine this computer's {@code MachineId}, as the application keeps it
     * @throws RuntimeException if no port could be had; nothing is left open
     */
    public static OnlineSession open(NetConfig net, LaunchOptions options, String machine) {
        OnlineSession session = create(net, options, machine, true);
        Log.info("opened a session " + session.where + ", room " + session.roomCode() + ", build " + BuildId.current()
                + ", machine " + machine);
        return session;
    }

    /**
     * Hosts {@code scenario} straight into play, with no briefing - the developer's scenario list. What is shipped
     * to peers is the world config {@code config} was bound from and its seed; the sequencer paces by the same
     * config's tick.
     *
     * @param worldConfig the text {@code config} was bound from, comments left out ({@link
     *     SimConfigLoader#loaded()}) - never a later read of the files
     * @param machine this computer's {@code MachineId}, as the application keeps it
     * @throws RuntimeException if no port could be had; nothing is left open
     */
    public static OnlineSession host(
            Scenario scenario,
            SimConfig config,
            String worldConfig,
            NetConfig net,
            LaunchOptions options,
            String machine) {
        OnlineSession session = create(net, options, machine, false);
        session.startMission(new SessionSetup(worldConfig, scenario.name(), config.seed()), config.tickSeconds());
        Log.info("hosting " + scenario.name() + " " + session.where + ", room " + session.roomCode() + ", build "
                + BuildId.current() + ", machine " + machine);
        return session;
    }

    private static OnlineSession create(NetConfig net, LaunchOptions options, String machine, boolean briefed) {
        Clock clock = SystemClock.INSTANCE;
        NetLog log = NetLog.open(net.log(), machine + "-" + options.logName() + "-host");
        long key = new SecureRandom().nextLong() | 1L;
        Sequencer sequencer = new Sequencer(
                new NettyTransport(net, clock, log, "sequencer"), net, log, Players.MAX, key, BuildId.current());
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
                BuildId.current(),
                machine);
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
                machineName(options),
                LanAddresses.reachable());
        return new OnlineSession(peer, thread, null, List.of(), log, clock, "host, port " + port, invitation, briefed);
    }

    /**
     * Joins the session at {@code host:port}, over the configured simulated lag.
     *
     * @param machine this computer's {@code MachineId}, as the application keeps it
     */
    public static OnlineSession join(
            NetConfig net, LaunchOptions options, List<Integer> lagSteps, String host, int port, String machine) {
        Clock clock = SystemClock.INSTANCE;
        NetLog log = NetLog.open(net.log(), machine + "-" + options.logName() + "-join");
        ConditionedTransport lag =
                new ConditionedTransport(new NettyTransport(net, clock, log, "peer"), net.conditions(), clock);
        PeerSession peer = new PeerSession(lag, net, log, new ScenarioWorlds(), dumps(net), BuildId.current(), machine);
        peer.connect(host, port, 0L);
        Log.info("joining " + host + ":" + port + ", build " + BuildId.current() + ", machine " + machine);
        return new OnlineSession(peer, null, lag, lagSteps, log, clock, host + ":" + port, null, false);
    }

    /**
     * Opens a session on its briefing as a room on the relay (G0103): connects to the relay's door as the host and asks
     * for a room with {@code password}. The relay answers a few frames later - {@link #relayAnswered}, {@link
     * #relayRefusal}, {@link #relayUnreachable} - and nothing is announced or hosted on this machine until it has. There
     * is no sequencer here: the room's is on the relay, and this machine's peer is one of its links, the host's.
     *
     * @param version this copy's build version, which the relay names in its refusals
     * @param password four characters of the room code's alphabet, or empty for none
     * @param machine this computer's {@code MachineId}, as the application keeps it
     * @throws RuntimeException if the relay's address could not be used; nothing is left open
     */
    public static OnlineSession openThroughRelay(
            NetConfig net, LaunchOptions options, String machine, String version, String password) {
        Clock clock = SystemClock.INSTANCE;
        NetLog log = NetLog.open(net.log(), machine + "-" + options.logName() + "-host");
        long key = new SecureRandom().nextLong() | 1L;
        PeerSession peer = new PeerSession(
                new NettyTransport(net, clock, log, "peer"),
                net,
                log,
                new ScenarioWorlds(),
                dumps(net),
                BuildId.current(),
                machine);
        try {
            peer.connect(
                    net.relay().host(),
                    net.relay().port(),
                    key,
                    DoorClient.open(BuildId.current(), version, key, RoomWire.sessionConfig(net.session()), password));
        } catch (RuntimeException e) {
            peer.close();
            log.close();
            throw e;
        }
        Invitation invitation = Invitation.roomed(
                new LanBeacon(
                        net.lan(),
                        clock,
                        log,
                        LanAddresses.beaconTargets(net.lan().port())),
                new SecureRandom().nextLong(),
                net.relay().port(),
                machineName(options));
        Log.info("opening a room at the relay " + net.relay().host() + ":"
                + net.relay().port() + ", build " + BuildId.current() + ", machine " + machine);
        return new OnlineSession(
                peer, null, null, List.of(), log, clock, "the relay", invitation, true, true, password);
    }

    /**
     * Joins the room of this code through the relay (G0103), over the configured simulated lag. The relay's answer comes
     * a few frames later, as {@link #openThroughRelay}'s does.
     *
     * @param password what the door is told: from the announcement, or typed on the page; empty for none
     */
    public static OnlineSession joinThroughRelay(
            NetConfig net,
            LaunchOptions options,
            List<Integer> lagSteps,
            String room,
            String password,
            String machine,
            String version) {
        Clock clock = SystemClock.INSTANCE;
        NetLog log = NetLog.open(net.log(), machine + "-" + options.logName() + "-join");
        ConditionedTransport lag =
                new ConditionedTransport(new NettyTransport(net, clock, log, "peer"), net.conditions(), clock);
        PeerSession peer = new PeerSession(lag, net, log, new ScenarioWorlds(), dumps(net), BuildId.current(), machine);
        try {
            peer.connect(
                    net.relay().host(),
                    net.relay().port(),
                    0L,
                    DoorClient.join(room, password, BuildId.current(), version));
        } catch (RuntimeException e) {
            peer.close();
            log.close();
            throw e;
        }
        Log.info("joining room " + room + " through the relay " + net.relay().host() + ":"
                + net.relay().port() + ", build " + BuildId.current() + ", machine " + machine);
        return new OnlineSession(peer, null, lag, lagSteps, log, clock, "room " + room, null, false, true, password);
    }

    /** This machine as the join list shows it: its name on Windows, then which copy it is. */
    private static String machineName(LaunchOptions options) {
        String name = System.getenv("COMPUTERNAME");
        return (name == null || name.isBlank() ? "host" : name)
                + (options.instanceGiven() ? " #" + options.instance() : "");
    }

    /** What a machine that could not reach the relay is told. */
    public static final String UNREACHABLE = "THE RELAY CANNOT BE REACHED";

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
            case TAKE_FIRST_CARD -> takeCard(0);
            case TAKE_SECOND_CARD -> takeCard(1);
            case TAKE_THIRD_CARD -> takeCard(2);
        }
    }

    /**
     * Held until the next frame, which hands it to the session as an input event: numbered, sent until the
     * sequencer seals it, and applied on the same tick on every machine (ADR 0012). Kept while paused,
     * as a solo draft is taken paused (G0049); sealed once the game runs again.
     */
    @Override
    public void pick(int seat, int serial, UpgradeKind kind) {
        held.pick(seat, serial, kind);
    }

    /** Held until the next frame, which hands it to the session as an input event, as a pick is (G0056). */
    @Override
    public void crate(int seat, @Nullable PickupKind take) {
        held.crate(seat, take);
    }

    @Override
    public void advance(float deltaSeconds, PlayerInputs local) {
        for (int seat = 0; seat < PlayerSeats.SEATS && seat < local.capacity(); seat++) {
            Order given = local.orderOf(seat);
            if (given != null && peer.slotOf(seat) >= 0) {
                lastOrder = given;
            }
        }
        // Taken only once the peer plays and the seat has a slot: until then they stay held.
        held.offer(local, seated);
        if (peer.pump(clock, local)) {
            held.taken();
        }
        Invitation hosting = invitation;
        Simulation world = peer.simulation();
        String room = peer.room();
        if (relayed && room != null && !roomNamed) {
            roomNamed = true;
            where = "room " + room;
            if (hosting != null) {
                hosting.opened(new RoomCode(room), password);
            }
        }
        if (hosting != null) {
            String playing = scenarioName();
            hosting.pump(
                    world == null
                            ? peer.players().size()
                            : world.engine().getEntitiesFor(COMMANDERS).size(),
                    playing == null ? Invitation.BRIEFING : playing);
        }
    }

    /** The seat itself once the sequencer has given it a slot; -1 until then. */
    private int seatOnceSeated(int seat) {
        return peer.slotOf(seat) >= 0 ? seat : -1;
    }

    /** What a hosting copy tells its players so their friends can join: its room code and addresses. */
    @Override
    public @Nullable String invitation() {
        if (relayed) {
            String room = peer.room();
            return room == null ? null : Invitation.roomLine(room, password);
        }
        Invitation hosting = invitation;
        return hosting == null ? null : hosting.line();
    }

    /** Whether the session is on its briefing: opened, joined, and no mission under way (G0101). */
    public boolean onBriefing() {
        return peer.state() == PeerSession.State.BRIEFING;
    }

    /** Whether this copy hosts the session: the briefing's entries are its, and its Back ends the session. */
    public boolean hosts() {
        return invitation != null || briefed;
    }

    @Override
    public boolean briefed() {
        return briefed;
    }

    /** The players on the briefing, the host's machine first then by slot, as the page shows them. */
    public List<Briefing.Player> players() {
        List<BriefingPlayer> heard = peer.players();
        List<Briefing.Player> players = new ArrayList<>(heard.size());
        for (BriefingPlayer each : heard) {
            players.add(new Briefing.Player(each.slot(), each.machine(), each.host(), each.online()));
        }
        return players;
    }

    /** Whether the sequencer has welcomed this machine: until then the host's words to the session are not heard. */
    public boolean welcomed() {
        return peer.peerId() >= 0;
    }

    /** The room code the host announces under, as it is read out; empty on a machine that joined. */
    public String roomCode() {
        if (relayed) {
            String room = peer.room();
            return room == null ? "" : room;
        }
        Invitation hosting = invitation;
        return hosting == null ? "" : hosting.code().toString();
    }

    /** The room's password as this machine knows it - the host's, or the one it joined with - or empty: none, or no room. */
    public String roomPassword() {
        return relayed ? password : "";
    }

    /** Whether this session is a room on the relay. */
    public boolean relayed() {
        return relayed;
    }

    /** Whether the relay's door has let this machine in: the room has its code. */
    public boolean relayAnswered() {
        return relayed && peer.room() != null;
    }

    /** The relay's door's refusal, whole - {@code refused by the relay: room ACDE has a password} - or null. */
    public @Nullable String relayRefusal() {
        String refusal = peer.refusal();
        return relayed
                        && peer.state() == PeerSession.State.REFUSED
                        && refusal != null
                        && refusal.startsWith(Door.REFUSAL_PREFIX)
                ? refusal
                : null;
    }

    /** Whether the link to the relay closed before its door answered: it could not be reached, or would not answer. */
    public boolean relayUnreachable() {
        return relayed && peer.room() == null && peer.state() == PeerSession.State.CLOSED;
    }

    /** Starts the mission for every machine on the briefing, the slots taken so far on the field at the first tick. */
    public void startMission(SessionSetup mission, float tickSeconds) {
        peer.start(mission, tickSeconds);
    }

    /** Puts the host's code line on every machine's briefing: the host's to say, ignored from anyone else. */
    public void note(String line) {
        peer.note(line);
    }

    /** The code line the host last put on the briefing. */
    public String note() {
        return peer.note();
    }

    @Override
    public void toBriefing() {
        peer.toBriefing();
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
    public void setDraftPaused(boolean on) {
        peer.pauseForDraft(on);
    }

    @Override
    public boolean pausedForDraft() {
        return peer.paused() && peer.pausedForDraft();
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
        if (relayUnreachable()) {
            return UNREACHABLE;
        }
        String doorsRefusal = relayRefusal();
        if (doorsRefusal != null) {
            return doorsRefusal;
        }
        return switch (peer.state()) {
            case REFUSED -> "refused by " + where + ": " + peer.refusal();
            case CLOSED -> "the game at " + where + " is over: " + reason(peer.closedBecause());
            case CONNECTING, JOINING, BRIEFING, PLAYING -> null;
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
            case BRIEFING -> "joined " + where + ", on the briefing";
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
        lagged.conditions(was.withLatency(lagSteps.get(lagStep)));
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
