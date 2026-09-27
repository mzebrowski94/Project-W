package pl.mzebrows.pwt.net.session;

import java.nio.charset.StandardCharsets;
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
import pl.mzebrows.pwt.sim.def.Order;

/**
 * The session's clock and nothing else (ADR 0012): every tick it seals what each slot is holding, and
 * the input events that have arrived, into a {@link Bundle}, and sends it to every peer. It holds no
 * world. A peer whose input is late for a tick has its last input repeated for it; nobody waits.
 *
 * <p><strong>Why it holds no world.</strong> A sequencer that simulated would be a host whose window
 * being dragged, or whose garbage collector pausing, stalls everybody. This one only needs a thread that
 * wakes sixty times a second, so today it runs beside the host's game and tomorrow it can run on a relay,
 * where the host leaving need not end the session.
 *
 * <p>What it decides, it decides alone, so every peer is told the same: which slot a newly seated player
 * gets, the tick an order lands on, when the session pauses, when it restarts. Pause is anyone's to ask
 * for; restart and god mode only the host's (G0038). The host is the peer that knew {@link #hostKey()},
 * which its own game handed it in process.
 *
 * <p>It is also where desyncs are noticed: every peer reports a hash of its world every {@code
 * checksumTicks}, the host's is the reference, and a peer that disagrees is told to write a dump and is
 * given a fresh snapshot of the host's world - the drop-in path, used to heal.
 *
 * <p>Driven by {@link #pump(long)} with the time passed in, so a test runs a whole session on a manual
 * clock; {@link SequencerThread} is what drives it for real. Single-threaded, like its transport.
 */
public final class Sequencer implements AutoCloseable {

    /** How many ticks ahead of the last sealed one a slot's input can be held. A power of two. */
    private static final int AHEAD = 128;

    /** How many checksum ticks back the host's hashes are remembered, waiting for slower peers'. */
    private static final int HASHES = 16;

    private final Transport transport;
    private final NetConfig.Session pacing;
    private final int maxDatagramBytes;
    private final int maxMessageBytes;
    private final NetLog log;
    private final SessionSetup setup;
    private final String build;
    private final long hostKey;
    private final long tickNanos;
    private final int capacity;
    private final long statsNanos;

    /**
     * Every link, in the order it opened. A list walked by index, because it is walked every tick and an
     * iterator per walk would be an allocation per tick.
     */
    private final List<Peer> peers = new ArrayList<>();

    private int nextPeerId;

    /** Which peer each slot belongs to, or -1. */
    private final int[] owner;

    private final CommanderInput[] lastHeld;
    private final CommanderInput[][] ahead;
    private final int[][] aheadTick;
    private final boolean[] joinWanted;

    private final byte[][] history;
    private final int[] historyLength;
    private final int[] historyTick;

    private final long[] hostHash = new long[HASHES];
    private final int[] hostHashTick = new int[HASHES];

    private final Bundle sealing;
    private final WireWriter out;
    private final WireReader in = new WireReader();
    private final Listener listener = new Listener();

    private int sealed = -1;
    private boolean started;
    private boolean paused;
    private long now;
    private long nextSealNanos;
    private long nextStatsNanos;
    private boolean restartWanted;
    private boolean invulnerable;
    private boolean invulnerabilityChanged;
    private int dumpedTick = -1;
    private int desyncs;

    /**
     * The latest any tick was sealed after its time, since the last stats line. What a coarse timer on
     * the sequencer's thread looks like from inside: Windows has been known to wake a sleeper 15 ms late.
     */
    private long lateMostNanos;

