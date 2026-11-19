package pl.mzebrows.pwt.client.play;

import java.util.List;
import pl.mzebrows.pwt.net.lan.LanAnnouncement;
import pl.mzebrows.pwt.net.lan.LanBeacon;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.sim.Players;

/**
 * A hosting copy's side of being found (G0038): it announces the session to the local network under a
 * room code, and gives its players a line to read out to a friend - the code for anyone on the same
 * network or Hamachi, and the addresses for anyone who has to type one.
 */
final class Invitation implements AutoCloseable {

    private final LanBeacon beacon;
    private final long session;
    private final int port;
    private final String machine;
    private final String line;
    private int players = -1;
    private String announced = "";

    /** What a session says it plays while it is on its briefing and has chosen nothing. */
    static final String BRIEFING = "briefing";

    Invitation(LanBeacon beacon, long session, int port, String machine, List<String> addresses) {
        this.beacon = beacon;
        this.session = session;
        this.port = port;
        this.machine = machine;
        this.line = "room " + code() + "   join at "
                + (addresses.isEmpty() ? "this PC only" : String.join(", ", addresses)) + "   port " + port;
    }

    RoomCode code() {
        return RoomCode.of(session);
    }

    /**
     * Announces the session, saying what it plays and how many players it has now - a new announcement only when
     * either changes.
     */
    void pump(int standing, String scenario) {
        if (standing != players || !scenario.equals(announced)) {
            players = standing;
            announced = scenario;
            beacon.announce(new LanAnnouncement(
                    session, code(), BuildId.current(), port, scenario, machine, standing, Players.MAX));
        }
        beacon.pump();
    }

    String line() {
        return line;
    }

    @Override
    public void close() {
        beacon.close();
    }
}
