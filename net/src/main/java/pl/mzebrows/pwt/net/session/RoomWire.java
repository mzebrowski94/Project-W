package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.net.NetConfig;

/**
 * What the door's two ends need of the session's wire without being part of it (ADR 0023): the host's session
 * config as the bytes an {@code OPEN} carries, read back by the relay that builds the room's sequencer, and
 * which of a link's messages the relay's byte meter does not count. The one place outside the peer and the
 * sequencer that knows {@link Msg}'s numbers.
 */
public final class RoomWire {

    private RoomWire() {}

    /** The session config as an {@code OPEN} carries it: what a welcome carries, no more. */
    public static byte[] sessionConfig(NetConfig.Session session) {
        WireWriter out = new WireWriter(16);
        Msg.putSession(out, session);
        return out.toBytes();
    }

    /**
     * Reads what {@link #sessionConfig(NetConfig.Session)} wrote.
     *
     * @throws IllegalArgumentException for bytes that are not a session config this protocol reads
     */
    public static NetConfig.Session sessionConfig(byte[] bytes) {
        try {
            WireReader in = new WireReader().wrap(bytes, 0, bytes.length);
            NetConfig.Session session = Msg.getSession(in);
            in.expectEnd();
            return session;
        } catch (WireException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Whether this message, heard from a peer, is a world on its way to a joiner: the one thing a link may send
     * more of than a rate allows, because there is one per joiner and {@code Transport.MAX_MESSAGE_BYTES}
     * bounds it.
     */
    public static boolean isSnapshot(byte[] data, int offset, int length) {
        return length > 0 && data[offset] == Msg.SNAPSHOT_TAKEN;
    }

    /**
     * Whether this message, the first a link let in to come back says to its room, is the return that shows its
     * token: anything else - a hello above all - is a stranger who knew only the code (G0105).
     */
    public static boolean isRejoin(byte[] data, int offset, int length) {
        return length > 0 && data[offset] == Msg.REJOIN;
    }
}
