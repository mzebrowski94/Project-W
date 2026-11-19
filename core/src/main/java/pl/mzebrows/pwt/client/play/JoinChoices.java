package pl.mzebrows.pwt.client.play;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.lan.LanListener;
import pl.mzebrows.pwt.net.lan.RoomCode;

/**
 * What the join screen decides (G0038): which session heard on the network is highlighted, what the
 * player has typed, and what Enter would join. Kept out of the screen so it can be tested without a
 * window.
 *
 * <p>Typed text wins over the list. A room code joins the session heard with that code; anything else is
 * an address, {@code host} or {@code host:port}, which is how a host the network cannot hear - one across
 * the internet, one behind a router that drops broadcasts - is still reached. With nothing typed, Enter
 * joins the highlighted session.
 */
public final class JoinChoices {

    /**
     * Where Enter would connect, and how to say so. A target with a {@code room} is reached through the relay (G0103):
     * {@code host} and {@code port} are the relay's, and the door is asked into that room with {@code password}.
     */
    public record Target(String host, int port, String description, String room, String password) {

        /** A session at an address: no room. */
        public Target(String host, int port, String description) {
            this(host, port, description, "", "");
        }

        /** Whether it is reached through the relay. */
        public boolean relayed() {
            return !room.isEmpty();
        }
    }

    /** Where the relay is, as {@code net.relay.host} and {@code net.relay.port} say. */
    public record Relay(String host, int port) {}

    static final int MAX_TYPED = 64;

    private final String ownBuild;
    private final @Nullable Relay relay;
    private final PasswordLine password = new PasswordLine("");
    private @Nullable String asking;
    private final StringBuilder typed = new StringBuilder();
    private List<LanListener.Heard> sessions = List.of();
    private int selected;
    private String problem = "";

    /** @param ownBuild this copy's build id: a session running another is shown, and refused here */
    public JoinChoices(String ownBuild) {
        this(ownBuild, null);
    }

    /**
     * @param ownBuild this copy's build id: a session running another is shown, and refused here
     * @param relay where the relay is, or null when none is set: a room code not heard on the network then goes nowhere
     */
    public JoinChoices(String ownBuild, @Nullable Relay relay) {
        this.ownBuild = ownBuild;
        this.relay = relay;
    }

    /**
     * The relay refused this room for want of a password: the page asks for one, and what is typed now goes into it
     * (G0103). One try - a wrong one is the relay's other refusal and ends the attempt.
     */
    public void askPassword(String room) {
        asking = room;
        password.replace("");
        problem = "";
    }

    /** The room whose password the page is asking for, or null. */
    public @Nullable String asking() {
        return asking;
    }

    /** Gives up the password line: what is typed is the code or address again. */
    public void cancelPassword() {
        asking = null;
        password.replace("");
        problem = "";
    }

    /** What has been typed into the password line. */
    public String typedPassword() {
        return password.text();
    }

    /** The sessions heard lately, in the order they were first heard. The highlight stays in range. */
    public void heard(List<LanListener.Heard> now) {
        sessions = now;
        selected = sessions.isEmpty() ? 0 : Math.min(selected, sessions.size() - 1);
    }

    /** Highlights a session by its place in {@link #sessions()}; out of range is ignored. */
    public void select(int session) {
        if (session >= 0 && session < sessions.size()) {
            selected = session;
        }
    }

    /**
     * Replaces what was typed - with a room code picked on a pad, a letter at a time. Filtered as typing
     * is, so nothing a keyboard could not have typed gets in.
     */
    public void replaceTyped(String text) {
        if (asking != null) {
            problem = "";
            password.replace(text);
            return;
        }
        typed.setLength(0);
        for (int i = 0; i < text.length(); i++) {
            type(text.charAt(i));
        }
    }

    public void up() {
        if (!sessions.isEmpty()) {
            selected = Math.floorMod(selected - 1, sessions.size());
        }
    }

