package pl.mzebrows.pwt.net.session;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.Door;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

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
 * checksumTicks} - the host's, which each welcome carries as the session config (ADR 0018) - the host's
 * hash is the reference, and a peer that disagrees is told to write a dump and is
 * given a fresh snapshot of the host's world - the drop-in path, used to heal.
 *
 * <p>Driven by {@link #pump(long)} with the time passed in, so a test runs a whole session on a manual
 * clock; {@link SequencerThread} is what drives it for real. Single-threaded, like its transport.
 */
public final class Sequencer implements AutoCloseable {

    /**
     * How many ticks ahead of the last sealed one a slot's input can be held: more than any lead. A power
     * of two.
     */
    private static final int AHEAD = Integer.highestOneBit(NetConfig.Pacing.MOST_LEAD_TICKS) * 2;

    /** How many checksum ticks back the host's hashes are remembered, waiting for slower peers'. */
    private static final int HASHES = 16;

    /** How often a session on its briefing looks up from waiting: nothing is sealed, a link is only heard. */
    private static final long BRIEFING_NANOS = 16_666_667L;

    /** What a machine is told when it comes back by a token the sequencer no longer holds (G0105). */
    static final String GIVEN_UP = Door.GIVEN_UP;

    private final Transport transport;
    private final NetConfig.Pacing pacing;
    private final NetConfig.Session session;
    private final int maxDatagramBytes;
    private final NetLog log;
    private final String build;
    private final long hostKey;
    private final RandomGenerator random;

    /** How long a dropped machine's seats are kept for it (G0105): the one place the key is read. */
    private final long windowNanos;

    private final int windowSeconds;

    /**
     * Machines whose link dropped and whose seats are kept until they come back by their token or the window
     * ends (G0105), in the order they dropped. A list walked by index, once a pump.
     */
    private final List<Peer> held = new ArrayList<>();

    /**
     * Whether the host's link gone past its window hands the session to another peer (G0104) rather than ending it:
     * true for a room on a relay, whose sequencer outlives the host's machine; false for the host's own machine,
     * where the sequencer goes with him.
     */
    private boolean handsOver;

    /** The new host a tick is yet to seal, or -1: his peer number, and the lowest slot he holds (G0104). */
    private int hostChangePeer = -1;

    private int hostChangeSlot;

    /** The host has been handed to another peer at least once: the host's key no longer makes anybody host (G0104). */
    private boolean handedOver;

    /** The session is over: its peers were told and its transport let go. */
    private boolean ended;

    /** Whether the host's peer has said its hello: what a relay reads to tell a room whose host never arrived. */
    private volatile boolean hostWelcomed;

    /** The world's tick, as the START said; zero while the session is on its briefing. */
    private long tickNanos;

    /** What the START carried, opaque, for a machine that arrives after it; empty on the briefing. */
    private byte[] startBytes = new byte[0];

    /** What the host last put on the briefing: opaque, kept for a machine that arrives (G0101). */
    private byte[] note = new byte[0];

    /** By slot, the machine that took it: kept while the session is on its briefing, for a players and a return. */
    private final String[] holderMachine;

    private final int capacity;
    private final long statsNanos;

    /**
     * The longest round trip a session can be played over - the lead that has to cover it stops at
     * leadMaxTicks each way. A peer slower than that to answer is not on a slow link but not listening.
     */
    private long longestRoundTripNanos;

    /**
     * Every link, in the order it opened. A list walked by index, because it is walked every tick and an
     * iterator per walk would be an allocation per tick.
     */
    private final List<Peer> peers = new ArrayList<>();

    private int nextPeerId;

    /** Which peer each slot belongs to, or -1. */
    private final int[] owner;

    /** By slot, the held bytes it last sealed: opaque, repeated for a slot whose input is late (ADR 0023). */
    private final byte[][] lastHeld;

    private final byte[][][] ahead;
    private final int[][] aheadTick;
    private final boolean[] joinWanted;

    /**
     * By slot, whether its link dropped and nobody has been seated in it since (G0052): its commander
     * stands idle, and the draft rotation passes it by. Sealed as <em>away</em> the tick after the drop and
     * as <em>back</em> the tick after somebody is seated in it again.
     */
    private final boolean[] away;

    private final boolean[] awayWanted;
    private final boolean[] backWanted;

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

    /** Why the pause in force was asked for: a player's hand, or a solo player's draft (G0049). */
    private int pauseReason = Msg.PAUSE_BY_PLAYER;

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

    /** The latest any tick was sealed after its time, ever: what a relay's operator reads (ADR 0023's revisit). */
    private volatile long lateMostEverNanos;

    /**
     * When something happened that was not an acknowledgement: a message other than input and checksums, an
     * input event, or held input that changed. What a relay reads to close a room nobody is playing in.
     */
    private volatile long activeNanos;

    /**
     * @param capacity how many slots the session has; the ceiling is {@code Players.MAX}
     * @param hostKey what the host's own peer says in its hello, to be known as the host
     * @param build this build's id; a peer with another is refused
     */
    public Sequencer(Transport transport, NetConfig config, NetLog log, int capacity, long hostKey, String build) {
        this(transport, config, log, capacity, hostKey, build, new SecureRandom());
    }

    /** @param random where the rejoin tokens come from: a secure one in play, a seeded one in a test (G0105) */
    public Sequencer(
            Transport transport,
            NetConfig config,
            NetLog log,
            int capacity,
            long hostKey,
            String build,
            RandomGenerator random) {
        this.transport = transport;
        this.random = random;
        this.windowSeconds = config.rejoin().windowSeconds();
        this.windowNanos = windowSeconds * 1_000_000_000L;
        this.pacing = config.pacing();
        this.session = config.session();
        this.maxDatagramBytes = config.maxDatagramBytes();
        this.log = log;
        this.build = build;
        this.hostKey = hostKey;
        this.capacity = capacity;
        this.statsNanos = config.log().statsSeconds() * 1_000_000_000L;
        owner = new int[capacity];
        holderMachine = new String[capacity];
        Arrays.fill(holderMachine, "");
        Arrays.fill(owner, -1);
        lastHeld = new byte[capacity][Bundle.HELD_BYTES];
        ahead = new byte[capacity][AHEAD][Bundle.HELD_BYTES];
        aheadTick = new int[capacity][AHEAD];
        for (int[] ticks : aheadTick) {
            Arrays.fill(ticks, -1);
        }
        joinWanted = new boolean[capacity];
        away = new boolean[capacity];
        awayWanted = new boolean[capacity];
        backWanted = new boolean[capacity];
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
        return 4
                + 1
                + 1
                + capacity * (1 + Bundle.HELD_BYTES)
                + 1
                + capacity * 2
                + 1
                // A join per slot: its number and the machine it sits at (G0056).
                + capacity * (1 + 4)
                // A card taken per slot, then a presence change per slot.
                + 1
                + capacity * (1 + 4 + 1)
                + 1
                + capacity * 2
                // A crate's event per slot (G0056): its number and what it takes, or a close.
                + 1
                + capacity * 2
                // Whether the host changed, and to whom (G0104).
                + 1
                + 4
                + 1;
    }

    /** Starts listening for peers; returns the port it got. */
    public int listen(int firstPort, int search) {
        int port = transport.listen(firstPort, search);
        log.info("sequencer", "event=listening port=" + port + " build=" + build + " capacity=" + capacity);
        return port;
    }

    public long hostKey() {
        return hostKey;
    }

    /**
     * Says whether the host's link gone past its window hands the session to the peer longest in the room (G0104), or
     * ends it. Off unless a room on a relay turns it on: a session on the host's own machine ends with him.
     */
    public void handsOver(boolean on) {
        handsOver = on;
    }

    /** How long a dropped machine's seats are kept, in nanoseconds: the one reading of the key, told to whoever asks. */
    public long rejoinWindowNanos() {
        return windowNanos;
    }

    /** Whether a peer has been welcomed as the host: false until its hello, true for good. Safe from any thread. */
    public boolean hostWelcomed() {
        return hostWelcomed;
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

    /** The latest any tick was sealed after its time, in nanoseconds, since this sequencer began; safe from any thread. */
    public long sealLateMostNanos() {
        return lateMostEverNanos;
    }

    /**
     * When this session last had anything but acknowledgements said to it, on the clock that pumps it; zero
     * until then. Safe from any thread.
     */
    public long lastActiveNanos() {
        return activeNanos;
    }

    /** How many ticks have been sealed with this peer's input repeated because it came too late, in all. */
    int repeatedFor(int peerId) {
        Peer peer = peerWithId(peerId);
        return peer == null ? 0 : peer.repeated;
    }

    /** When the next tick is due, for the thread driving this to sleep until. */
    public long nextDeadlineNanos() {
        return started && !paused ? nextSealNanos : now + (started ? tickNanos : BRIEFING_NANOS);
    }

    /** Hears every peer, seals every tick that is due, and sends every peer what it has not got yet. */
    public void pump(long nowNanos) {
        now = nowNanos;
        if (ended) {
            return;
        }
        transport.poll(listener);
        giveUpOnThoseGone();
        if (ended) {
            return;
        }
        askForSnapshots();
        int sealedNow = 0;
        if (started && !paused) {
            while (nowNanos - nextSealNanos >= 0 && sealedNow < pacing.catchUpTicks()) {
                lateMostNanos = Math.max(lateMostNanos, nowNanos - nextSealNanos);
                lateMostEverNanos = Math.max(lateMostEverNanos, nowNanos - nextSealNanos);
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
        if (ended) {
            return;
        }
        ended = true;
        out.reset().putByte(Msg.ENDED).putString(reason);
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.id >= 0) {
                transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
            }
            transport.disconnect(peer.link, reason);
        }
        held.clear();
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
            // A new world has nobody in it who is away: a slot nobody holds is simply empty there - but a
            // dormant machine's commanders join it away, as they left the old one.
            Arrays.fill(away, false);
            Arrays.fill(awayWanted, false);
            Arrays.fill(backWanted, false);
            for (int slot = 0; slot < capacity; slot++) {
                Peer holder = owner[slot] < 0 ? null : peerWithId(owner[slot]);
                if (holder == null || holder.state == PeerState.DORMANT) {
                    goAway(slot);
                }
            }
            restartWanted = false;
            log.info("sequencer", "event=restart tick=" + tick);
        }
        int index = tick & (AHEAD - 1);
        for (int slot = 0; slot < capacity; slot++) {
            if (awayWanted[slot]) {
                sealing.away(slot);
                awayWanted[slot] = false;
            }
            if (backWanted[slot]) {
                sealing.back(slot);
                backWanted[slot] = false;
            }
            if (owner[slot] < 0) {
                continue;
            }
            if (aheadTick[slot][index] == tick) {
                System.arraycopy(ahead[slot][index], 0, lastHeld[slot], 0, Bundle.HELD_BYTES);
            } else {
                Peer peer = peerWithId(owner[slot]);
                if (peer != null && peer.state != PeerState.DORMANT) {
                    peer.repeats++;
                    peer.repeated++;
                }
            }
            sealing.hold(slot, lastHeld[slot], 0);
            if (joinWanted[slot]) {
                sealing.join(slot, owner[slot]);
                joinWanted[slot] = false;
            }
        }
        if (invulnerabilityChanged) {
            sealing.switchInvulnerability(invulnerable);
            invulnerabilityChanged = false;
        }
        if (hostChangePeer >= 0) {
            sealing.hostChange(hostChangePeer, hostChangeSlot);
            hostChangePeer = -1;
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
            if (peer.state == PeerState.LOADING && sealed - peer.acked >= history.length) {
                dormant(peer);
                continue;
            }
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
                            "event=fell-behind peer=" + peer.id + " acked=" + peer.acked + " sealed=" + sealed
                                    + " generation=" + peer.generation + " silentMs="
                                    + millis(now - peer.lastHeardNanos));
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
     * the same oldest ticks while the newest wait - and only to a peer still answering: one that has said
     * nothing for longer than any round trip cannot say what it is missing, and was sent 72 kB a second
     * of it in the 2026-10-01 playtest. Its first word again brings the resends back.
     */
    private int writeBundles(Peer peer) {
        int newestFrom = Math.max(peer.acked + 1, sealed - pacing.redundantTicks() + 1);
        int newestBytes = 0;
        for (int tick = newestFrom; tick <= sealed; tick++) {
            newestBytes += historyLength[tick % history.length];
        }
        long resendAfter = peer.roundTripNanos == 0L ? 0L : peer.roundTripNanos + 2 * tickNanos;
        int count = 0;
        boolean answering = now - peer.lastHeardNanos <= longestRoundTripNanos;
        // Room is reckoned on whole bundles: one written as it changed from the one before is never longer.
        int previous = -1;
        for (int tick = peer.acked + 1; answering && tick < newestFrom && count < 255; tick++) {
            int at = tick % history.length;
            if (peer.sentTick[at] == tick && now - peer.sentNanos[at] < resendAfter) {
                continue;
            }
            if (out.size() + historyLength[at] + newestBytes > maxDatagramBytes) {
                break;
            }
            previous = writeBundle(peer, tick, previous);
            count++;
        }
        for (int tick = newestFrom; tick <= sealed && count < 255; tick++) {
            if (out.size() + historyLength[tick % history.length] > maxDatagramBytes) {
                break;
            }
            previous = writeBundle(peer, tick, previous);
            count++;
        }
        return count;
    }

    /** Writes one tick, whole if it is the first in the datagram; returns where it is in the history. */
    private int writeBundle(Peer peer, int tick, int previous) {
        int at = tick % history.length;
        if (previous < 0) {
            out.putRaw(history[at], 0, historyLength[at]);
        } else {
            Bundle.writeChanged(out, history[at], historyLength[at], history[previous]);
        }
        if (peer.sentTick[at] != tick) {
            peer.sentTick[at] = tick;
            peer.firstSentNanos[at] = now;
        }
        peer.sentNanos[at] = now;
        return at;
    }

    private void askForSnapshots() {
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            if (peer.wantsSnapshot && !peer.snapshotAsked) {
                Peer source = snapshotSource(peer);
                if (source == null) {
                    continue;
                }
                out.reset().putByte(Msg.SNAPSHOT_WANTED).putInt(peer.id);
                transport.send(source.link, Delivery.RELIABLE, out.array(), 0, out.size());
                peer.snapshotAsked = true;
                peer.snapshotFrom = source.id;
                log.debug("sequencer", "event=snapshot-asked for=" + peer.id + " from=" + source.id);
            }
        }
    }

    /**
     * Who takes the snapshot a peer is to drop in with: the host, once it is playing. When the host's link is down
     * within its window (G0105), or it is the one coming back, any machine that is playing - every world is the
     * same at a tick - and nobody while there is none.
     */
    private @Nullable Peer snapshotSource(Peer target) {
        Peer host = host();
        if (host != null && host != target) {
            return host.state == PeerState.PLAYING ? host : null;
        }
        for (int i = 0; i < peers.size(); i++) {
            Peer other = peers.get(i);
            if (other != target && other.id >= 0 && other.state == PeerState.PLAYING) {
                return other;
            }
        }
        return null;
    }

    private void heal(Peer peer) {
        peer.state = PeerState.JOINING;
        peer.wantsSnapshot = true;
        peer.snapshotAsked = false;
        peer.snapshotFrom = -1;
    }

    /**
     * The peer has built the world it was sent - its first input about it says so - and from here is sent
     * every tick since, oldest first. Nothing was sent while it loaded: ticks it could not have placed
     * would only have been thrown away, and waited for again (the second Hamachi playtest).
     */
    private void loaded(Peer peer) {
        if (peer.state == PeerState.DORMANT || peer.returning) {
            for (int slot = 0; slot < capacity; slot++) {
                if (owner[slot] == peer.id) {
                    comeBack(slot);
                }
            }
            log.info(
                    "sequencer",
                    "event=" + (peer.returning ? "back" : "awake") + " peer=" + peer.id + " machine=" + peer.machine);
            peer.returning = false;
        }
        peer.state = PeerState.PLAYING;
        peer.catchUpTo = sealed;
        peer.newestHeard = -1;
        log.info(
                "sequencer",
                "event=loaded peer=" + peer.id + " generation=" + peer.generation + " ms="
                        + millis(now - peer.snapshotSentNanos) + " behind=" + (sealed - peer.acked));
    }

    /**
     * A peer that has not built the world it was sent before the ticks since it fell out of the history
     * can never be brought up to date from it, and another snapshot would take as long again. It is kept,
     * and left alone: sent nothing, its commanders standing idle and shown away as for a dropped link
     * (G0052), so it costs nobody else anything. Its game has stopped - a window held by its title bar, a
     * console clicked into - or its link is too slow to carry a world; when it builds the one it was sent
     * and says so, it is back, falls behind, and is sent a fresh one. The 2026-10-01 playtest let such a
     * machine go after 20 s, blaming a 3 ms link.
     */
    private void dormant(Peer peer) {
        peer.state = PeerState.DORMANT;
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] == peer.id) {
                Arrays.fill(lastHeld[slot], (byte) 0);
                Arrays.fill(aheadTick[slot], -1);
                if (!joinWanted[slot]) {
                    goAway(slot);
                }
            }
        }
        log.warn(
                "sequencer",
                "event=dormant peer=" + peer.id + " machine=" + peer.machine + " generation=" + peer.generation
                        + " ms=" + millis(now - peer.snapshotSentNanos) + " silentMs="
                        + millis(now - peer.lastHeardNanos));
    }

    /** The slot's commander stands idle and the draft rotation passes him by (G0052), from the next tick. */
    private void goAway(int slot) {
        away[slot] = true;
        awayWanted[slot] = true;
        backWanted[slot] = false;
    }

    /** Somebody is at this commander again. If his going away has not been sealed yet, nothing was. */
    private void comeBack(int slot) {
        if (!away[slot]) {
            return;
        }
        away[slot] = false;
        if (awayWanted[slot]) {
            awayWanted[slot] = false;
        } else {
            backWanted[slot] = true;
        }
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }

    private void stats() {
        if (statsNanos <= 0 || now - nextStatsNanos < 0 || !started || paused) {
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
        if (peer.id < 0 && type != Msg.HELLO && type != Msg.REJOIN) {
            throw new WireException("message " + type + " before hello");
        }
        if (type != Msg.INPUT && type != Msg.CHECKSUM) {
            activeNanos = now;
        }
        switch (type) {
            case Msg.HELLO -> hello(peer);
            case Msg.REJOIN -> rejoin(peer);
            case Msg.LEAVE -> {
                in.expectEnd();
                peer.leaving = true;
            }
            case Msg.INPUT -> input(peer);
            case Msg.CHECKSUM -> checksum(peer);
            case Msg.SNAPSHOT_TAKEN -> snapshotTaken(peer);
            case Msg.SEAT -> seat(peer);
            case Msg.PAUSE -> pause(peer);
            case Msg.RESTART -> restart(peer);
            case Msg.GOD_MODE -> godMode(peer);
            case Msg.START -> start(peer);
            case Msg.TO_BRIEFING -> toBriefing(peer);
            case Msg.NOTE -> noted(peer);
            default -> throw new WireException("no message " + type);
        }
    }

    private void hello(Peer peer) {
        int protocol = in.getInt();
        if (protocol != Msg.PROTOCOL) {
            // Another protocol's hello may be another shape: refused before any more of it is read.
            if (peer.id < 0) {
                refuse(peer, "protocol " + protocol + ", the host speaks " + Msg.PROTOCOL);
            }
            return;
        }
        String theirBuild = in.getString(Msg.MAX_TEXT);
        long key = in.getLong();
        String machine = in.getString(MachineId.MAX_LENGTH);
        in.expectEnd();
        if (!MachineId.valid(machine)) {
            throw new WireException("a machine id that cannot be logged as it came");
        }
        if (peer.id >= 0) {
            return;
        }
        if (!theirBuild.equals(build) || build.equals(BuildId.UNKNOWN)) {
            refuse(peer, "build " + theirBuild + ", the host runs " + build);
            return;
        }
        peer.id = nextPeerId++;
        peer.machine = machine;
        peer.host = key == hostKey && host() == null && !holdingHost() && !handedOver;
        hostWelcomed |= peer.host;
        // Sixteen bytes only this machine and this sequencer know: what proves a returning link is this one (G0105).
        peer.token = new byte[Msg.TOKEN_BYTES];
        random.nextBytes(peer.token);
        welcome(peer);
        if (peer.host) {
            sendHost(null);
        }
    }

    private boolean holdingHost() {
        for (int i = 0; i < held.size(); i++) {
            if (held.get(i).host) {
                return true;
            }
        }
        return false;
    }

    /** Tells a machine it is in: the welcome, its token with it, the briefing's players and note. */
    private void welcome(Peer peer) {
        byte[] token = peer.token;
        if (token == null) {
            throw new IllegalStateException("a welcome with no token for peer " + peer.id);
        }
        Msg.putWelcome(out, peer.id, capacity, started, session, tickNanos, startBytes, token, windowSeconds);
        transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
        int welcomeBytes = out.size();
        if (started) {
            heal(peer);
        } else {
            peer.state = PeerState.BRIEFING;
        }
        log.info(
                "sequencer",
                "event=welcome peer=" + peer.id + " machine=" + peer.machine + " link=" + peer.link + " host="
                        + peer.host + " started=" + started
                        + " setupBytes=" + startBytes.length + " wireBytes=" + welcomeBytes);
        sendPlayers();
        sendNote(peer);
        if (!peer.host) {
            sendHost(peer);
        }
    }

    /**
     * A machine whose link dropped asks to be put back (G0105). The token is the proof: it names a machine the
     * sequencer holds - dropped within its window, or still linked on a link the machine has lost without the other
     * end knowing - and a link that shows one is that machine. Its old link, if any, is closed. Its seats were kept;
     * a world comes by the drop-in path, and its commanders come back when it has built it.
     */
    private void rejoin(Peer peer) {
        int protocol = in.getInt();
        if (protocol != Msg.PROTOCOL) {
            if (peer.id < 0) {
                refuse(peer, "protocol " + protocol + ", the host speaks " + Msg.PROTOCOL);
            }
            return;
        }
        String theirBuild = in.getString(Msg.MAX_TEXT);
        String machine = in.getString(MachineId.MAX_LENGTH);
        byte[] token = in.getBytes(Msg.TOKEN_BYTES);
        in.expectEnd();
        if (!MachineId.valid(machine)) {
            throw new WireException("a machine id that cannot be logged as it came");
        }
        if (peer.id >= 0) {
            return;
        }
        if (!theirBuild.equals(build) || build.equals(BuildId.UNKNOWN)) {
            refuse(peer, "build " + theirBuild + ", the host runs " + build);
            return;
        }
        Peer old = holderOf(token);
        if (old == null) {
            log.info("sequencer", "event=rejoin-refused link=" + peer.link + " machine=" + machine);
            refuse(peer, GIVEN_UP);
            return;
        }
        if (!held.remove(old)) {
            peers.remove(old);
            transport.disconnect(old.link, "replaced by a new link");
            for (int i = 0; i < peers.size(); i++) {
                if (peers.get(i).snapshotFrom == old.id) {
                    peers.get(i).snapshotAsked = false;
                }
            }
        }
        peer.id = old.id;
        peer.machine = old.machine;
        peer.host = old.host;
        peer.token = old.token;
        peer.returning = true;
        for (int seat = 0; seat < capacity; seat++) {
            int slot = old.slotOfSeat[seat];
            peer.slotOfSeat[seat] = slot >= 0 && owner[slot] == old.id ? slot : -1;
        }
        for (int slot = 0; slot < capacity; slot++) {
            if (started && owner[slot] == peer.id) {
                // Its world is thrown away and comes again from a snapshot: its commanders wait idle till then.
                Arrays.fill(lastHeld[slot], (byte) 0);
                Arrays.fill(aheadTick[slot], -1);
                if (!away[slot]) {
                    goAway(slot);
                }
            }
        }
        welcome(peer);
        if (peer.host) {
            sendHost(peer);
        }
        for (int seat = 0; seat < capacity; seat++) {
            if (peer.slotOfSeat[seat] >= 0) {
                out.reset().putByte(Msg.SLOT).putByte(seat).putByte(peer.slotOfSeat[seat]);
                transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
            }
        }
        log.info(
                "sequencer",
                "event=rejoined peer=" + peer.id + " machine=" + peer.machine + " link=" + peer.link + " host="
                        + peer.host);
    }

    private @Nullable Peer holderOf(byte[] token) {
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.token != null && MessageDigest.isEqual(each.token, token)) {
                return each;
            }
        }
        for (int i = 0; i < held.size(); i++) {
            Peer each = held.get(i);
            if (each.token != null && MessageDigest.isEqual(each.token, token)) {
                return each;
            }
        }
        return null;
    }

    /** The seats of a machine that did not come back in its window are free, as a pad unplugged for good (G0105). */
    private void giveUpOnThoseGone() {
        for (int i = held.size() - 1; i >= 0; i--) {
            Peer gone = held.get(i);
            if (now - gone.heldUntilNanos < 0) {
                continue;
            }
            held.remove(i);
            for (int slot = 0; slot < capacity; slot++) {
                if (owner[slot] == gone.id) {
                    owner[slot] = -1;
                    joinWanted[slot] = false;
                    Arrays.fill(lastHeld[slot], (byte) 0);
                    holderMachine[slot] = "";
                }
            }
            log.info("sequencer", "event=gave-up peer=" + gone.id + " machine=" + gone.machine + " host=" + gone.host);
            if (gone.host && !handOver(gone)) {
                end("the host left the game");
                return;
            }
            sendPlayers();
        }
    }

    /**
     * The host's window is over and he is not back (G0104): the peer longest in the room - the lowest number among
     * those linked, one that can already serve a world before one that cannot - is the host from here, and every
     * machine is told on one tick (sealed, while a mission runs) or at once (nothing is sealed to carry it). Nothing is
     * copied: every world is the same. False when the session does not hand over, or nobody is linked to take it.
     */
    private boolean handOver(Peer gone) {
        if (!handsOver) {
            return false;
        }
        Peer next = null;
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id < 0 || each.leaving || each == gone) {
                continue;
            }
            if (next == null
                    || (serves(each) && !serves(next))
                    || (serves(each) == serves(next) && each.id < next.id)) {
                next = each;
            }
        }
        if (next == null) {
            return false;
        }
        gone.host = false;
        next.host = true;
        handedOver = true;
        int slot = lowestSlotOf(next.id);
        log.info(
                "sequencer",
                "event=host-changed from=" + gone.id + " to=" + next.id + " machine=" + next.machine + " slot=" + slot);
        if (started && !paused) {
            hostChangePeer = next.id;
            hostChangeSlot = slot < 0 ? Msg.NO_SLOT : slot;
        }
        sendHost(null);
        return true;
    }

    /** A hand-over no tick has carried yet goes to every machine now: the session paused or went back to its briefing. */
    private void flushHostChange() {
        if (hostChangePeer >= 0) {
            hostChangePeer = -1;
            sendHost(null);
        }
    }

    /** Whether the peer has a world a joiner can be sent: it is playing, or there is no world yet to send. */
    private boolean serves(Peer peer) {
        return peer.state == PeerState.PLAYING || (!started && peer.state == PeerState.BRIEFING);
    }

    /** The lowest slot this peer holds, or -1. */
    private int lowestSlotOf(int peerId) {
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] == peerId) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Tells who the host is: one welcomed peer, or all of them - except those playing a mission that is running, who
     * hear it in the tick the sequencer seals (G0104). Nothing is sent while there is no host.
     */
    private void sendHost(@Nullable Peer only) {
        Peer host = host();
        if (host == null) {
            return;
        }
        int slot = lowestSlotOf(host.id);
        Msg.putHost(out.reset(), host.id, slot);
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id < 0 || (only != null && each != only)) {
                continue;
            }
            if (only == null && hostChangePeer >= 0 && each.state == PeerState.PLAYING) {
                continue;
            }
            transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
        }
    }

    /**
     * The host starts the mission (G0101): every machine on the briefing is told, with the setup as the host sent
     * it - bytes this class never reads - and the first tick seals with every slot taken so far joining it. A
     * slot whose machine has gone is put on the field away, as a mission leaves a commander (G0038).
     */
    private void start(Peer peer) {
        long nanos = in.getLong();
        byte[] setup = in.getPacked(Msg.MAX_START);
        in.expectEnd();
        if (!peer.host || started) {
            log.warn("sequencer", "event=refused-start peer=" + peer.id + " started=" + started);
            return;
        }
        if (nanos <= 0) {
            throw new WireException("a tick of " + nanos + " ns");
        }
        tickNanos = nanos;
        longestRoundTripNanos = 2L * pacing.leadMaxTicks() * tickNanos;
        startBytes = setup;
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] >= 0) {
                joinWanted[slot] = true;
                if (peerWithId(owner[slot]) == null) {
                    goAway(slot);
                }
            }
        }
        started = true;
        nextSealNanos = now + tickNanos;
        nextStatsNanos = now + statsNanos;
        out.reset().putByte(Msg.STARTED).putLong(tickNanos).putPacked(setup, 0, setup.length);
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.state == PeerState.BRIEFING) {
                transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
                each.state = PeerState.PLAYING;
                each.inputGapNanos = tickNanos;
                each.catchUpTo = -1;
            }
        }
        log.info("sequencer", "event=started peer=" + peer.id + " tickNs=" + tickNanos + " setupBytes=" + setup.length);
    }

    /**
     * The host takes the session back to its briefing (G0101): nothing more is sealed, every machine drops its world,
     * and the slots stay with the machines that hold them. Each peer's generation moves on, so what was in flight from
     * the mission is told from what comes of the next.
     */
    private void toBriefing(Peer peer) {
        in.expectEnd();
        if (!peer.host || !started) {
            log.warn("sequencer", "event=refused-briefing peer=" + peer.id + " started=" + started);
            return;
        }
        started = false;
        paused = false;
        pauseReason = Msg.PAUSE_BY_PLAYER;
        flushHostChange();
        sealed = -1;
        restartWanted = false;
        invulnerable = false;
        invulnerabilityChanged = false;
        dumpedTick = -1;
        lateMostNanos = 0L;
        startBytes = new byte[0];
        Arrays.fill(historyTick, -1);
        Arrays.fill(historyLength, 0);
        Arrays.fill(hostHashTick, -1);
        for (int slot = 0; slot < capacity; slot++) {
            away[slot] = false;
            awayWanted[slot] = false;
            backWanted[slot] = false;
            joinWanted[slot] = false;
            Arrays.fill(lastHeld[slot], (byte) 0);
            Arrays.fill(aheadTick[slot], -1);
        }
        out.reset().putByte(Msg.BRIEFING);
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id < 0) {
                continue;
            }
            each.state = PeerState.BRIEFING;
            each.generation++;
            each.acked = -1;
            each.eventCount = 0;
            each.eventsApplied = 0;
            each.wantsSnapshot = false;
            each.snapshotAsked = false;
            each.catchUpTo = -1;
            each.newestHeard = -1;
            each.margin = Msg.NO_MARGIN;
            each.roundTripNanos = 0L;
            Arrays.fill(each.sentTick, -1);
            Arrays.fill(each.waitingTick, -1);
            transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
        }
        log.info("sequencer", "event=briefing peer=" + peer.id);
        sendPlayers();
    }

    /**
     * Tells every welcomed machine who holds which slot, while the session is on its briefing: per slot held, its
     * number, whether the holder is the host and whether its link is up, and the machine it plays from.
     */
    private void sendPlayers() {
        if (started) {
            return;
        }
        out.reset().putByte(Msg.PLAYERS);
        int count = 0;
        for (int slot = 0; slot < capacity; slot++) {
            count += owner[slot] >= 0 ? 1 : 0;
        }
        out.putByte(count);
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] < 0) {
                continue;
            }
            Peer holder = peerWithId(owner[slot]);
            out.putByte(slot)
                    .putByte((holder != null && holder.host ? Msg.PLAYER_HOST : 0)
                            | (holder != null ? Msg.PLAYER_ONLINE : 0))
                    .putString(holderMachine[slot]);
        }
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id >= 0) {
                transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
            }
        }
    }

    /** The host's note for the briefing, passed to every machine on it, as it is. */
    private void noted(Peer peer) {
        byte[] heard = in.getBytes(Msg.MAX_NOTE);
        in.expectEnd();
        if (!peer.host) {
            return;
        }
        note = heard;
        sendNote(null);
    }

    /** The note to one welcomed peer, or to all of them. */
    private void sendNote(@Nullable Peer only) {
        out.reset().putByte(Msg.NOTED).putBytes(note);
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id >= 0 && (only == null || each == only)) {
                transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
            }
        }
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
        boolean current = generation == peer.generation;
        boolean spike = heardAfterSpike(peer);
        if (current && (peer.state == PeerState.LOADING || peer.state == PeerState.DORMANT)) {
            loaded(peer);
        }
        if (current && acked > peer.acked && acked <= sealed) {
            peer.acked = acked;
            peer.measure(acked, now);
            if (peer.catchUpTo >= 0 && acked >= peer.catchUpTo) {
                log.info(
                        "sequencer",
                        "event=caught-up peer=" + peer.id + " generation=" + generation + " ms="
                                + millis(now - peer.snapshotSentNanos));
                peer.catchUpTo = -1;
            }
        }
        // Only input about the world it was sent: a peer still playing the one it is being healed from
        // tags ticks long gone, and would drive its lead to the ceiling for nothing. And only a tag heard
        // for the first time: how early it came is known then, and a peer with nothing new to tag - starved
        // by a gap in the network - says it again with every input, later each tick, though nothing it
        // sent was late (the third playtest: a 180 ms gap raised a lead from 8 to 18).
        if (current && newestTag > peer.newestHeard) {
            peer.newestHeard = newestTag;
            // How many ticks before its seal the newest input arrived: what the peer's lead is steered by -
            // unless a spike held it, which the lead does not chase (heardAfterSpike).
            int margin = newestTag - sealed;
            if (!spike) {
                peer.margin = peer.margin == Msg.NO_MARGIN ? margin : Math.min(peer.margin, margin);
            }
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
                in.copyTo(ahead[slot][index], 0, Bundle.HELD_BYTES);
                aheadTick[slot][index] = tick;
                if (!Arrays.equals(ahead[slot][index], lastHeld[slot])) {
                    activeNanos = now;
                }
            }
        }
        int eventCount = in.getByte();
        if (eventCount > 0) {
            activeNanos = now;
        }
        for (int i = 0; i < eventCount; i++) {
            int seq = in.getInt();
            int slot = in.getByte();
            int type = in.getByte();
            int value = in.getInt();
            int extra = in.getByte();
            if (slot >= capacity) {
                throw new WireException("slot " + slot + " in a session of " + capacity);
            }
            // Only what fits a bundle is checked; what it means is for the peers (ADR 0023).
            Bundle.checkEvent(type, value, extra);
            if (current) {
                peer.hold(seq, slot, type, value, extra);
            }
        }
        in.expectEnd();
    }

    /**
     * Whether this input came in a burst after the peer had been silent for spikeTicks longer than its
     * usual gap: held by a link that stopped for a moment - a relay, a busy radio - and let go at once.
     * Late, and its ticks are sealed with the peer's input repeated, but a lead raised to cover every spike
     * is that much input delay all the time: on 2026-10-01, 120 ms once a second took a lead from 8 ticks
     * to 14. Everything heard in the pump the burst began in belongs to it.
     */
    private boolean heardAfterSpike(Peer peer) {
        long gap = now - peer.lastInputNanos;
        boolean first = peer.lastInputNanos == 0L;
        peer.lastInputNanos = now;
        if (first || pacing.spikeTicks() == 0) {
            return false;
        }
        if (now == peer.spikeHeardNanos) {
            return true;
        }
        long allowed = peer.inputGapNanos + pacing.spikeTicks() * tickNanos;
        // The usual gap follows the peer's frames, a long one counting no further than a spike's edge.
        peer.inputGapNanos = (7 * peer.inputGapNanos + Math.min(gap, allowed)) / 8;
        if (gap > allowed) {
            peer.spikeHeardNanos = now;
            return true;
        }
        return false;
    }

    private void checksum(Peer peer) {
        int generation = in.getInt();
        int tick = in.getInt();
        long hash = in.getLong();
        in.expectEnd();
        if (peer.host) {
            int at = (tick / session.checksumTicks()) % HASHES;
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
        int at = (tick / session.checksumTicks()) % HASHES;
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
        if (length < 0 || length > in.remaining() || length > Transport.MAX_MESSAGE_BYTES - Msg.SNAPSHOT_HEADER) {
            throw new WireException("a snapshot of " + length + " bytes");
        }
        int start = in.position();
        in.skip(length);
        in.expectEnd();
        Peer target = peerWithId(forPeer);
        if (target == null || !target.wantsSnapshot || target.snapshotFrom != from.id) {
            return;
        }
        if (tick > sealed || sealed - tick >= history.length - 1) {
            target.snapshotAsked = false;
            return;
        }
        target.generation++;
        out.reset().putByte(Msg.SNAPSHOT).putInt(target.generation).putInt(tick).putPacked(in.array(), start, length);
        transport.send(target.link, Delivery.RELIABLE, out.array(), 0, out.size());
        target.acked = tick;
        target.state = PeerState.LOADING;
        target.snapshotSentNanos = now;
        target.wantsSnapshot = false;
        target.snapshotAsked = false;
        log.info(
                "sequencer",
                "event=snapshot-sent peer=" + target.id + " tick=" + tick + " bytes=" + length + " wireBytes="
                        + out.size() + " generation=" + target.generation);
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
            for (int kept = 0; !started && kept < capacity && slot < 0; kept++) {
                // A machine that blinked on the briefing takes its slot back rather than another beside it.
                if (owner[kept] >= 0 && peerWithId(owner[kept]) == null && holderMachine[kept].equals(peer.machine)) {
                    slot = kept;
                }
            }
            for (int free = hostsKeyboard ? 0 : 1; free < capacity && slot < 0; free++) {
                if (owner[free] < 0) {
                    slot = free;
                }
            }
            if (slot >= 0) {
                owner[slot] = peer.id;
                holderMachine[slot] = peer.machine;
                Arrays.fill(lastHeld[slot], (byte) 0);
                Arrays.fill(aheadTick[slot], -1);
                joinWanted[slot] = true;
                peer.slotOfSeat[seat] = slot;
                comeBack(slot);
                if (paused && pauseReason == Msg.PAUSE_FOR_DRAFT) {
                    // A second player makes the game co-op, and co-op never pauses (G0049). Nothing is sealed
                    // while paused, so the world would never hear he had come - the pause lifts here.
                    setPaused(false, Msg.PAUSE_FOR_DRAFT, peer.id);
                }
            }
            log.info("sequencer", "event=seated peer=" + peer.id + " seat=" + seat + " slot=" + slot);
        }
        out.reset().putByte(Msg.SLOT).putByte(seat).putByte(slot < 0 ? Msg.NO_SLOT : slot);
        transport.send(peer.link, Delivery.RELIABLE, out.array(), 0, out.size());
        sendPlayers();
    }

    private void pause(Peer peer) {
        boolean want = in.getByte() != 0;
        int reason = in.getByte();
        in.expectEnd();
        if (reason != Msg.PAUSE_BY_PLAYER && reason != Msg.PAUSE_FOR_DRAFT) {
            throw new WireException("no pause reason " + reason);
        }
        if (want == paused || !started) {
            return;
        }
        setPaused(want, reason, peer.id);
    }

    /** Pauses or resumes, and tells every machine who asked and why. */
    private void setPaused(boolean on, int reason, int by) {
        paused = on;
        pauseReason = reason;
        flushHostChange();
        if (!paused) {
            nextSealNanos = now + tickNanos;
        }
        log.info(
                "sequencer",
                "event=" + (paused ? "paused" : "resumed") + " by=" + by + " tick=" + sealed
                        + (reason == Msg.PAUSE_FOR_DRAFT ? " reason=draft" : ""));
        out.reset()
                .putByte(Msg.PAUSED)
                .putByte(paused ? 1 : 0)
                .putInt(sealed)
                .putInt(by)
                .putByte(reason);
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.id >= 0) {
                transport.send(each.link, Delivery.RELIABLE, out.array(), 0, out.size());
            }
        }
    }

    private void restart(Peer peer) {
        in.expectEnd();
        if (!peer.host || !started) {
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
        for (int i = 0; i < peers.size(); i++) {
            Peer each = peers.get(i);
            if (each.snapshotFrom == peer.id && peer.id >= 0) {
                each.snapshotAsked = false;
            }
        }
        if (peer.host && peer.leaving) {
            // The host chose to go: nothing to wait for, and nobody to hand the session to yet (G0038).
            end("the host left the game");
            return;
        }
        boolean keep = peer.id >= 0 && !peer.leaving && windowNanos > 0 && peer.token != null;
        if (keep) {
            // Its seats are kept for the window and its commanders stand idle (G0052): it may be back (G0105).
            peer.heldUntilNanos = now + windowNanos;
            held.add(peer);
            for (int slot = 0; started && slot < capacity; slot++) {
                if (owner[slot] == peer.id) {
                    Arrays.fill(lastHeld[slot], (byte) 0);
                    Arrays.fill(aheadTick[slot], -1);
                    if (!away[slot]) {
                        goAway(slot);
                    }
                }
            }
            log.info(
                    "sequencer",
                    "event=held peer=" + peer.id + " machine=" + peer.machine + " link=" + peer.link + " host="
                            + peer.host + " seconds=" + windowSeconds + " reason=\"" + reason + "\"");
            if (!started) {
                sendPlayers();
            }
            return;
        }
        if (peer.host && peer.id >= 0) {
            // No window to wait out (a key of zero): the hand-over is now, if this session has one (G0104).
            handOver(peer);
        }
        if (!started) {
            // On the briefing a slot stays with the machine that took it, shown offline: it is put on the field
            // away, as a mission leaves a commander (G0038), if the host starts without it back.
            log.info(
                    "sequencer",
                    "event=left peer=" + peer.id + " machine=" + peer.machine + " link=" + peer.link + " reason=\""
                            + reason + "\"");
            sendPlayers();
            return;
        }
        for (int slot = 0; slot < capacity; slot++) {
            if (owner[slot] == peer.id && peer.id >= 0) {
                owner[slot] = -1;
                Arrays.fill(lastHeld[slot], (byte) 0);
                // The unplugged pad of G0038: the commander stays, and the draft rotation passes him by.
                if (!joinWanted[slot]) {
                    goAway(slot);
                }
                joinWanted[slot] = false;
            }
        }
        log.info(
                "sequencer",
                "event=left peer=" + peer.id + " machine=" + peer.machine + " link=" + peer.link + " reason=\"" + reason
                        + "\"");
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
        /** Linked, not yet welcomed. */
        HELLO,
        /** Welcomed to a session still on its briefing: nothing is sent it until the host starts the mission. */
        BRIEFING,
        /** Waiting for the host to snapshot its world: a drop-in, or a heal. */
        JOINING,
        /** Sent a snapshot and building it; sent no ticks until its first input about that world. */
        LOADING,
        /** Did not build its snapshot before the history moved past it: left alone until it does. */
        DORMANT,
        PLAYING
    }

    /** One link, and what the sequencer knows of the peer at the other end. */
    private final class Peer {
        final int link;
        final int[] slotOfSeat = new int[capacity];
        final int[] inputSlots = new int[capacity];
        int id = -1;

        /** The computer it plays from, as its hello said: {@link MachineId}. */
        String machine = "-";

        boolean host;

        /** What proves a link is this machine's (G0105): sixteen bytes, set at its welcome, told to nobody else. */
        byte @Nullable [] token;

        /** It said it is leaving on purpose: no window for its seats. */
        boolean leaving;

        /** It came back by its token and is waiting for a world: its commanders stand away until it has built it. */
        boolean returning;

        /** When a dropped machine's seats are given up, on the sequencer's clock; set while it is held. */
        long heldUntilNanos;

        /** The peer asked for the snapshot this one is waiting for, or -1. */
        int snapshotFrom = -1;

        PeerState state = PeerState.HELLO;
        int acked = -1;

        /** How many worlds it has been sent: one for its drop-in, one more for each heal. */
        int generation;

        int margin = Msg.NO_MARGIN;

        /** Ticks sealed with its input repeated: since the last stats line, and in all. */
        int repeats;

        int repeated;
        long lastSentNanos;
        boolean wantsSnapshot;
        boolean snapshotAsked;

        /** When its last snapshot went, for how long loading and catching up took. */
        long snapshotSentNanos;

        /** When it last said anything: input, a checksum, anything. Its link's heartbeats do not count. */
        long lastHeardNanos;

        /** When its last input came, its usual gap between two, and when a spike's burst of them came. */
        long lastInputNanos;

        long inputGapNanos = tickNanos;
        long spikeHeardNanos = -1L;

        /** The newest tag it has said, in the world it plays: a tag is judged when first heard. */
        int newestHeard = -1;

        /** The tick sealed when it loaded: it has caught up once it acknowledges that, -1 once it has. */
        int catchUpTo = -1;

        /** Input events heard but not yet sealed: numbered, applied in number order, once each. */
        int eventsApplied;

        final int[] eventSeq = new int[32];
        final int[] eventSlot = new int[32];
        final int[] eventType = new int[32];
        final int[] eventValue = new int[32];
        final int[] eventExtra = new int[32];
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
            // Clamped to the longest round trip: anything longer is the peer not listening (loading the world
            // it was sent, stalled), and believed, it held a gap's resends back until the peer fell out of
            // the history, every time: the second Hamachi playtest read 9.9 s over a 60 ms link.
            long sample = Math.min(Math.max(1L, nowNanos - firstSentNanos[at]), longestRoundTripNanos);
            roundTripNanos = roundTripNanos == 0L ? sample : (3 * roundTripNanos + sample) / 4;
        }

        void hold(int seq, int slot, int type, int value, int extra) {
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
            eventType[eventCount] = type;
            eventValue[eventCount] = value;
            eventExtra[eventCount] = extra;
            eventCount++;
        }

        /**
         * Seals every event that is next in line, one of each type per slot per tick; a second waits for the
         * next tick, and so does everything after it, so they land in the order they were given.
         */
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
                    if (bundle.hasEvent(slot, eventType[next])) {
                        return;
                    }
                    bundle.event(slot, eventType[next], eventValue[next], eventExtra[next]);
                }
                eventsApplied++;
                eventCount--;
                eventSeq[next] = eventSeq[eventCount];
                eventSlot[next] = eventSlot[eventCount];
                eventType[next] = eventType[eventCount];
                eventValue[next] = eventValue[eventCount];
                eventExtra[next] = eventExtra[eventCount];
            }
        }

        void waitFor(int tick, long hash) {
            int at = (tick / session.checksumTicks()) % HASHES;
            waitingTick[at] = tick;
            waitingHash[at] = hash;
        }

        void compareWaiting(int tick, long reference) {
            int at = (tick / session.checksumTicks()) % HASHES;
            if (waitingTick[at] == tick) {
                waitingTick[at] = -1;
                compare(this, tick, reference, waitingHash[at]);
            }
        }
    }

    private final class Listener implements TransportListener {
        @Override
        public void opened(int link, String remote) {
            Peer peer = new Peer(link);
            peer.lastHeardNanos = now;
            peers.add(peer);
            log.info("sequencer", "event=link link=" + link + " remote=" + remote);
        }

        @Override
        public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
            Peer peer = onLink(link);
            if (peer == null) {
                return;
            }
            peer.lastHeardNanos = now;
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
