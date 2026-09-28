package pl.mzebrows.pwt.net.session;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * Bytes out, big-endian, into a buffer kept from one message to the next - so sending sixty bundles a
 * second allocates nothing once the buffer has grown to fit. The transport copies what it is handed, so
 * the buffer is free again as soon as {@code send} returns.
 */
final class WireWriter {

    private byte[] bytes;
    private int size;

    WireWriter(int initialBytes) {
        bytes = new byte[initialBytes];
    }

    /** Starts a new message in the same buffer. */
    WireWriter reset() {
        size = 0;
        return this;
    }

    WireWriter putByte(int value) {
        ensure(1);
        bytes[size++] = (byte) value;
        return this;
    }

    WireWriter putInt(int value) {
        ensure(4);
        bytes[size++] = (byte) (value >>> 24);
        bytes[size++] = (byte) (value >>> 16);
        bytes[size++] = (byte) (value >>> 8);
        bytes[size++] = (byte) value;
        return this;
    }

    WireWriter putLong(long value) {
        putInt((int) (value >>> 32));
        return putInt((int) value);
    }

    /** A length, then the bytes. */
    WireWriter putBytes(byte[] source, int offset, int length) {
        putInt(length);
        ensure(length);
        System.arraycopy(source, offset, bytes, size, length);
        size += length;
        return this;
    }

    WireWriter putBytes(byte[] source) {
        return putBytes(source, 0, source.length);
    }

    /** UTF-8, length first. For the few control messages that carry text, never for a tick. */
    WireWriter putString(String text) {
        return putBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The length, then the bytes deflated, their own length first. For the two things a join waits for -
     * the host's config and a world - which cross a slow link as a tenth of themselves. Never for a tick.
     */
    WireWriter putPacked(byte[] source, int offset, int length) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(source, offset, length);
            deflater.finish();
            putInt(length);
            int packedLengthAt = size;
            putInt(0);
            while (!deflater.finished()) {
                ensure(Math.max(1024, length / 4));
                size += deflater.deflate(bytes, size, bytes.length - size);
            }
            int packed = size - packedLengthAt - 4;
            bytes[packedLengthAt] = (byte) (packed >>> 24);
            bytes[packedLengthAt + 1] = (byte) (packed >>> 16);
            bytes[packedLengthAt + 2] = (byte) (packed >>> 8);
            bytes[packedLengthAt + 3] = (byte) packed;
        } finally {
            deflater.end();
        }
        return this;
    }

    /** Appends what another writer holds, as it is. */
    WireWriter putRaw(byte[] source, int offset, int length) {
        ensure(length);
        System.arraycopy(source, offset, bytes, size, length);
        size += length;
        return this;
    }

    byte[] array() {
        return bytes;
    }

    int size() {
        return size;
    }

    /** A copy of what has been written, for keeping. */
    byte[] toBytes() {
        return Arrays.copyOf(bytes, size);
    }

    private void ensure(int more) {
        if (size + more > bytes.length) {
            bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
        }
    }
}
