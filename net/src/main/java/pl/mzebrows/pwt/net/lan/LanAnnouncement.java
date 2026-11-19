package pl.mzebrows.pwt.net.lan;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A host saying it is there: which session, how to reach its game, what it is playing and how full it
 * is. Sent in the clear once a second, so it carries nothing a stranger on the network should not see -
 * not the host's key, not its config. Where it came from is the address to connect to; the datagram does
 * not name one, because the host cannot know which of its addresses a joiner will hear it on.
 *
 * @param session the session's id, which its room code is made from; how duplicates heard over two
 *     networks are told to be one
 * @param build the host's build id: a joiner shows a session it could not join rather than hiding it
 * @param gamePort the port the host's sequencer listens on
 * @param host a name for the host's machine, for the list
 * @param roomed the session has a room on the relay (G0103): {@code code} is the relay's, and a machine joins through
 *     the relay rather than at the address heard - {@code gamePort} is then the relay's and means nothing to a joiner
 * @param password the room's password as the host set it - four characters of the room code's alphabet - or empty for
 *     none; only a roomed session has one. Carried so a machine on the host's network joins without typing it (G0103)
 */
public record LanAnnouncement(
        long session,
        RoomCode code,
        String build,
        int gamePort,
        String scenario,
        String host,
        int players,
        int capacity,
        boolean roomed,
        String password) {

    /** "PWL2": the announcement that says whether its session has a room, and its password. */
    private static final int MAGIC = 0x5057_4C32;

    /** The longest any text in an announcement may be, in bytes. */
    static final int MAX_TEXT = 64;

    /** The largest an announcement can be. */
    static final int MAX_BYTES = 4 + 8 + RoomCode.LENGTH + 4 + 3 * (2 + MAX_TEXT) + 2 + 1 + 2 + RoomCode.LENGTH;

    public LanAnnouncement {
        if (gamePort <= 0 || gamePort > 65_535 || players < 0 || capacity < 1 || capacity > 255) {
            throw new IllegalArgumentException("an announcement of port " + gamePort + ", " + players + "/" + capacity);
        }
        if (!password.isEmpty() && (!roomed || RoomCode.letters(password, RoomCode.LENGTH) == null)) {
            throw new IllegalArgumentException("a password of an announcement that has no room, or is not a password");
        }
        build = clip(build);
        scenario = clip(scenario);
        host = clip(host);
    }

    /** A session of this network only: no room, no password. */
    public LanAnnouncement(
            long session,
            RoomCode code,
            String build,
            int gamePort,
            String scenario,
            String host,
            int players,
            int capacity) {
        this(session, code, build, gamePort, scenario, host, players, capacity, false, "");
    }

    /** The bytes that go on the wire. */
    public ByteBuffer encode(ByteBuffer into) {
        into.clear();
        into.putInt(MAGIC).putLong(session).put(code.text().getBytes(StandardCharsets.US_ASCII));
        into.putInt(gamePort);
        put(into, build);
        put(into, scenario);
        put(into, host);
        into.put((byte) Math.min(players, 255)).put((byte) capacity);
        into.put((byte) (roomed ? 1 : 0));
        put(into, password);
        return into.flip();
    }

    /**
     * Reads an announcement from a datagram.
     *
     * @throws IllegalArgumentException if it is not one - something else on the network uses the port too
     */
    public static LanAnnouncement decode(ByteBuffer from) {
        try {
            if (from.getInt() != MAGIC) {
                throw new IllegalArgumentException("not an announcement");
            }
            long session = from.getLong();
            byte[] code = new byte[RoomCode.LENGTH];
            from.get(code);
            int gamePort = from.getInt();
            String build = text(from);
            String scenario = text(from);
            String host = text(from);
            int players = from.get() & 0xFF;
            int capacity = from.get() & 0xFF;
            boolean roomed = from.get() != 0;
            String password = text(from);
            if (from.hasRemaining()) {
                throw new IllegalArgumentException(from.remaining() + " bytes after an announcement");
            }
            return new LanAnnouncement(
                    session,
                    new RoomCode(new String(code, StandardCharsets.US_ASCII)),
                    build,
                    gamePort,
                    scenario,
                    host,
                    players,
                    capacity,
                    roomed,
                    password);
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("an announcement that ends early", e);
        }
    }

    private static void put(ByteBuffer into, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        into.putShort((short) bytes.length).put(bytes);
    }

    private static String text(ByteBuffer from) {
        int length = from.getShort() & 0xFFFF;
        if (length > MAX_TEXT) {
            throw new IllegalArgumentException("a text of " + length + " bytes in an announcement");
        }
        byte[] bytes = new byte[length];
        from.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Cuts a text to what an announcement carries, on a character boundary. */
    private static String clip(String text) {
        String clipped = text;
        while (clipped.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped;
    }
}