    /**
     * @param setup what every peer builds its world from - the host's config, scenario and seed
     * @param capacity how many slots the session has; the ceiling is {@code Players.MAX}
     * @param tickSeconds the world's tick, from the host's config - bundles are sealed at this rate
     * @param hostKey what the host's own peer says in its hello, to be known as the host
     * @param build this build's id; a peer with another is refused
     */
    public Sequencer(
            Transport transport,
            NetConfig config,
            NetLog log,
            SessionSetup setup,
            int capacity,
            float tickSeconds,
            long hostKey,
            String build) {
        this.transport = transport;
        this.pacing = config.session();
        this.maxDatagramBytes = config.maxDatagramBytes();
        this.maxMessageBytes = config.maxMessageBytes();
        this.log = log;
        this.setup = setup;
        this.build = build;
        this.hostKey = hostKey;
        this.tickNanos = Math.round(tickSeconds * 1e9);
        this.capacity = capacity;
        this.statsNanos = config.log().statsSeconds() * 1_000_000_000L;
        owner = new int[capacity];
        Arrays.fill(owner, -1);
        lastHeld = new CommanderInput[capacity];
        Arrays.fill(lastHeld, CommanderInput.IDLE);
        ahead = new CommanderInput[capacity][AHEAD];
        aheadTick = new int[capacity][AHEAD];
        for (int[] ticks : aheadTick) {
            Arrays.fill(ticks, -1);
        }
        joinWanted = new boolean[capacity];
        int historySize = pacing.historyTicks();
        history = new byte[historySize][maxBundleBytes(capacity)];
        historyLength = new int[historySize];
        historyTick = new int[historySize];
        Arrays.fill(historyTick, -1);
        Arrays.fill(hostHashTick, -1);
        sealing = new Bundle(capacity);
        out = new WireWriter(Math.max(2048, maxDatagramBytes));
    }

    /** The most bytes one sealed tick can take for a session this size. */
    static int maxBundleBytes(int capacity) {
        return 4 + 1 + 1 + capacity * (1 + Bundle.HELD_BYTES) + 1 + capacity * 2 + 1 + capacity;
    }

    /** Starts listening for peers; returns the port it got. */
    public int listen(int firstPort, int search) {
        int port = transport.listen(firstPort, search);
        log.info(
                "sequencer",
                "event=listening port=" + port + " build=" + build + " scenario=\"" + setup.scenario() + "\" seed="
                        + setup.seed() + " capacity=" + capacity);
        return port;
    }

    public long hostKey() {
        return hostKey;
    }

    /** The last tick sealed, or -1 before the host's peer has started the session. */
    public int sealedTick() {
        return sealed;
    }

    public boolean paused() {
        return paused;
    }

    /** How many times a peer's world has disagreed with the host's, this session. */
    public int desyncs() {
        return desyncs;
    }

    /** When the next tick is due, for the thread driving this to sleep until. */
    public long nextDeadlineNanos() {
        return started && !paused ? nextSealNanos : now + tickNanos;
    }

    /** Hears every peer, seals every tick that is due, and sends every peer what it has not got yet. */
    public void pump(long nowNanos) {
        now = nowNanos;
        transport.poll(listener);
        askForSnapshots();
        int sealedNow = 0;
        if (started && !paused) {
            while (nowNanos - nextSealNanos >= 0 && sealedNow < pacing.catchUpTicks()) {
                lateMostNanos = Math.max(lateMostNanos, nowNanos - nextSealNanos);
                seal();
                nextSealNanos += tickNanos;
                sealedNow++;
            }
            if (nowNanos - nextSealNanos >= 0) {
                // Stalled for longer than a catch-up covers. The session slows down rather than skipping a
                // tick: time is re-anchored, and every peer simply sees ticks arrive late.
                log.warn("sequencer", "event=stalled behindTicks=" + (nowNanos - nextSealNanos) / tickNanos);
                nextSealNanos = nowNanos + tickNanos;
            }
        }
        sendBundles(sealedNow > 0);
        stats();
    }

    @Override
    public void close() {
        end("the host left the game");
    }

    /**
     * Ends the session: tells every peer why, then lets go of the transport. A peer sees the link close
     * with this reason, and its game goes back to the menu saying so (G0038) - rather than leaving its
     * players looking at a world that will never move again.
     */
    public void end(String reason) {
        for (int i = 0; i < peers.size(); i++) {
            transport.disconnect(peers.get(i).link, reason);
        }
        transport.close();
    }

