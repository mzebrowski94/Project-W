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

    Room(
            RoomCode code,
            @Nullable String password,
            String buildId,
            String buildVersion,
            long hostKey,
            byte[] sessionConfig,
            long openedAtNanos) {
        this.code = code;
        this.password = password;
        this.buildId = buildId;
        this.buildVersion = buildVersion;
        this.hostKey = hostKey;
        this.sessionConfig = sessionConfig.clone();
        this.openedAtNanos = openedAtNanos;
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
