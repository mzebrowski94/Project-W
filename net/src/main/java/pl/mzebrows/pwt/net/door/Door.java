package pl.mzebrows.pwt.net.door;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * The door's wire: the first message of a link to the relay is {@link Open} (a host) or {@link Join} (anybody
 * else), and the relay answers {@link Opened}, {@link Accepted} or {@link Refused}. Numbers from 40 up, so
 * nothing here is mistaken for a session message (1-33); big-endian, strings as a length and UTF-8. Control
 * traffic, sent once per link and never per tick.
 *
 * <p>Provisional, for review (AFK run): the door has a wire of its own in {@code net}, not a share of the
 * session's {@code Msg}, which is package-private and stage 4's to make blind. The turned-down alternative is
 * to put the five types in {@code Msg} beside the session's.
 */
public final class Door {

    public static final int OPEN = 40;
    public static final int JOIN = 41;
    public static final int OPENED = 42;
    public static final int ACCEPTED = 43;
    public static final int REFUSED = 44;

    /** A machine whose link dropped asking back into the room it was in (G0105). */
    public static final int REJOIN = 45;

    /** Why a machine that asks back into a room it was in is turned away: its place is no longer held (G0105). */
    public static final String GIVEN_UP = "your place in the room was given up - join again";

    /** What every refusal text starts with. */
    public static final String REFUSAL_PREFIX = "refused by the relay: ";

    /** The longest string a door message carries, in bytes: a build id, a version, a code, a password, a reason. */
    public static final int MAX_TEXT_BYTES = 256;

    /** The longest session config an {@link Open} carries - a handful of numbers, never a world. */
    public static final int MAX_CONFIG_BYTES = 4_096;

    private Door() {}

    /** One message of the door. */
    public sealed interface Message permits Open, Join, Rejoin, Opened, Accepted, Refused {}

    /**
     * A host asking for a room.
     *
     * @param hostKey what names the host to the relay, opaque to it
     * @param sessionConfig the host's session config, as bytes the relay keeps and never reads here
     * @param password the room's password as typed, or empty for none
     */
    @SuppressWarnings("ArrayRecordComponent")
    public record Open(String buildId, String buildVersion, long hostKey, byte[] sessionConfig, String password)
            implements Message {}

    /**
     * A machine asking into a room.
     *
     * @param password as typed, or empty for none
     */
    public record Join(String code, String password, String buildId, String buildVersion) implements Message {}

    /**
     * A machine that was in a room asking back into it, with no password: the proof is the token it will show the room's
     * sequencer next, which the relay never sees (G0105).
     */
    public record Rejoin(String code) implements Message {}

    /** The relay's answer to an {@link Open}: the room's code. */
    public record Opened(String code) implements Message {}

    /** The relay's answer to a {@link Join}: the machine is in the room of this code. */
    public record Accepted(String code) implements Message {}

    /** The relay's refusal, whole: {@link #REFUSAL_PREFIX} and the reason. The link is closed after it. */
    public record Refused(String text) implements Message {}

    /**
     * The text of a refusal for a reason, cut to {@link #MAX_TEXT_BYTES} at a character's edge: a reason may repeat
     * what a machine sent, and a refusal that cannot be sent would stop the relay.
     */
    public static Refused refusal(String reason) {
        String text = REFUSAL_PREFIX + reason;
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        if (body.length <= MAX_TEXT_BYTES) {
            return new Refused(text);
        }
        int end = MAX_TEXT_BYTES;
        while (end > 0 && (body[end] & 0xC0) == 0x80) {
            end--; // the first byte left out continues a character: leave that whole character out
        }
        return new Refused(new String(body, 0, end, StandardCharsets.UTF_8));
    }

    public static byte[] encode(Message message) {
        return switch (message) {
            case Open open -> {
                byte[] id = bytes(open.buildId());
                byte[] version = bytes(open.buildVersion());
                byte[] password = bytes(open.password());
                if (open.sessionConfig().length > MAX_CONFIG_BYTES) {
                    throw new IllegalArgumentException("a session config over " + MAX_CONFIG_BYTES + " bytes");
                }
                ByteBuffer out = ByteBuffer.allocate(
                        1 + 12 + id.length + version.length + password.length + 8 + open.sessionConfig().length + 4);
                out.put((byte) OPEN);
                put(out, id);
                put(out, version);
                out.putLong(open.hostKey());
                out.putInt(open.sessionConfig().length).put(open.sessionConfig());
                put(out, password);
                yield done(out);
            }
            case Join join -> {
                byte[][] parts = {
                    bytes(join.code()), bytes(join.password()), bytes(join.buildId()), bytes(join.buildVersion())
                };
                ByteBuffer out = ByteBuffer.allocate(
                        1 + 16 + parts[0].length + parts[1].length + parts[2].length + parts[3].length);
                out.put((byte) JOIN);
                for (byte[] part : parts) {
                    put(out, part);
                }
                yield done(out);
            }
            case Rejoin rejoin -> text(REJOIN, rejoin.code());
            case Opened opened -> text(OPENED, opened.code());
            case Accepted accepted -> text(ACCEPTED, accepted.code());
            case Refused refused -> text(REFUSED, refused.text());
        };
    }

    /**
     * Reads one message.
     *
     * @throws IllegalArgumentException for anything that is not exactly one well-formed door message
     */
    public static Message read(byte[] data, int offset, int length) {
        ByteBuffer in = ByteBuffer.wrap(data, offset, length);
        try {
            int type = in.get();
            Message message =
                    switch (type) {
                        case OPEN -> {
                            String id = string(in);
                            String version = string(in);
                            long hostKey = in.getLong();
                            int configBytes = in.getInt();
                            if (configBytes < 0 || configBytes > MAX_CONFIG_BYTES || configBytes > in.remaining()) {
                                throw new IllegalArgumentException("a session config of " + configBytes + " bytes");
                            }
                            byte[] config = new byte[configBytes];
                            in.get(config);
                            yield new Open(id, version, hostKey, config, string(in));
                        }
                        case JOIN -> new Join(string(in), string(in), string(in), string(in));
                        case REJOIN -> new Rejoin(string(in));
                        case OPENED -> new Opened(string(in));
                        case ACCEPTED -> new Accepted(string(in));
                        case REFUSED -> new Refused(string(in));
                        default -> throw new IllegalArgumentException("not a door message: type " + type);
                    };
            if (in.hasRemaining()) {
                throw new IllegalArgumentException("a door message with " + in.remaining() + " bytes left over");
            }
            return message;
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("a door message cut short", e);
        }
    }

    private static byte[] text(int type, String text) {
        byte[] body = bytes(text);
        ByteBuffer out = ByteBuffer.allocate(1 + 4 + body.length);
        out.put((byte) type);
        put(out, body);
        return done(out);
    }

    private static byte[] bytes(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("a door string over " + MAX_TEXT_BYTES + " bytes");
        }
        return body;
    }

    private static void put(ByteBuffer out, byte[] body) {
        out.putInt(body.length).put(body);
    }

    private static byte[] done(ByteBuffer out) {
        byte[] result = new byte[out.position()];
        out.flip().get(result);
        return result;
    }

    private static String string(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > MAX_TEXT_BYTES || length > in.remaining()) {
            throw new IllegalArgumentException("a door string of " + length + " bytes");
        }
        byte[] body = new byte[length];
        in.get(body);
        return new String(body, StandardCharsets.UTF_8);
    }
}
