package pl.mzebrows.pwt.net.session;

import pl.mzebrows.pwt.net.NetConfig;

/**
 * The session protocol's message types, the first byte of every message (ADR 0012). Numbers, never
 * class names, so nothing on the wire decides what gets built.
 *
 * <p>Two go unreliable and are sent every tick. {@link #INPUT} carries everything the sequencer has not
 * yet acknowledged; {@link #BUNDLES} carries the newest unacknowledged ticks and any older one a round
 * trip has passed without, so a lost one costs nothing but its own copy.
 * Everything else is rare, goes reliable, and is sent once.
 */
final class Msg {

    /** Bumped whenever a message changes shape. The build id catches that too; this names it in the log. */
    static final int PROTOCOL = 4;

    // Peer to sequencer.
    static final int HELLO = 1;
    static final int INPUT = 2;
    static final int CHECKSUM = 3;
    static final int SNAPSHOT_TAKEN = 4;
    static final int SEAT = 5;
    static final int PAUSE = 6;
    static final int RESTART = 7;
    static final int GOD_MODE = 8;

    // Sequencer to peer.
    static final int WELCOME = 20;
    static final int REFUSED = 21;
    static final int BUNDLES = 22;
    static final int SNAPSHOT = 23;
    static final int SLOT = 24;
    static final int PAUSED = 25;
    static final int DESYNC = 26;
    static final int SNAPSHOT_WANTED = 27;
    static final int DUMP_WANTED = 28;

    /**
     * An input event's type: what the rest of it means. An event on the wire is its number
     * (int), its slot (byte), its type (byte), a value (int) and an extra (byte) - {@link #EVENT_BYTES} in
     * all, whatever the type, so a message's room is one sum.
     */
    static final int EVENT_ORDER = 1;

    /** A card taken: the value is the draft's serial, the extra the kind's code. */
    static final int EVENT_PICK = 2;

    /**
     * A crate's event (G0056): the value is the code of the pickup taken from the crate the slot has open,
     * or {@link Bundle#CRATE_CLOSE} for shutting it.
     */
    static final int EVENT_CRATE = 3;

    /** The bytes one input event takes on the wire. */
    static final int EVENT_BYTES = 4 + 1 + 1 + 4 + 1;

    /** Why a pause was asked for, carried by {@link #PAUSE} and echoed by {@link #PAUSED}: a player's own. */
    static final int PAUSE_BY_PLAYER = 0;

    /** A solo player's draft (G0049), which the paused banner does not cover the view for. */
    static final int PAUSE_FOR_DRAFT = 1;

    /** No slot: the session is full. */
    static final int NO_SLOT = 255;

    /** No margin heard since the last report. */
    static final int NO_MARGIN = Integer.MIN_VALUE;

    /** The longest text a control message carries - a build id, a scenario name, a reason. */
    static final int MAX_TEXT = 256;

    /**
     * What a snapshot message carries besides the world: its type, then three ints - a peer and a tick,
     * or a generation and a tick - then the world's length. The world may be this much less than {@code
     * Transport.MAX_MESSAGE_BYTES}, or than the limit a test gives.
     */
    static final int SNAPSHOT_HEADER = 1 + 4 + 4 + 4;

    /** The longest config a host ships, unpacked. {@code assets/sim/*.properties} without their comments are about 26 KB. */
    static final int MAX_CONFIG = 1024 * 1024;

    /** Writes the session config into a welcome: every component, in order (ADR 0018). */
    static void putSession(WireWriter out, NetConfig.Session session) {
        out.putInt(session.checksumTicks());
    }

    /** Reads what {@link #putSession} wrote. */
    static NetConfig.Session getSession(WireReader in) {
        int checksumTicks = in.getInt();
        if (checksumTicks < 1 || checksumTicks > NetConfig.Session.MOST_CHECKSUM_TICKS) {
            throw new WireException("a session comparing worlds every " + checksumTicks + " ticks");
        }
        return new NetConfig.Session(checksumTicks);
    }

    private Msg() {}
}
