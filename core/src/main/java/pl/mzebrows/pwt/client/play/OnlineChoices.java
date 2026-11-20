package pl.mzebrows.pwt.client.play;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.session.MachineId;

/**
 * What the online page holds for the player to type: the nickname he plays under, and the address of the relay
 * (server ip and server port). Each is a line a keyboard types into, filtered as it is typed so nothing the wire or
 * the connection could not carry gets in. Kept out of the screen so it can be tested without a window.
 *
 * <p>The nickname is the machine's id, which the hello says and the briefing and the pause list show - so it has the
 * id's alphabet: lower-case letters, digits and dashes, sixteen at most. The server line is a host name or an
 * address, the port line digits; an empty port means the shipped one.
 */
public final class OnlineChoices {

    /** The lines, in the order of the online page's rows. */
    public enum Row {
        NICKNAME,
        SERVER_IP,
        SERVER_PORT
    }

    private static final List<Row> ROWS = List.of(Row.values());

    static final int MAX_HOST = 64;
    static final int MAX_PORT_DIGITS = 5;

    private final Map<Row, String> lines = new EnumMap<>(Row.class);
    private final String ownNickname;

    /**
     * @param nickname what this machine is called now: its id, or what the player chose
     * @param host the relay's address as it stands: what the player kept, or {@code net.relay.host}
     * @param port the relay's port as it stands, or empty
     */
    public OnlineChoices(String nickname, String host, String port) {
        for (Row row : Row.values()) {
            lines.put(row, "");
        }
        this.ownNickname = nickname;
        replace(Row.NICKNAME, nickname);
        replace(Row.SERVER_IP, host);
        replace(Row.SERVER_PORT, port);
    }

    private String line(Row row) {
        return lines.getOrDefault(row, "");
    }

    /** The line the online page's row at this place holds: the rows are in the page's order. */
    public static Row row(int place) {
        return ROWS.get(place);
    }

    /** One typed character into a line; backspace deletes, anything the line cannot hold is ignored. True if it changed. */
    public boolean type(Row row, char c) {
        String was = line(row);
        if (c == '\b') {
            if (!was.isEmpty()) {
                lines.put(row, was.substring(0, was.length() - 1));
            }
        } else if (was.length() < max(row) && accepts(row, c)) {
            lines.put(row, was + filtered(row, c));
        }
        return !was.equals(line(row));
    }

    /** Replaces a line - with what was kept - filtered as typing is. */
    public void replace(Row row, String text) {
        lines.put(row, "");
        for (int i = 0; i < text.length(); i++) {
            type(row, text.charAt(i));
        }
    }

    /** What a line holds now. */
    public String text(Row row) {
        return line(row);
    }

    /** The nickname to play under: what is typed, or - while the line is empty - the one the machine had. */
    public String nickname() {
        String typed = line(Row.NICKNAME);
        return MachineId.valid(typed) ? typed : ownNickname;
    }

    /** The relay's address as typed, empty for none. */
    public String host() {
        return line(Row.SERVER_IP);
    }

    /** The relay's port as typed: digits that make a port, or null for none or not one - the shipped port stands. */
    public @Nullable String port() {
        String typed = line(Row.SERVER_PORT);
        if (typed.isEmpty()) {
            return null;
        }
        int number = Integer.parseInt(typed);
        return number > 0 && number < 65_536 ? typed : null;
    }

    private static int max(Row row) {
        return switch (row) {
            case NICKNAME -> MachineId.MAX_LENGTH;
            case SERVER_IP -> MAX_HOST;
            case SERVER_PORT -> MAX_PORT_DIGITS;
        };
    }

    private static boolean accepts(Row row, char c) {
        return switch (row) {
            case NICKNAME -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
            case SERVER_IP ->
                (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
            case SERVER_PORT -> c >= '0' && c <= '9';
        };
    }

    private static char filtered(Row row, char c) {
        return row == Row.NICKNAME ? Character.toLowerCase(c) : c;
    }
}
