package pl.mzebrows.pwt.relay;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.session.Sequencer;
import pl.mzebrows.pwt.net.session.SequencerThread;

/**
 * The relay's record of one session (G0103): its code, its password if it has one, the build it runs and
 * its links. The password lives here, in memory, and nowhere else - no {@code toString}, no log line.
 * And its sequencer (ADR 0023), over a {@link RoomTransport} that is this room's view of the relay's links: a
 * {@link SequencerThread} in the process, a sequencer the relay pumps in a test. The host's link is the one that
 * opened the room; when it goes the room is over.
 */
final class Room {

    private final RoomCode code;
    private final @Nullable String password;
    private final String buildId;
    private final String buildVersion;
    private final long hostKey;
    private final byte[] sessionConfig;
    private final long openedAtNanos;
    private final Set<Integer> links = new LinkedHashSet<>();
    private final String openedBy;
    private final RoomTransport view = new RoomTransport();
    private @Nullable Sequencer sequencer;
    private @Nullable SequencerThread thread;
    private int hostLink = -1;
    private long hostGoneAtNanos = -1L;
    private boolean sealLateWarned;
    private boolean ended;
    private int wrongPasswords;
    private long wrongWindowStartNanos;
    private long lockedUntilNanos;

    Room(
            RoomCode code,
            @Nullable String password,
            String buildId,
            String buildVersion,
            long hostKey,
            byte[] sessionConfig,
            long openedAtNanos,
            String openedBy) {
        this.code = code;
        this.password = password;
        this.buildId = buildId;
        this.buildVersion = buildVersion;
        this.hostKey = hostKey;
        this.sessionConfig = sessionConfig.clone();
        this.openedAtNanos = openedAtNanos;
        this.openedBy = openedBy;
    }

    /** Gives the room its sequencer, built over {@link #view()}, and says which link is the host's. */
    void sequence(Sequencer built, @Nullable SequencerThread running, int host) {
        this.sequencer = built;
        this.thread = running;
        this.hostLink = host;
    }

    RoomTransport view() {
        return view;
    }

    Sequencer sequencer() {
        Sequencer built = sequencer;
        if (built == null) {
            throw new IllegalStateException("room " + code + " has no sequencer yet");
        }
        return built;
    }

    @Nullable
    SequencerThread thread() {
        return thread;
    }

    int hostLink() {
        return hostLink;
    }

    /** The host's link closed: from now on a room whose host never said hello is ended when the window has passed. */
    void hostLinkClosed(long now) {
        if (hostGoneAtNanos < 0) {
            hostGoneAtNanos = now;
        }
    }

    /**
     * Whether the host's link closed before its peer was ever welcomed and the rejoin window has passed since: no
     * token was given, so nobody can come back to it, and no sequencer end will say so.
     */
    boolean hostNeverCame(long now, long windowNanos, boolean hostWelcomed) {
        return !hostWelcomed && hostGoneAtNanos >= 0 && now - hostGoneAtNanos >= windowNanos;
    }

    /** Whether the sequencer has been told to end: closed, or ended on its own. */
    boolean ended() {
        return ended;
    }

    void end() {
        ended = true;
    }

    /** Whether the late-seal warning has been given: said once, on the pass. */
    boolean sealLateWarned() {
        return sealLateWarned;
    }

    void warnedSealLate() {
        sealLateWarned = true;
    }

    /** The address the host's link came from: the one whose room count this room is in. */
    String openedBy() {
        return openedBy;
    }

    /** Whether the room is turning new machines away at {@code now}. */
    boolean locked(long now) {
        return now < lockedUntilNanos;
    }

    /**
     * Records a wrong password; true when this one is the cap-th in the window and the room is now locked.
     * The window and the lock are the same length (one key, {@code roomLockSeconds}); the window opens with
     * the first wrong password after the last window expired.
     */
    boolean heardWrongPassword(long now, int max, long windowNanos) {
        if (wrongPasswords == 0 || now - wrongWindowStartNanos >= windowNanos) {
            wrongPasswords = 0;
            wrongWindowStartNanos = now;
        }
        wrongPasswords++;
        if (wrongPasswords >= max) {
            wrongPasswords = 0;
            lockedUntilNanos = now + windowNanos;
            return true;
        }
        return false;
    }

    RoomCode code() {
        return code;
    }

    boolean hasPassword() {
        return password != null;
    }

    /** Whether this is the password, as the code's alphabet reads it: already normalised by the caller. */
    boolean passwordIs(@Nullable String normalised) {
        return password != null && password.equals(normalised);
    }

    String buildId() {
        return buildId;
    }

    String buildVersion() {
        return buildVersion;
    }

    long hostKey() {
        return hostKey;
    }

    byte[] sessionConfig() {
        return sessionConfig.clone();
    }

    long openedAtNanos() {
        return openedAtNanos;
    }

    int linkCount() {
        return links.size();
    }

    void add(int link) {
        links.add(link);
    }

    /** The links in the room now, as a fresh array. */
    int[] links() {
        int[] result = new int[links.size()];
        int i = 0;
        for (int link : links) {
            result[i++] = link;
        }
        return result;
    }

    /** Takes a link out; true when that was the last one. */
    boolean remove(int link) {
        links.remove(link);
        return links.isEmpty();
    }
}
