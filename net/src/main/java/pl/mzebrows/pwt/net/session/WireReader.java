package pl.mzebrows.pwt.net.session;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Bytes in, big-endian, with every read checked against what is actually there: a message that runs
 * short throws {@link WireException} rather than reading its neighbour's bytes or an old one's.
 * Re-pointed at each message rather than made per message.
 */
final class WireReader {

    private byte[] bytes = new byte[0];
    private int at;
    private int end;

    WireReader wrap(byte[] data, int offset, int length) {
        bytes = data;
        at = offset;
        end = offset + length;
        return this;
    }

    int getByte() {
        need(1);
        return bytes[at++] & 0xFF;
    }

    int getInt() {
        need(4);
        int value = ((bytes[at] & 0xFF) << 24)
                | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8)
                | (bytes[at + 3] & 0xFF);
        at += 4;
        return value;
    }

    long getLong() {
        return ((long) getInt() << 32) | (getInt() & 0xFFFF_FFFFL);
    }

    /** A length-prefixed run of bytes, copied out - it is kept, and the message it came in is not. */
    byte[] getBytes(int maxLength) {
        int length = getInt();
        if (length < 0 || length > maxLength) {
            throw new WireException("a run of " + length + " bytes, where at most " + maxLength + " fit");
        }
        need(length);
        byte[] copy = Arrays.copyOfRange(bytes, at, at + length);
        at += length;
        return copy;
    }

    /**
     * What {@link WireWriter#putPacked} wrote, inflated. Refused if it claims more than {@code maxLength}
     * - so a hostile or garbled message cannot make it allocate more - or does not inflate to exactly the
     * length it claims.
     */
    byte[] getPacked(int maxLength) {
        int length = getInt();
        if (length < 0 || length > maxLength) {
            throw new WireException("a packed run of " + length + " bytes, where at most " + maxLength + " fit");
        }
        int packed = getInt();
        if (packed < 0) {
            throw new WireException("a packed run " + packed + " bytes long");
        }
        need(packed);
        byte[] raw = new byte[length];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes, at, packed);
            int inflated = 0;
            while (inflated < length) {
                int more = inflater.inflate(raw, inflated, length - inflated);
                if (more == 0) {
                    break;
                }
                inflated += more;
            }
            if (inflated != length
                    || inflater.inflate(new byte[1]) != 0
                    || !inflater.finished()
                    || inflater.getRemaining() != 0) {
                throw new WireException("a packed run that does not inflate to the " + length + " bytes it claims");
            }
        } catch (DataFormatException e) {
            throw new WireException("a packed run that does not inflate: " + e.getMessage());
        } finally {
            inflater.end();
        }
        at += packed;
        return raw;
    }

    String getString(int maxBytes) {
        return new String(getBytes(maxBytes), StandardCharsets.UTF_8);
    }

    /** Steps over bytes the caller has no use for, still checking they are there. */
    void skip(int count) {
        need(count);
        at += count;
    }

    /** Where the reader is, so a caller can keep a part of the message as it came. */
    int position() {
        return at;
    }

    byte[] array() {
        return bytes;
    }

    int remaining() {
        return end - at;
    }

    /** Fails a message that carries more than it should - a different protocol, or garbage. */
    void expectEnd() {
        if (at != end) {
            throw new WireException((end - at) + " bytes left over");
        }
    }

    private void need(int count) {
        if (end - at < count) {
            throw new WireException("message ends " + (count - (end - at)) + " bytes early");
        }
    }
}
