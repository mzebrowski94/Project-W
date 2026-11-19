package pl.mzebrows.pwt.client.play;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.LanAnnouncement;
import pl.mzebrows.pwt.net.lan.LanBeacon;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.sim.Players;

/**
 * A hosting copy's side of being found (G0038): it announces the session to the local network under a
 * room code, and gives its players a line to read out to a friend - the code for anyone on the same
 * network or Hamachi, and the addresses for anyone who has to type one.
 *
 * <p>A session with a room on the relay (G0103) is announced under the relay's code, with its password, once the relay
 * has handed the code out: a machine on the host's network then joins through the relay without typing the password.
 */
final class Invitation implements AutoCloseable {

    private final LanBeacon beacon;
    private final long session;
    private final int port;
    private final String machine;
    private final String line;
    private final boolean roomed;
    private @Nullable RoomCode room;
    private String password = "";
    private int players = -1;
    private String announced = "";
    private boolean changed = true;

    /** What a session says it plays while it is on its briefing and has chosen nothing. */
    static final String BRIEFING = "briefing";

    Invitation(LanBeacon beacon, long session, int port, String machine, List<String> addresses) {
        this.beacon = beacon;
        this.session = session;
        this.port = port;
        this.machine = machine;
        this.roomed = false;
        this.line = "room " + code() + "   join at "
                + (addresses.isEmpty() ? "this PC only" : String.join(", ", addresses)) + "   port " + port;
    }

    /** An invitation to a room on the relay, silent until {@link #opened} says what the relay named it. */
    private Invitation(LanBeacon beacon, long session, int relayPort, String machine) {
        this.beacon = beacon;
        this.session = session;
        this.port = relayPort;
        this.machine = machine;
        this.roomed = true;
        this.line = "";
    }

    /** An invitation to a room on the relay at {@code relayPort}; {@code session} tells its announcements from others'. */
    static Invitation roomed(LanBeacon beacon, long session, int relayPort, String machine) {
        return new Invitation(beacon, session, relayPort, machine);
    }

    /** The relay handed out this room's code: announced from now on, with its password (empty for none). */
    void opened(RoomCode code, String roomPassword) {
        room = code;
        password = roomPassword;
        changed = true;
    }

    boolean roomed() {
        return roomed;
    }

    /** Whether the relay has named the room: always, for a session of this network. */
    boolean ready() {
        return !roomed || room != null;
    }

    RoomCode code() {
        RoomCode relays = room;
        return relays != null ? relays : RoomCode.of(session);
    }

    /**
     * Announces the session, saying what it plays and how many players it has now - a new announcement only when
     * either changes. A room not yet named says nothing.
     */
    void pump(int standing, String scenario) {
        if (!ready()) {
            return;
        }
        if (changed || standing != players || !scenario.equals(announced)) {
            changed = false;
            players = standing;
            announced = scenario;
            beacon.announce(new LanAnnouncement(
                    session,
                    code(),
                    BuildId.current(),
                    port,
                    scenario,
                    machine,
                    standing,
                    Players.MAX,
                    roomed,
                    password));
        }
        beacon.pump();
    }

    /** What the host reads out to a friend: the room and how to reach it - or, in a room on the relay, its password. */
    String line() {
        if (roomed) {
            return room == null ? "" : roomLine(room.text(), password);
        }
        return line;
    }

    /** {@code room ACDE   password KJ34}: a room on the relay as a player reads it out, the password if it has one. */
    static String roomLine(String code, String password) {
        return "room " + code + (password.isEmpty() ? "" : "   password " + password);
    }

    @Override
    public void close() {
        beacon.close();
    }
}
