package pl.mzebrows.pwt.relay;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.RoomCode;

/**
 * The relay's record of one session (G0103): its code, its password if it has one, the build it runs and
 * its links. The password lives here, in memory, and nowhere else - no {@code toString}, no log line.
 * No sequencer yet (stage 5): a room after the door only keeps its links.
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

    /** Takes a link out; true when that was the last one. */
    boolean remove(int link) {
        links.remove(link);
        return links.isEmpty();
    }
}
