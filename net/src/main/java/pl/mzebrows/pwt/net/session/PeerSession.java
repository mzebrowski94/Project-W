package pl.mzebrows.pwt.net.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * One machine's place in a session (ADR 0012): it sends what its players hold and order, steps its
 * world with each bundle the sequencer seals, and nothing else ever changes that world. The host's own
 * game plays through one of these too, over the same wire, so the host has no path of its own to get
 * wrong.
 *
 * <p><strong>Input is sent a lead of ticks ahead.</strong> When the world steps tick T, what the
 * players are holding is tagged for tick T + lead, so it reaches the sequencer before that tick is
 * sealed. The sequencer reports how early each input arrived, and the lead follows: up at once when an
 * input is late, down one tick at a time after a second of arriving comfortably early. The lead is the
 * input delay this machine's players feel - two ticks on the host, about a round trip more elsewhere.
 *
 * <p><strong>A world steps only on sealed ticks, and never skips one.</strong> Ticks are played at the
 * world's own rate as they arrive; a machine holding more than {@code bufferTicks} unplayed plays one
 * extra a frame until it is back, never more than {@code catchUpTicks} in a frame - or one more than a
 * frame is worth, on a machine whose frames are longer than that. One that has held some
 * back for a whole second without once running dry plays one early, so a steady link drains to no delay.
 * With none to play it waits, and banks no time while it waits - so a stall ends in a short catch-up, not
 * a burst.
 *
 * <p>Every {@code checksumTicks} - the host's, from the welcome, never this machine's file (ADR 0018) - it snapshots its world, keeps the snapshot as a keyframe and sends the
 * sequencer its hash. Told it has desynced, it writes a dump - its keyframes and the bundles between
 * them - and is healed with a snapshot of the host's world.
 *
 * <p>Driven by {@link #pump(long, PlayerInputs)} once a frame from the game's thread, with the local
 * players' input indexed by seat. Single-threaded.
 */
public final class PeerSession implements AutoCloseable {

    /** Where a session stands, as the game shows it. */
    public enum State {
        /** Not connected: before {@link #connect}, or after the link closed. */
        CLOSED,
        CONNECTING,
        /** Welcomed, and waiting for the snapshot to drop in with. */
        JOINING,
        PLAYING,
        /** The sequencer said no; {@link #refusal()} says why. */
        REFUSED
    }

    /** How many of this machine's own ticks of input are kept to send: more than any lead. A power of two. */
    private static final int OWN = Integer.highestOneBit(NetConfig.Pacing.MOST_LEAD_TICKS) * 2;

    /** How many keyframes are kept: the one a desync is reported on, and the agreed ones before it. */
    private static final int KEYFRAMES = 3;

    /**
     * How many frames in a row a peer must hold at least one unplayed tick before it plays one of them
     * early - about a second. A link that never runs dry drains to no delay at all; one that does keeps
     * the cushion it keeps needing.
     */
    private static final int DRAIN_FRAMES = 60;

    /** How many reports in a row must come early before the lead comes down: a second's worth. */
    private static final int CALM_REPORTS = 60;

    /** How long this machine's frames are watched for before {@link #frameTicks} is judged again. */
    private static final long FRAME_WINDOW_NANOS = 1_000_000_000L;

    /**
     * While paused, how many ticks apart this machine sends input with nothing new in it: enough to keep
     * acknowledging, not sixty messages a second of nothing.
     */
    private static final int PAUSED_INPUT_TICKS = 15;

    /** The most unacknowledged input events - orders and picks - sent in one message. */
    private static final int EVENTS_PER_MESSAGE = 16;

    private final Transport transport;
    private final NetConfig.Pacing pacing;
    private final int maxDatagramBytes;
    private final int maxMessageBytes;
    private final NetLog log;
    private final WorldFactory worlds;
    private final @Nullable Path dumpDir;
    private final String build;
    private final String machineId;
    private final long statsNanos;

    private final WireWriter out;
    private final WireReader in = new WireReader();
    private final Listener listener = new Listener();

    private State state = State.CLOSED;
    private @Nullable String refusal;
    private @Nullable String closedBecause;
    private int link = -1;
    private long hostKey;
    private int peerId = -1;
    private int capacity;
    private long tickNanos;
    private @Nullable SessionSetup setup;
    private @Nullable Simulation world;
    private Bundle bundle = new Bundle(0);
    private PlayerInputs inputs = new PlayerInputs(1);
    private int generation;
    private boolean paused;

    /** Whether the pause in force was asked for by a solo player's draft (G0049). */
    private boolean pausedForDraft;

    /** The last tick this world stepped. */
    private int tick = -1;

    /** The last tick for which every sealed bundle up to it has arrived. */
    private int contiguous = -1;

    private int ringMask;
    private byte[][] ring = new byte[0][];
    private int[] ringLength = new int[0];
    private int[] ringTick = new int[0];

    private int lead;
    private int calm;

    /** The least early any input was over the current run of early reports. */
    private int calmLeast = Integer.MAX_VALUE;

    private int settle;

    private long accumulator;
    private long lastPumpNanos;
    private boolean pumped;
    private long lastInputNanos;
    private long nextStatsNanos;
    private int stalls;
    private int starved;
    private int cushioned;

    /**
     * How many ticks one of this machine's frames is worth while they run steadily: the second longest of
     * the last second's, so that one hitch does not count and a frame rate below the tick rate does. Its
     * input goes once a frame, so this many ticks are sealed between two of its inputs, and this many
     * fall due at the start of every frame.
     */
    private int frameTicks = 1;

    private long frameLongest;
    private long frameSecondLongest;
    private long frameWindowNanos;

    private final int[] slotOfSeat = new int[Players.MAX];
    private final boolean[] seatWanted = new boolean[Players.MAX];
    private final CommanderInput[][] own = new CommanderInput[Players.MAX][OWN];
    private final int[][] ownTick = new int[Players.MAX][OWN];
    private int newestTag = -1;

    private int nextEventSeq = 1;
    private int eventsAcked;
    private final int[] eventSeq = new int[64];
    private final int[] eventSlot = new int[64];
    private final int[] eventType = new int[64];
    private final int[] eventValue = new int[64];
    private final int[] eventExtra = new int[64];
    private int eventCount;

    private final int[] keyTick = new int[KEYFRAMES];
    private final byte[][] keyBytes = new byte[KEYFRAMES][];
    private final long[] keyHash = new long[KEYFRAMES];
    private int keyNext;

    /** How often worlds are compared, as the host's session config says; known from the welcome on. */
    private int checksumTicks;

    /**
     * @param dumpDir where desync dumps are written, or null to write none
     * @param build this build's id, sent in the hello
     * @param machineId this computer's {@link MachineId}, sent in the hello
     */
    public PeerSession(
            Transport transport,
            NetConfig config,
            NetLog log,
            WorldFactory worlds,
            @Nullable Path dumpDir,
            String build,
            String machineId) {
        this(transport, config, log, worlds, dumpDir, build, machineId, Transport.MAX_MESSAGE_BYTES);
    }

    /** @param maxMessageBytes the largest snapshot this peer sends - lower only in a test of a world too big */
    PeerSession(
            Transport transport,
            NetConfig config,
            NetLog log,
            WorldFactory worlds,
            @Nullable Path dumpDir,
            String build,
            String machineId,
            int maxMessageBytes) {
        if (!MachineId.valid(machineId)) {
            throw new IllegalArgumentException("not a machine id: \"" + machineId + "\"");
        }
        this.transport = transport;
        this.pacing = config.pacing();
        this.maxDatagramBytes = config.maxDatagramBytes();
        this.maxMessageBytes = maxMessageBytes;
        this.log = log;
        this.worlds = worlds;
        this.dumpDir = dumpDir;
        this.build = build;
        this.machineId = machineId;
        this.statsNanos = config.log().statsSeconds() * 1_000_000_000L;
        this.out = new WireWriter(Math.max(2048, config.maxDatagramBytes()));
        this.lead = pacing.leadMinTicks();
        Arrays.fill(slotOfSeat, -1);
        for (int[] ticks : ownTick) {
            Arrays.fill(ticks, -1);
        }
        Arrays.fill(keyTick, -1);
    }

    /** Opens a link to a sequencer. {@code hostKey} is the sequencer's own for the host's peer, 0 for anyone else. */
    public void connect(String host, int port, long key) {
        hostKey = key;
        state = State.CONNECTING;
        link = transport.connect(host, port);
        log.info("peer", "event=connecting to=" + host + ":" + port);
    }

    /**
     * One frame: hears the sequencer, queues any order a local player gave, plays whatever ticks are due,
     * and sends what the local players are holding.
     *
     * @param local this frame's input, indexed by local seat
     */
    public void pump(long nowNanos, PlayerInputs local) {
        long elapsed = pumped ? nowNanos - lastPumpNanos : 0L;
        lastPumpNanos = nowNanos;
        pumped = true;
        if (statsNanos > 0 && elapsed >= statsNanos) {
            // This machine's game stopped running - its window held by its title bar, a console clicked into
            // - while its link went on answering: a hole in this log, and to every other machine the same as
            // a slow network, unless it is said here (the 2026-10-01 playtest).
            log.warn("peer", "event=frame-stall ms=" + elapsed / 1_000_000L + " tick=" + tick);
        }
        timeFrame(elapsed);
        transport.poll(listener);
        Simulation current = world;
        if (state != State.PLAYING || current == null) {
            return;
        }
        queueEvents(local);
        accumulator += elapsed;
        int available = contiguous - tick;
        // A frame plays at least one tick more than its steady length is worth, or a machine drawing fewer
        // frames than catchUpTicks a tick could never catch up, nor even keep up.
        int most = Math.max(pacing.catchUpTicks(), frameTicks + 1);
        // On time: the ticks this frame's time is worth, as far as there are any. Only these spend time.
        int onTime = (int) Math.min(Math.min(accumulator / tickNanos, available), most);
        // Catching up is free: it makes up for time already spent waiting, so it is not taken from the bank.
        int extra = 0;
        if (available - onTime > pacing.bufferTicks()) {
            extra = 1;
        } else if (available - onTime == 0) {
            cushioned = 0;
        } else if (++cushioned >= DRAIN_FRAMES) {
            // A second without once running dry: the ticks held back are not absorbing anything, only
            // adding delay. Play one of them.
            extra = 1;
            cushioned = 0;
        }
        int steps = Math.min(onTime + extra, most);
        for (int i = 0; i < steps; i++) {
            step(local);
        }
        accumulator -= onTime * tickNanos;
        if (tick == contiguous && accumulator > tickNanos) {
            // Nothing left to play: waiting, not falling behind. Banking the wait would only replay it as
            // a burst when the next bundle comes.
            accumulator = tickNanos;
            if (!paused) {
                stalls++;
            }
            starved++;
        } else if (steps > 0) {
            starved = 0;
        }
        sendInput(nowNanos, steps > 0);
        stats(nowNanos);
    }

    /** Keeps the longest two frames of the current second, and judges {@link #frameTicks} once it is up. */
    private void timeFrame(long elapsed) {
        if (tickNanos <= 0) {
            return;
        }
        if (elapsed > frameLongest) {
            frameSecondLongest = frameLongest;
            frameLongest = elapsed;
        } else if (elapsed > frameSecondLongest) {
            frameSecondLongest = elapsed;
        }
        frameWindowNanos += elapsed;
        if (frameWindowNanos >= FRAME_WINDOW_NANOS) {
            frameTicks = (int) Math.max(1L, (frameSecondLongest + tickNanos / 2) / tickNanos);
            frameLongest = 0L;
            frameSecondLongest = 0L;
            frameWindowNanos = 0L;
        }
    }

    /** Asks for a slot for a local seat - a player who has just picked up a pad. Idempotent. */
    public void seat(int seat) {
        seatWanted[seat] = true;
        if (peerId >= 0) {
            sendSeat(seat);
        }
    }

    /** Pauses or resumes the whole session. Anybody may (G0038). */
    public void pause(boolean on) {
        sendControl(out.reset().putByte(Msg.PAUSE).putByte(on ? 1 : 0).putByte(Msg.PAUSE_BY_PLAYER));
    }

    /**
     * Pauses or resumes the session for a solo player's draft (G0049): a pause like any other, carrying its
     * reason, so every machine can leave the view clear of the paused banner.
     */
    public void pauseForDraft(boolean on) {
        sendControl(out.reset().putByte(Msg.PAUSE).putByte(on ? 1 : 0).putByte(Msg.PAUSE_FOR_DRAFT));
    }

    /** Restarts the scenario on every machine. The host's to ask for; the sequencer ignores anyone else. */
    public void restart() {
        sendControl(out.reset().putByte(Msg.RESTART));
    }

    /** Switches every commander's invulnerability. The host's to ask for, as {@link #restart()} is. */
    public void godMode(boolean on) {
        sendControl(out.reset().putByte(Msg.GOD_MODE).putByte(on ? 1 : 0));
    }

    public State state() {
        return state;
    }

    /** Why the sequencer refused this peer, once it has. */
    public @Nullable String refusal() {
        return refusal;
    }

    /** Why the link closed, once it has. */
    public @Nullable String closedBecause() {
        return closedBecause;
    }

    /** What the host said this session plays - its config, scenario and seed - or null before the welcome. */
    public @Nullable SessionSetup setup() {
        return setup;
    }

    /** The world as of the last tick played, or null before there is one. Replaced on a restart or a heal. */
    public @Nullable Simulation simulation() {
        return world;
    }

    /** The last tick this world played. */
    public int tick() {
        return tick;
    }

    /** The last tick for which every sealed bundle has arrived: how far this machine could play. */
    int contiguous() {
        return contiguous;
    }

    /** The slot a local seat plays in, or -1 if it has none (yet). */
    public int slotOf(int seat) {
        return slotOfSeat[seat];
    }

    public boolean paused() {
        return paused;
    }

    /** Whether the session is paused for a solo player's draft rather than by somebody's hand (G0049). */
    public boolean pausedForDraft() {
        return pausedForDraft;
    }

    /**
     * How far this frame is towards the next tick, for drawing between two: what the frame's time is
     * worth past the last tick played. One while there is nothing to play, so a world that is waiting is
     * drawn where it stands rather than part-way to a tick that has not come.
     */
    public float alpha() {
        return tickNanos <= 0 ? 1f : Math.min(1f, (float) accumulator / tickNanos);
    }

    /** How many frames in a row this machine has had time for a tick and no tick to play. */
    public int starvedFrames() {
        return starved;
    }

    /** How long a tick is, in nanoseconds; zero before the welcome. */
    public long tickNanos() {
        return tickNanos;
    }

    /** The input delay this machine currently runs with, in ticks. */
    public int lead() {
        return lead;
    }

    /** The id the sequencer gave this peer, or -1 before it has. */
    public int peerId() {
        return peerId;
    }

    @Override
    public void close() {
        transport.close();
        state = State.CLOSED;
    }

    private void step(PlayerInputs local) {
        Simulation current = world;
        SessionSetup built = setup;
        if (current == null || built == null) {
            return;
        }
        int next = tick + 1;
        int at = next & ringMask;
        in.wrap(ring[at], 0, ringLength[at]);
        bundle.read(in);
        if (bundle.restart()) {
            current = worlds.fresh(built);
            world = current;
            log.info("peer", "event=restart tick=" + next);
        }
        bundle.applyTo(inputs);
        current.step(inputs);
        tick = next;
        produce(local);
        if (tick % checksumTicks == 0) {
            checksum(current);
        }
    }

    /** Tags what every seated local player is holding for tick + lead. */
    private void produce(PlayerInputs local) {
        int tag = tick + lead;
        int from = newestTag < 0 ? tag : Math.max(newestTag + 1, tag - OWN / 2);
        if (tag <= newestTag) {
            from = tag;
        }
        for (int seat = 0; seat < slotOfSeat.length; seat++) {
            if (slotOfSeat[seat] < 0 || seat >= local.capacity()) {
                continue;
            }
            CommanderInput held = local.get(seat);
            for (int t = from; t <= tag; t++) {
                own[seat][t & (OWN - 1)] = held;
                ownTick[seat][t & (OWN - 1)] = t;
            }
        }
        newestTag = Math.max(newestTag, tag);
    }

    /**
     * Numbers every order, every card taken and every crate event this frame, to be sent until the sequencer
     * has sealed it.
     */
    private void queueEvents(PlayerInputs local) {
        for (int seat = 0; seat < slotOfSeat.length && seat < local.capacity(); seat++) {
            if (slotOfSeat[seat] < 0) {
                continue;
            }
            Order order = local.orderOf(seat);
            if (order != null) {
                queue(slotOfSeat[seat], Msg.EVENT_ORDER, Bundle.orderCode(order), 0);
            }
            UpgradeKind picked = local.pickOf(seat);
            if (picked != null) {
                queue(slotOfSeat[seat], Msg.EVENT_PICK, local.pickSerialOf(seat), Bundle.pickCode(picked));
            }
            PickupKind taken = local.crateTakeOf(seat);
            if (taken != null) {
                queue(slotOfSeat[seat], Msg.EVENT_CRATE, Bundle.crateCode(taken), 0);
            } else if (local.closesCrate(seat)) {
                queue(slotOfSeat[seat], Msg.EVENT_CRATE, Bundle.CRATE_CLOSE, 0);
            }
        }
    }

    private void queue(int slot, int type, int value, int extra) {
        if (eventCount == eventSeq.length) {
            return;
        }
        eventSeq[eventCount] = nextEventSeq++;
        eventSlot[eventCount] = slot;
        eventType[eventCount] = type;
        eventValue[eventCount] = value;
        eventExtra[eventCount] = extra;
        eventCount++;
    }

    private void sendInput(long nowNanos, boolean stepped) {
        long every = paused ? PAUSED_INPUT_TICKS * tickNanos : tickNanos;
        if (!stepped && eventCount == 0 && nowNanos - lastInputNanos < every) {
            return;
        }
        lastInputNanos = nowNanos;
        // The newest tag goes even when no held input does - when every tick this machine has tagged is
        // one it already knows was sealed. That is exactly when its lead is too short, and the sequencer
        // can only say so if it hears what the tag was. But not while this machine is behind the ticks it
        // holds - catching up after a drop-in, a heal or a stall: what it tags then is late because it is,
        // and a report of it comes back a round trip later, when adaptLead can no longer tell. The third
        // playtest: a lead of 17 and 18 for the first seconds after every join.
        boolean catchingUp = contiguous - tick > pacing.bufferTicks();
        out.reset().putByte(Msg.INPUT).putInt(generation).putInt(contiguous).putInt(catchingUp ? -1 : newestTag);
        int seated = 0;
        for (int seat = 0; seat < slotOfSeat.length; seat++) {
            seated += slotOfSeat[seat] >= 0 ? 1 : 0;
        }
        out.putByte(seated);
        for (int seat = 0; seat < slotOfSeat.length; seat++) {
            if (slotOfSeat[seat] >= 0) {
                out.putByte(slotOfSeat[seat]);
            }
        }
        int events = Math.min(eventCount, EVENTS_PER_MESSAGE);
        int room = maxDatagramBytes - out.size() - 4 - 1 - 1 - events * Msg.EVENT_BYTES;
        int fit = seated == 0 ? 0 : Math.min(Math.min(room / (seated * Bundle.HELD_BYTES), 255), OWN / 2);
        int first = Math.max(contiguous + 1, newestTag - fit + 1);
        int count = seated == 0 || newestTag < first ? 0 : newestTag - first + 1;
        out.putInt(first).putByte(count);
        for (int t = first; t < first + count; t++) {
            for (int seat = 0; seat < slotOfSeat.length; seat++) {
                if (slotOfSeat[seat] >= 0) {
                    int at = t & (OWN - 1);
                    Bundle.writeHeld(out, ownTick[seat][at] == t ? own[seat][at] : CommanderInput.IDLE);
                }
            }
        }
        out.putByte(events);
        for (int i = 0; i < events; i++) {
            out.putInt(eventSeq[i])
                    .putByte(eventSlot[i])
                    .putByte(eventType[i])
                    .putInt(eventValue[i])
                    .putByte(eventExtra[i]);
        }
        transport.send(link, Delivery.UNRELIABLE, out.array(), 0, out.size());
    }

    private void checksum(Simulation current) {
        byte[] snapshot = WorldCodec.snapshot(current);
        long hash = WorldCodec.hash(snapshot);
        keyTick[keyNext] = tick;
        keyBytes[keyNext] = snapshot;
        keyHash[keyNext] = hash;
        keyNext = (keyNext + 1) % KEYFRAMES;
        out.reset().putByte(Msg.CHECKSUM).putInt(generation).putInt(tick).putLong(hash);
        transport.send(link, Delivery.RELIABLE, out.array(), 0, out.size());
    }

    /**
     * Steers the lead by how early this machine's input reached the sequencer.
     *
     * <p>Up at once when input was late - but not while this machine is behind the ticks it holds: then
     * the input was late because the machine stalled on its own (its window dragged, its garbage
     * collected), and catching up will cure it; the round trip has not changed. Down once a second's worth
     * of reports all came early, by as much as the least early of them allows, so a lead raised by a
     * passing spike is back where the round trip puts it within a second or two rather than a tick a
     * second - which is what a 0.4 s hitch cost in the first playtest: 25 seconds at 27 ticks.
     *
     * <p>Both judged by this machine's frame, not the world's tick. The sequencer measures how early an
     * input was when it came, but this machine's next comes a frame later, after {@link #frameTicks} more
     * ticks are sealed; and a frame starts holding that many ticks it is simply due, which is not being
     * behind. The 2026-10-01 playtest: at 24 frames a second the lead never rose and every input was late.
     */
    private void adaptLead(int reported) {
        if (reported == Msg.NO_MARGIN) {
            return;
        }
        if (settle > 0) {
            settle--;
            return;
        }
        int margin = reported - (frameTicks - 1);
        if (margin < pacing.marginTicks()) {
            calm = 0;
            calmLeast = Integer.MAX_VALUE;
            if (contiguous - tick > pacing.bufferTicks() + frameTicks - 1) {
                return;
            }
            int was = lead;
            lead = Math.min(pacing.leadMaxTicks(), lead + pacing.marginTicks() - margin);
            settle = lead;
            if (lead != was) {
                log.debug("peer", "event=lead-up lead=" + lead + " margin=" + margin);
            }
        } else if (margin > pacing.marginTicks() + 1) {
            calmLeast = Math.min(calmLeast, margin);
            if (++calm >= CALM_REPORTS && lead > pacing.leadMinTicks()) {
                int was = lead;
                lead = Math.max(pacing.leadMinTicks(), lead - (calmLeast - pacing.marginTicks() - 1));
                calm = 0;
                calmLeast = Integer.MAX_VALUE;
                settle = lead;
                log.debug("peer", "event=lead-down lead=" + lead + " was=" + was);
            }
        } else {
            calm = 0;
            calmLeast = Integer.MAX_VALUE;
        }
    }

    private void stats(long nowNanos) {
        if (statsNanos <= 0 || nowNanos - nextStatsNanos < 0 || paused) {
            return;
        }
        nextStatsNanos = nowNanos + statsNanos;
        log.info(
                "stats",
                "peer=" + peerId + " tick=" + tick + " held=" + (contiguous - tick) + " lead=" + lead + " stalls="
                        + stalls + " paused=" + paused);
        stalls = 0;
    }

    private void received(int type) {
        switch (type) {
            case Msg.WELCOME -> welcome();
            case Msg.REFUSED -> {
                refusal = in.getString(Msg.MAX_TEXT);
                state = State.REFUSED;
                log.warn("peer", "event=refused reason=\"" + refusal + "\"");
            }
            case Msg.BUNDLES -> bundles();
            case Msg.SNAPSHOT -> snapshot();
            case Msg.SLOT -> {
                int seat = in.getByte();
                int slot = in.getByte();
                in.expectEnd();
                if (seat >= slotOfSeat.length || (slot != Msg.NO_SLOT && slot >= capacity)) {
                    throw new WireException("seat " + seat + " to slot " + slot);
                }
                slotOfSeat[seat] = slot == Msg.NO_SLOT ? -1 : slot;
                log.info("peer", "event=seated seat=" + seat + " slot=" + (slot == Msg.NO_SLOT ? "none" : slot));
            }
            case Msg.PAUSED -> {
                paused = in.getByte() != 0;
                int at = in.getInt();
                int by = in.getInt();
                int reason = in.getByte();
                in.expectEnd();
                pausedForDraft = paused && reason == Msg.PAUSE_FOR_DRAFT;
                log.info(
                        "peer",
                        "event=" + (paused ? "paused" : "resumed") + " tick=" + at + " by=" + by
                                + (pausedForDraft ? " reason=draft" : ""));
            }
            case Msg.DESYNC -> {
                int at = in.getInt();
                in.expectEnd();
                log.warn("peer", "event=desync tick=" + at);
                dump(at, "desynced");
            }
            case Msg.DUMP_WANTED -> {
                int at = in.getInt();
                in.expectEnd();
                dump(at, "reference");
            }
            case Msg.SNAPSHOT_WANTED -> {
                int forPeer = in.getInt();
                in.expectEnd();
                Simulation current = world;
                if (current != null && state == State.PLAYING) {
                    byte[] snapshot = WorldCodec.snapshot(current);
                    if (snapshot.length > maxMessageBytes - Msg.SNAPSHOT_HEADER) {
                        // The joiner waits; a crash here would end the session for everybody already in it.
                        log.error(
                                "peer",
                                "event=snapshot-too-big for=" + forPeer + " bytes=" + snapshot.length + " limit="
                                        + (maxMessageBytes - Msg.SNAPSHOT_HEADER),
                                null);
                        return;
                    }
                    out.reset()
                            .putByte(Msg.SNAPSHOT_TAKEN)
                            .putInt(forPeer)
                            .putInt(tick)
                            .putBytes(snapshot);
                    transport.send(link, Delivery.RELIABLE, out.array(), 0, out.size());
                    log.debug(
                            "peer",
                            "event=snapshot-taken for=" + forPeer + " tick=" + tick + " bytes=" + snapshot.length);
                }
            }
            default -> throw new WireException("no message " + type);
        }
    }

    private void welcome() {
        peerId = in.getInt();
        capacity = in.getByte();
        boolean fresh = in.getByte() != 0;
        tickNanos = in.getLong();
        checksumTicks = Msg.getSession(in).checksumTicks();
        String scenario = in.getString(Msg.MAX_TEXT);
        long seed = in.getLong();
        String config = new String(in.getPacked(Msg.MAX_CONFIG), StandardCharsets.UTF_8);
        in.expectEnd();
        if (capacity < 1 || capacity > Players.MAX || tickNanos <= 0) {
            throw new WireException("a session of " + capacity + " slots at " + tickNanos + " ns a tick");
        }
        SessionSetup built = new SessionSetup(config, scenario, seed);
        setup = built;
        bundle = new Bundle(capacity);
        inputs = new PlayerInputs(capacity);
        int size = Integer.highestOneBit(Math.max(2 * checksumTicks + 2 * pacing.leadMaxTicks() + 64, 64) * 2 - 1);
        ringMask = size - 1;
        ring = new byte[size][Sequencer.maxBundleBytes(capacity)];
        ringLength = new int[size];
        ringTick = new int[size];
        Arrays.fill(ringTick, -1);
        log.info(
                "peer",
                "event=welcome peer=" + peerId + " fresh=" + fresh + " scenario=\"" + scenario + "\" seed=" + seed
                        + " capacity=" + capacity + " configBytes=" + config.length());
        if (fresh) {
            world = worlds.fresh(built);
            tick = -1;
            contiguous = -1;
            state = State.PLAYING;
        } else {
            state = State.JOINING;
        }
        for (int seat = 0; seat < seatWanted.length; seat++) {
            if (seatWanted[seat]) {
                sendSeat(seat);
            }
        }
    }

    private void bundles() {
        if (setup == null) {
            // Ticks come by datagram and the welcome by the stream, carrying the host's sim config, so
            // over a real network the first ticks can arrive before it - and there is no reading them without
            // the session's size. The sequencer sends a tick again until it is acknowledged, so this loses none.
            return;
        }
        int applied = in.getInt();
        int margin = in.getInt();
        int count = in.getByte();
        if (applied > eventsAcked) {
            eventsAcked = applied;
            dropAckedEvents();
        }
        adaptLead(margin);
        for (int i = 0; i < count; i++) {
            int start = in.position();
            int at = Bundle.skip(in, capacity);
            int length = in.position() - start;
            if (at > tick && at - tick <= ringMask && ringTick[at & ringMask] != at) {
                System.arraycopy(in.array(), start, ring[at & ringMask], 0, length);
                ringLength[at & ringMask] = length;
                ringTick[at & ringMask] = at;
            }
        }
        in.expectEnd();
        advanceContiguous();
    }

    private void snapshot() {
        int newGeneration = in.getInt();
        int at = in.getInt();
        byte[] snapshot = in.getPacked(maxMessageBytes);
        in.expectEnd();
        SessionSetup built = setup;
        if (built == null) {
            throw new WireException("a snapshot before the welcome");
        }
        // Logged before the world is built, so the gap to the line after says what building it cost.
        log.info(
                "peer",
                "event=snapshot-received tick=" + at + " bytes=" + snapshot.length + " generation=" + newGeneration);
        Simulation restored = worlds.blank(built);
        WorldCodec.restore(restored, snapshot);
        world = restored;
        tick = at;
        contiguous = at;
        generation = newGeneration;
        accumulator = 0L;
        Arrays.fill(keyTick, -1);
        advanceContiguous();
        state = State.PLAYING;
        log.info("peer", "event=restored tick=" + at + " bytes=" + snapshot.length + " generation=" + generation);
    }

    private void advanceContiguous() {
        if (contiguous < tick) {
            contiguous = tick;
        }
        while (ringTick[(contiguous + 1) & ringMask] == contiguous + 1) {
            contiguous++;
        }
    }

    private void dropAckedEvents() {
        int kept = 0;
        for (int i = 0; i < eventCount; i++) {
            if (eventSeq[i] > eventsAcked) {
                eventSeq[kept] = eventSeq[i];
                eventSlot[kept] = eventSlot[i];
                eventType[kept] = eventType[i];
                eventValue[kept] = eventValue[i];
                eventExtra[kept] = eventExtra[i];
                kept++;
            }
        }
        eventCount = kept;
    }

    private void sendSeat(int seat) {
        sendControl(out.reset().putByte(Msg.SEAT).putByte(seat));
    }

    private void sendControl(WireWriter message) {
        if (link < 0 || peerId < 0) {
            return;
        }
        transport.send(link, Delivery.RELIABLE, message.array(), 0, message.size());
    }

    /**
     * Writes what this machine knows about a disputed tick: its keyframe at that tick, the agreed one
     * before it, and every bundle in between - enough to replay the interval anywhere.
     */
    private void dump(int at, String role) {
        Path dir = dumpDir;
        SessionSetup built = setup;
        if (dir == null || built == null) {
            return;
        }
        int disputed = keyframeAt(at);
        if (disputed < 0) {
            log.warn("peer", "event=no-keyframe tick=" + at);
            return;
        }
        int agreed = keyframeAt(at - checksumTicks);
        List<byte[]> between = new ArrayList<>();
        if (agreed >= 0) {
            for (int t = keyTick[agreed] + 1; t <= at; t++) {
                int slot = t & ringMask;
                if (ringTick[slot] != t) {
                    between.clear();
                    break;
                }
                between.add(Arrays.copyOf(ring[slot], ringLength[slot]));
            }
        }
        DesyncDump dump = new DesyncDump(
                build,
                peerId,
                role,
                built,
                agreed < 0 ? -1 : keyTick[agreed],
                agreed < 0 ? new byte[0] : keyBytes[agreed],
                at,
                keyBytes[disputed],
                keyHash[disputed],
                between);
        Path file = dir.resolve("desync-t" + at + "-peer" + peerId + "-" + role + ".bin");
        try {
            dump.write(file);
            log.warn("peer", "event=dumped file=\"" + file + "\" tick=" + at + " bundles=" + between.size());
        } catch (IOException e) {
            log.error("peer", "event=dump-failed file=\"" + file + "\"", e);
        }
    }

    private int keyframeAt(int at) {
        for (int i = 0; i < KEYFRAMES; i++) {
            if (keyTick[i] == at && keyBytes[i] != null) {
                return i;
            }
        }
        return -1;
    }

    private final class Listener implements TransportListener {
        @Override
        public void opened(int opened, String remote) {
            if (opened != link) {
                return;
            }
            out.reset()
                    .putByte(Msg.HELLO)
                    .putInt(Msg.PROTOCOL)
                    .putString(build)
                    .putLong(hostKey)
                    .putString(machineId);
            transport.send(link, Delivery.RELIABLE, out.array(), 0, out.size());
            log.info("peer", "event=hello remote=" + remote + " build=" + build + " machine=" + machineId);
        }

        @Override
        public void received(int from, Delivery delivery, byte[] data, int offset, int length) {
            if (from != link) {
                return;
            }
            try {
                in.wrap(data, offset, length);
                PeerSession.this.received(in.getByte());
            } catch (WireException | IllegalStateException e) {
                log.error("peer", "event=malformed reason=\"" + e.getMessage() + "\"", e);
                transport.disconnect(link, "malformed: " + e.getMessage());
            }
        }

        @Override
        public void closed(int closed, String reason) {
            if (closed != link) {
                return;
            }
            if (state != State.REFUSED) {
                state = State.CLOSED;
            }
            closedBecause = reason;
            log.info("peer", "event=closed reason=\"" + reason + "\"");
        }
    }
}