    private void seal() {
        int tick = sealed + 1;
        sealing.clear(tick);
        if (restartWanted) {
            sealing.restarting();
            for (int slot = 0; slot < capacity; slot++) {
                if (owner[slot] >= 0) {
                    joinWanted[slot] = true;
                }
            }
            if (invulnerable) {
                invulnerabilityChanged = true;
            }
            restartWanted = false;
            log.info("sequencer", "event=restart tick=" + tick);
        }
        int index = tick & (AHEAD - 1);
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] < 0) {
                continue;
            }
            CommanderInput held;
            if (aheadTick[slot][index] == tick) {
                held = ahead[slot][index];
            } else {
                held = lastHeld[slot];
                Peer peer = peerWithId(owner[slot]);
                if (peer != null) {
                    peer.repeats++;
                }
            }
            lastHeld[slot] = held;
            sealing.hold(slot, held);
            if (joinWanted[slot]) {
                sealing.join(slot);
                joinWanted[slot] = false;
            }
        }
        if (invulnerabilityChanged) {
            sealing.switchInvulnerability(invulnerable);
            invulnerabilityChanged = false;
        }
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            peer.applyEvents(sealing, owner);
        }
        out.reset();
        sealing.write(out);
        int at = tick % history.length;
        System.arraycopy(out.array(), 0, history[at], 0, out.size());
        historyLength[at] = out.size();
        historyTick[at] = tick;
        sealed = tick;
    }

    private void sendBundles(boolean sealedNow) {
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.state != PeerState.PLAYING) {
                continue;
            }
            boolean behind = peer.acked < sealed;
            if (!sealedNow && !(behind && now - peer.lastSentNanos >= tickNanos)) {
                continue;
            }
            if (sealed - peer.acked >= history.length) {
                // What it is missing has gone from the history. Anybody but the host is brought forward
                // with a snapshot; the host is where snapshots come from, so it is only reported.
                if (!peer.host) {
                    log.warn(
                            "sequencer",
                            "event=fell-behind peer=" + peer.id + " acked=" + peer.acked + " sealed=" + sealed);
                    heal(peer);
                } else if (now - peer.lastSentNanos >= statsNanos) {
                    log.error("sequencer", "event=host-fell-behind acked=" + peer.acked + " sealed=" + sealed, null);
                    peer.lastSentNanos = now;
                }
                continue;
            }
            out.reset().putByte(Msg.BUNDLES).putInt(peer.eventsApplied).putInt(peer.margin);
            int countAt = out.size();
            out.putByte(0);
            out.array()[countAt] = (byte) writeBundles(peer);
            transport.send(peer.link, Delivery.UNRELIABLE, out.array(), 0, out.size());
            peer.margin = Msg.NO_MARGIN;
            peer.lastSentNanos = now;
        }
    }

    /**
     * Fills a datagram for one peer and returns how many bundles went in. The newest {@code
     * redundantTicks} unacknowledged ticks always go, so one lost datagram costs nothing; room is kept for
     * them first. Older unacknowledged ticks go oldest first, but only those never sent or not
     * acknowledged within the peer's round trip, so however long the round trip, a peer is never sent
     * the same oldest ticks while the newest wait.
     */
    private int writeBundles(Peer peer) {
        int newestFrom = Math.max(peer.acked + 1, sealed - pacing.redundantTicks() + 1);
        int newestBytes = 0;
        for (int tick = newestFrom; tick <= sealed; tick++) {
            newestBytes += historyLength[tick % history.length];
        }
        long resendAfter = peer.roundTripNanos == 0L ? 0L : peer.roundTripNanos + 2 * tickNanos;
        int count = 0;
        for (int tick = peer.acked + 1; tick < newestFrom && count < 255; tick++) {
            int at = tick % history.length;
            if (peer.sentTick[at] == tick && now - peer.sentNanos[at] < resendAfter) {
                continue;
            }
            if (out.size() + historyLength[at] + newestBytes > maxDatagramBytes) {
                break;
            }
            writeBundle(peer, tick);
            count++;
        }
        for (int tick = newestFrom; tick <= sealed && count < 255; tick++) {
            if (out.size() + historyLength[tick % history.length] > maxDatagramBytes) {
                break;
            }
            writeBundle(peer, tick);
            count++;
        }
        return count;
    }

    private void writeBundle(Peer peer, int tick) {
        int at = tick % history.length;
        out.putRaw(history[at], 0, historyLength[at]);
        if (peer.sentTick[at] != tick) {
            peer.sentTick[at] = tick;
            peer.firstSentNanos[at] = now;
        }
        peer.sentNanos[at] = now;
    }

    private void askForSnapshots() {
        Peer host = host();
        if (host == null || host.state != PeerState.PLAYING) {
            return;
        }
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.wantsSnapshot && !peer.snapshotAsked) {
                out.reset().putByte(Msg.SNAPSHOT_WANTED).putInt(peer.id);
                transport.send(host.link, Delivery.RELIABLE, out.array(), 0, out.size());
                peer.snapshotAsked = true;
                log.info("sequencer", "event=snapshot-asked for=" + peer.id + " from=" + host.id);
            }
        }
    }

    private void heal(Peer peer) {
        peer.state = PeerState.JOINING;
        peer.wantsSnapshot = true;
        peer.snapshotAsked = false;
    }

    private void stats() {
        if (statsNanos <= 0 || now - nextStatsNanos < 0 || !started) {
            return;
        }
        nextStatsNanos = now + statsNanos;
        StringBuilder line = new StringBuilder("tick=")
                .append(sealed)
                .append(" paused=")
                .append(paused)
                .append(" sealLateMostMs=")
                .append(lateMostNanos / 1_000_000L);
        lateMostNanos = 0L;
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.id >= 0) {
                line.append(" peer")
                        .append(peer.id)
                        .append("=behind:")
                        .append(sealed - peer.acked)
                        .append(",repeats:")
                        .append(peer.repeats)
                        .append(",rttMs:")
                        .append(peer.roundTripNanos / 1_000_000L)
                        .append(",state:")
                        .append(peer.state);
                peer.repeats = 0;
            }
        }
        log.info("stats", line.toString());
    }

    private void received(Peer peer, int type) {
        if (peer.id < 0 && type != Msg.HELLO) {
            throw new WireException("message " + type + " before hello");
        }
        switch (type) {
            case Msg.HELLO -> hello(peer);
            case Msg.INPUT -> input(peer);
            case Msg.CHECKSUM -> checksum(peer);
            case Msg.SNAPSHOT_TAKEN -> snapshotTaken(peer);
            case Msg.SEAT -> seat(peer);
            case Msg.PAUSE -> pause(peer);
            case Msg.RESTART -> restart(peer);
            case Msg.GOD_MODE -> godMode(peer);
            default -> throw new WireException("no message " + type);
        }
    }

    private void hello(Peer peer) {
        int protocol = in.getInt();
        String theirBuild = in.getString(Msg.MAX_TEXT);
        long key = in.getLong();
        in.expectEnd();
        if (peer.id >= 0) {
            return;
        }
        if (protocol != Msg.PROTOCOL) {
            refuse(peer, "protocol " + protocol + ", the host speaks " + Msg.PROTOCOL);
            return;
        }
        if (!theirBuild.equals(build) || build.equals(BuildId.UNKNOWN)) {
            refuse(peer, "build " + theirBuild + ", the host runs " + build);
            return;
        }
        peer.id = nextPeerId++;
        peer.host = key == hostKey && host() == null;
        boolean fresh = !started && peer.host;
        byte[] config = setup.configText().getBytes(StandardCharsets.UTF_8);
        out.reset()
                .putByte(Msg.WELCOME)
                .putInt(peer.id)
                .putByte(capacity)
                .putByte(fresh ? 1 : 0)
                .putLong(tickNanos)
                .putString(setup.scenario())
                .putLong(setup.seed())
                .putBytes(config);
        transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
        if (fresh) {
            started = true;
            peer.state = PeerState.PLAYING;
            nextSealNanos = now + tickNanos;
            nextStatsNanos = now + statsNanos;
        } else {
            heal(peer);
        }
        log.info(
                "sequencer",
                "event=welcome peer=" + peer.id + " link=" + peer.link + " host=" + peer.host + " fresh=" + fresh);
    }

    private void refuse(Peer peer, String reason) {
        log.warn("sequencer", "event=refused link=" + peer.link + " reason=\"" + reason + "\"");
        out.reset().putByte(Msg.REFUSED).putString(reason);
        transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
        transport.disconnect(peer.link, "refused: " + reason);
    }

    private void input(Peer peer) {
        int generation = in.getInt();
        int acked = in.getInt();
        int newestTag = in.getInt();
        if (generation == peer.generation && acked > peer.acked && acked <= sealed) {
            peer.acked = acked;
            peer.measure(acked, now);
        }
        if (newestTag >= 0) {
            // How many ticks before its seal the newest input arrived: what the peer's lead is steered by.
            int margin = newestTag - sealed;
            peer.margin = peer.margin == Msg.NO_MARGIN ? margin : Math.min(peer.margin, margin);
        }
        int slotCount = in.getByte();
        if (slotCount > capacity) {
            throw new WireException(slotCount + " slots in a session of " + capacity);
        }
        for (int i = 0; i < slotCount; i++) {
            int slot = in.getByte();
            if (slot >= capacity) {
                throw new WireException("slot " + slot + " in a session of " + capacity);
            }
            peer.inputSlots[i] = slot;
        }
        int firstTick = in.getInt();
        int tickCount = in.getByte();
        for (int t = 0; t < tickCount; t++) {
            int tick = firstTick + t;
            for (int i = 0; i < slotCount; i++) {
                int slot = peer.inputSlots[i];
                if (owner[slot] != peer.id || tick <= sealed || tick - sealed >= AHEAD) {
                    in.skip(Bundle.HELD_BYTES);
                    continue;
                }
                int index = tick & (AHEAD - 1);
                ahead[slot][index] = Bundle.readHeld(in, lastHeld[slot]);
                aheadTick[slot][index] = tick;
            }
        }
        int eventCount = in.getByte();
        for (int i = 0; i < eventCount; i++) {
            int seq = in.getInt();
            int slot = in.getByte();
            int code = in.getByte();
            if (slot >= capacity) {
                throw new WireException("slot " + slot + " in a session of " + capacity);
            }
            peer.hold(seq, slot, Bundle.orderFromCode(code));
        }
        in.expectEnd();
    }

    private void checksum(Peer peer) {
        int generation = in.getInt();
        int tick = in.getInt();
        long hash = in.getLong();
        in.expectEnd();
        if (peer.host) {
            int at = (tick / pacing.checksumTicks()) % HASHES;
            hostHash[at] = hash;
            hostHashTick[at] = tick;
            for (int i = 0; i < peers.size(); i++) {
                Peer other = peers.get(i);
                if (other != peer) {
                    other.compareWaiting(tick, hash);
                }
            }
            return;
        }
        if (generation != peer.generation || peer.state != PeerState.PLAYING) {
            return;
        }
        int at = (tick / pacing.checksumTicks()) % HASHES;
        if (hostHashTick[at] == tick) {
            compare(peer, tick, hostHash[at], hash);
        } else {
            peer.waitFor(tick, hash);
        }
    }

    private void compare(Peer peer, int tick, long reference, long theirs) {
        if (reference == theirs || peer.state != PeerState.PLAYING) {
            return;
        }
        desyncs++;
        log.warn(
                "sequencer",
                "event=desync peer=" + peer.id + " tick=" + tick + " host=" + Long.toHexString(reference) + " peer="
                        + Long.toHexString(theirs));
        out.reset().putByte(Msg.DESYNC).putInt(tick);
        transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
        Peer host = host();
        if (host != null && dumpedTick != tick) {
            out.reset().putByte(Msg.DUMP_WANTED).putInt(tick);
            transport.send(host.link, Delivery.RELIABLE, out.array(), 0, out.size());
            dumpedTick = tick;
        }
        heal(peer);
    }

    private void snapshotTaken(Peer from) {
        int forPeer = in.getInt();
        int tick = in.getInt();
        int length = in.getInt();
        if (length < 0 || length > in.remaining() || length > maxMessageBytes - Msg.SNAPSHOT_HEADER) {
            throw new WireException("a snapshot of " + length + " bytes");
        }
        int start = in.position();
        in.skip(length);
        in.expectEnd();
        Peer target = peerWithId(forPeer);
        if (!from.host || target == null || !target.wantsSnapshot) {
            return;
        }
        if (tick > sealed || sealed - tick >= history.length - 1) {
            target.snapshotAsked = false;
            return;
        }
        target.generation++;
        out.reset().putByte(Msg.SNAPSHOT).putInt(target.generation).putInt(tick).putBytes(in.array(), start, length);
        transport.send(target.link, Delivery.RELIABLE, out.array(), 0, out.size());
        target.acked = tick;
        target.state = PeerState.PLAYING;
        target.wantsSnapshot = false;
        target.snapshotAsked = false;
        log.info(
                "sequencer",
                "event=snapshot-sent peer=" + target.id + " tick=" + tick + " bytes=" + length + " generation="
                        + target.generation);
    }

    private void seat(Peer peer) {
        int seat = in.getByte();
        in.expectEnd();
        if (seat >= capacity) {
            throw new WireException("seat " + seat + " in a session of " + capacity);
        }
        int slot = peer.slotOfSeat[seat];
        if (slot < 0) {
            // Slot zero is the host's keyboard, held for it: it is the commander a scenario starts with,
            // and the host plays from the first tick (G0038) - whoever's hello happened to arrive first.
            boolean hostsKeyboard = peer.host && seat == 0;
            for (int free = hostsKeyboard ? 0 : 1; free < capacity && slot < 0; free++) {
                if (owner[free] < 0) {
                    slot = free;
                }
            }
            if (slot >= 0) {
                owner[slot] = peer.id;
                lastHeld[slot] = CommanderInput.IDLE;
                Arrays.fill(aheadTick[slot], -1);
                joinWanted[slot] = true;
                peer.slotOfSeat[seat] = slot;
            }
            log.info("sequencer", "event=seated peer=" + peer.id + " seat=" + seat + " slot=" + slot);
        }
        out.reset().putByte(Msg.SLOT).putByte(seat).putByte(slot < 0 ? Msg.NO_SLOT : slot);
        transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
    }

    private void pause(Peer peer) {
        boolean want = in.getByte() != 0;
        in.expectEnd();
        if (want == paused || !started) {
            return;
        }
        paused = want;
        if (!paused) {
            nextSealNanos = now + tickNanos;
        }
        log.info("sequencer", "event=" + (paused ? "paused" : "resumed") + " by=" + peer.id + " tick=" + sealed);
        out.reset().putByte(Msg.PAUSED).putByte(paused ? 1 : 0).putInt(sealed).putInt(peer.id);
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id >= 0) {
                transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
            }
        }
    }

    private void restart(Peer peer) {
        in.expectEnd();
        if (!peer.host) {
            log.warn("sequencer", "event=refused-restart peer=" + peer.id);
            return;
        }
        restartWanted = true;
    }

    private void godMode(Peer peer) {
        boolean on = in.getByte() != 0;
        in.expectEnd();
        if (!peer.host) {
            log.warn("sequencer", "event=refused-god-mode peer=" + peer.id);
            return;
        }
        invulnerable = on;
        invulnerabilityChanged = true;
        log.info("sequencer", "event=god-mode on=" + on);
    }

    private void left(Peer peer, String reason) {
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] == peer.id && peer.id >= 0) {
                owner[slot] = -1;
                lastHeld[slot] = CommanderInput.IDLE;
            }
        }
        log.info("sequencer", "event=left peer=" + peer.id + " link=" + peer.link + " reason=\"" + reason + "\"");
    }

    private @Nullable Peer host() {
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.host) {
                return peer;
            }
        }
        return null;
    }

    private @Nullable Peer onLink(int link) {
        for (int i = 0; i < peers.size(); i++) {
            if (peers.get(i).link == link) {
                return peers.get(i);
            }
        }
        return null;
    }

    private @Nullable Peer peerWithId(int id) {
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.id == id) {
                return peer;
            }
        }
        return null;
    }

    private enum PeerState {
        HELLO,
        JOINING,
        PLAYING
    }

    /** One link, and what the sequencer knows of the peer at the other end. */
    private final class Peer {
        final int link;
        final int[] slotOfSeat = new int[capacity];
        final int[] inputSlots = new int[capacity];
        int id = -1;
        boolean host;
        PeerState state = PeerState.HELLO;
        int acked = -1;
        int generation;
        int margin = Msg.NO_MARGIN;
        int repeats;
        long lastSentNanos;
        boolean wantsSnapshot;
        boolean snapshotAsked;

        /** Orders heard but not yet sealed: numbered, applied in number order, once each. */
        int eventsApplied;

        final int[] eventSeq = new int[32];
        final int[] eventSlot = new int[32];
        final Order[] eventOrder = new Order[32];
        int eventCount;

        /** Per history slot: which tick was last sent there, when first and when last. */
        final int[] sentTick = new int[history.length];

        final long[] firstSentNanos = new long[history.length];
        final long[] sentNanos = new long[history.length];

        /** From a bundle's first send to its acknowledgement, smoothed; zero until the first is heard. */
        long roundTripNanos;

        /** Checksums waiting for the host's for the same tick. */
        final int[] waitingTick = new int[HASHES];

        final long[] waitingHash = new long[HASHES];

        Peer(int link) {
            this.link = link;
            Arrays.fill(slotOfSeat, -1);
            Arrays.fill(waitingTick, -1);
            Arrays.fill(sentTick, -1);
        }

        void measure(int ackedTick, long nowNanos) {
            int at = ackedTick % history.length;
            if (sentTick[at] != ackedTick) {
                return;
            }
            long sample = Math.max(1L, nowNanos - firstSentNanos[at]);
            roundTripNanos = roundTripNanos == 0L ? sample : (3 * roundTripNanos + sample) / 4;
        }

        void hold(int seq, int slot, Order order) {
            if (seq <= eventsApplied || eventCount == eventSeq.length) {
                return;
            }
            for (int i = 0; i < eventCount; i++) {
                if (eventSeq[i] == seq) {
                    return;
                }
            }
            eventSeq[eventCount] = seq;
            eventSlot[eventCount] = slot;
            eventOrder[eventCount] = order;
            eventCount++;
        }

        /** Seals every order that is next in line, one per slot per tick; a second waits for the next tick. */
        void applyEvents(Bundle bundle, int[] owners) {
            while (true) {
                int next = -1;
                for (int i = 0; i < eventCount; i++) {
                    if (eventSeq[i] == eventsApplied + 1) {
                        next = i;
                    }
                }
                if (next < 0) {
                    return;
                }
                int slot = eventSlot[next];
                if (owners[slot] == id) {
                    if (bundle.orderOf(slot) != null) {
                        return;
                    }
                    bundle.order(slot, eventOrder[next]);
                }
                eventsApplied++;
                eventCount--;
                eventSeq[next] = eventSeq[eventCount];
                eventSlot[next] = eventSlot[eventCount];
                eventOrder[next] = eventOrder[eventCount];
            }
        }

        void waitFor(int tick, long hash) {
            int at = (tick / pacing.checksumTicks()) % HASHES;
            waitingTick[at] = tick;
            waitingHash[at] = hash;
        }

        void compareWaiting(int tick, long reference) {
            int at = (tick / pacing.checksumTicks()) % HASHES;
            if (waitingTick[at] == tick) {
                waitingTick[at] = -1;
                compare(this, tick, reference, waitingHash[at]);
            }
        }
    }

    private final class Listener implements TransportListener {
        @Override
        public void opened(int link, String remote) {
            peers.add(new Peer(link));
            log.info("sequencer", "event=link link=" + link + " remote=" + remote);
        }

        @Override
        public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
            Peer peer = onLink(link);
            if (peer == null) {
                return;
            }
            try {
                in.wrap(data, offset, length);
                Sequencer.this.received(peer, in.getByte());
            } catch (WireException e) {
                log.warn(
                        "sequencer",
                        "event=malformed link=" + link + " peer=" + peer.id + " reason=\"" + e.getMessage() + "\"");
                transport.disconnect(link, "malformed: " + e.getMessage());
            }
        }

        @Override
        public void closed(int link, String reason) {
            Peer peer = onLink(link);
            if (peer != null) {
                peers.remove(peer);
                left(peer, reason);
            }
        }
    }
}
