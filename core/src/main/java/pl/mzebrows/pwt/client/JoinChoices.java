package pl.mzebrows.pwt.client;

import java.util.List;
import java.util.Locale;
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

    /** Where Enter would connect, and how to say so. */
    public record Target(String host, int port, String description) {}

    static final int MAX_TYPED = 64;

    private final String ownBuild;
    private final StringBuilder typed = new StringBuilder();
    private List<LanListener.Heard> sessions = List.of();
    private int selected;
    private String problem = "";

    /** @param ownBuild this copy's build id: a session running another is shown, and refused here */
    public JoinChoices(String ownBuild) {
        this.ownBuild = ownBuild;
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
        return new Target(
                session.address(),
                session.announcement().gamePort(),
                "room " + session.announcement().code() + " at " + session.address());
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