    public void down() {
        if (!sessions.isEmpty()) {
            selected = Math.floorMod(selected + 1, sessions.size());
        }
    }

    /**
     * One typed character. Backspace deletes; letters, digits and the characters an address has are
     * kept; anything else, and anything past {@value #MAX_TYPED} characters, is ignored.
     */
    public void type(char c) {
        problem = "";
        if (asking != null) {
            password.type(c);
            return;
        }
        if (c == '\b') {
            if (!typed.isEmpty()) {
                typed.setLength(typed.length() - 1);
            }
            return;
        }
        boolean addressy = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.'
                || c == ':'
                || c == '-'
                || c == '_';
        if (addressy && typed.length() < MAX_TYPED) {
            typed.append(c);
        }
    }

    public String typed() {
        return typed.toString();
    }

    public List<LanListener.Heard> sessions() {
        return sessions;
    }

    public int selected() {
        return selected;
    }

    /** Why the last {@link #choose} came back empty, or nothing. */
    public String problem() {
        return problem;
    }

    /** Whether a session heard here runs a build this copy could join. */
    public boolean joinable(LanListener.Heard session) {
        return session.announcement().build().equals(ownBuild);
    }

    /** What Enter joins, or null - with {@link #problem()} saying why. */
    public @Nullable Target choose(int defaultPort) {
        String room = asking;
        if (room != null) {
            String read = password.read();
            if (read == null || read.isEmpty()) {
                problem = PasswordLine.REQUIRED;
                return null;
            }
            return through(room, read);
        }
        String text = typed.toString().trim();
        if (text.isEmpty()) {
            if (sessions.isEmpty()) {
                problem = "nothing heard on this network yet - type an address or a room code";
                return null;
            }
            return join(sessions.get(selected));
        }
        RoomCode code = RoomCode.parse(text);
        if (code != null) {
            for (LanListener.Heard session : sessions) {
                if (session.announcement().code().equals(code)) {
                    return join(session);
                }
            }
            if (text.indexOf('.') < 0 && text.indexOf(':') < 0) {
                if (relay != null) {
                    // Not heard here: a room on the relay, if there is one (G0103).
                    return through(code.text(), "");
                }
                problem = "no session with room " + code + " heard on this network - try its address";
                return null;
            }
        }
        return address(text, defaultPort);
    }

    private @Nullable Target join(LanListener.Heard session) {
        if (!joinable(session)) {
            problem = "room " + session.announcement().code() + " runs build "
                    + session.announcement().build() + " and this is " + ownBuild
                    + " - the same build on both machines, or no game";
            return null;
        }
        if (session.announcement().roomed()) {
            // A room on the relay: it announces its password to its own network, so nobody types it here (G0103).
            if (relay == null) {
                problem = "room " + session.announcement().code()
                        + " is on the relay, and no relay is set - net.relay.host in network.properties";
                return null;
            }
            return through(
                    session.announcement().code().text(), session.announcement().password());
        }
        return new Target(
                session.address(),
                session.announcement().gamePort(),
                "room " + session.announcement().code() + " at " + session.address());
    }

    private Target through(String room, String withPassword) {
        Relay at = Objects.requireNonNull(relay);
        return new Target(at.host(), at.port(), "room " + room + " through the relay", room, withPassword);
    }

    private @Nullable Target address(String text, int defaultPort) {
        int colon = text.lastIndexOf(':');
        String host = text;
        int port = defaultPort;
        if (colon >= 0) {
            if (text.indexOf(':') != colon) {
                problem = "one colon at most: host or host:port";
                return null;
            }
            host = text.substring(0, colon);
            try {
                port = Integer.parseInt(text.substring(colon + 1));
            } catch (NumberFormatException e) {
                problem = "the port after the colon is a number";
                return null;
            }
        }
        if (host.isEmpty() || port <= 0 || port > 65_535) {
            problem = "an address is host or host:port";
            return null;
        }
        return new Target(host.toLowerCase(Locale.ROOT), port, host + ":" + port);
    }
}
