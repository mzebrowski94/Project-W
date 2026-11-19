package pl.mzebrows.pwt.net.session;

import java.util.Arrays;

/**
 * One sealed tick (ADR 0012, "tick bundle"): what every seated slot is holding, the input events the
 * sequencer stamped on it, and the session's own - a restart, a switch of invulnerability. Every peer
 * steps its world with exactly this, which is the whole of lockstep.
 *
 * <p><strong>Blind to the game</strong> (ADR 0023): held input is {@link #HELD_BYTES} bytes the sequencer
 * stores and repeats without reading, and an event is a type, a slot, a value and an extra - numbers the
 * peers, and only the peers, give a meaning ({@link InputCodec}). Nothing here names a game type, so a
 * relay that has never heard of one can seal, store and resend a session.
 *
 * <p>Mutable and reused: one is sealed or read per tick, and nothing in it allocates.
 *
 * <p>Layout: tick (int); flags (byte: 1 restart, 2 switches invulnerability, 4 invulnerable); held
 * count, then per slot its number and its {@link #HELD_BYTES} bytes; order count, then per order a slot
 * and its value (byte); join count, then per join a slot and the machine it sits at (int); pick count,
 * then per pick a slot, its value (int) and its extra (byte); presence count, then per change a slot and
 * whether it goes away (1) or comes back (0); crate count, then per event a slot and its value (byte).
 * Which sections an event type has is {@link Msg#EVENT_ORDER}, {@link Msg#EVENT_PICK},
 * {@link Msg#EVENT_CRATE}.
 */
final class Bundle {

    /** The bytes one slot's held input takes, its slot number not included. */
    static final int HELD_BYTES = 13;

    /** Ahead of the seats in a bundle: its tick, its flags, and how many seats hold input. */
    private static final int HEADER_BYTES = 6;

    private static final int SEAT_BYTES = 1 + HELD_BYTES;

    /**
     * Where each stretch of a seat's held bytes starts, and how long it is. A datagram's second bundle on
     * resends only the stretches that differ from the one before it - compared as bytes, whatever they
     * mean.
     */
    private static final int[] FIELD_AT = {0, 2, 4, 8, 12};

    private static final int[] FIELD_BYTES = {2, 2, 4, 4, 1};

    private static final int ALL_FIELDS = (1 << FIELD_AT.length) - 1;

    /** A changed seat's slot and which of its stretches follow share a byte: the slot is the low three bits. */
    private static final int SLOT_BITS = 3;

    private static final int SLOT_MASK = (1 << SLOT_BITS) - 1;

    private static final int RESTART = 1;
    private static final int SWITCHES_INVULNERABILITY = 2;
    private static final int INVULNERABLE = 4;

    private final byte[][] held;
    private final boolean[] present;

    /** By event type (one of {@code Msg.EVENT_*}), then slot. */
    private final boolean[][] hasEvent;

    private final int[][] eventValue;
    private final int[][] eventExtra;
    private final boolean[] joins;
    private final int[] joinMachines;
    private final boolean[] goesAway;
    private final boolean[] comesBack;

    private int tick;
    private boolean restart;
    private boolean switchesInvulnerability;
    private boolean invulnerable;

    Bundle(int capacity) {
        held = new byte[capacity][HELD_BYTES];
        present = new boolean[capacity];
        hasEvent = new boolean[Msg.EVENT_TYPES + 1][capacity];
        eventValue = new int[Msg.EVENT_TYPES + 1][capacity];
        eventExtra = new int[Msg.EVENT_TYPES + 1][capacity];
        joins = new boolean[capacity];
        joinMachines = new int[capacity];
        goesAway = new boolean[capacity];
        comesBack = new boolean[capacity];
        clear(-1);
    }

    int capacity() {
        return held.length;
    }

    int tick() {
        return tick;
    }

    boolean restart() {
        return restart;
    }

    boolean switchesInvulnerability() {
        return switchesInvulnerability;
    }

    boolean invulnerable() {
        return invulnerable;
    }

    /** Empties this for a new tick. */
    Bundle clear(int newTick) {
        tick = newTick;
        restart = false;
        switchesInvulnerability = false;
        invulnerable = false;
        for (int slot = 0; slot < held.length; slot++) {
            present[slot] = false;
            for (int type = 1; type <= Msg.EVENT_TYPES; type++) {
                hasEvent[type][slot] = false;
                eventValue[type][slot] = 0;
                eventExtra[type][slot] = 0;
            }
            joins[slot] = false;
            joinMachines[slot] = 0;
            goesAway[slot] = false;
            comesBack[slot] = false;
        }
        return this;
    }

    /** This slot holds these {@link #HELD_BYTES} bytes this tick: copied from {@code source} at {@code at}. */
    Bundle hold(int slot, byte[] source, int at) {
        System.arraycopy(source, at, held[slot], 0, HELD_BYTES);
        present[slot] = true;
        return this;
    }

    boolean holds(int slot) {
        return present[slot];
    }

    /** The bytes this slot holds - the bundle's own, not a copy. */
    byte[] heldBytes(int slot) {
        return held[slot];
    }

    /** This slot has an event of this type: one of each type a slot a tick. */
    Bundle event(int slot, int type, int value, int extra) {
        hasEvent[type][slot] = true;
        eventValue[type][slot] = value;
        eventExtra[type][slot] = extra;
        return this;
    }

    boolean hasEvent(int slot, int type) {
        return hasEvent[type][slot];
    }

    int eventValue(int slot, int type) {
        return eventValue[type][slot];
    }

    int eventExtra(int slot, int type) {
        return eventExtra[type][slot];
    }

    /**
     * Refuses an event no bundle could carry: a type nobody has, or a value that does not fit the bytes its
     * type gives it. What it means is not asked - that is for the peers.
     */
    static void checkEvent(int type, int value, int extra) {
        if (type < 1 || type > Msg.EVENT_TYPES) {
            throw new WireException("no input event of type " + type);
        }
        if (type != Msg.EVENT_PICK && (value < 0 || value > 255)) {
            throw new WireException("an event value of " + value + " in a byte");
        }
        if (extra < 0 || extra > 255) {
            throw new WireException("an event extra of " + extra + " in a byte");
        }
    }

    /** A player takes this slot, sitting at this machine - the peer that holds it (G0056). */
    Bundle join(int slot, int machine) {
        joins[slot] = true;
        joinMachines[slot] = machine;
        return this;
    }

    boolean joins(int slot) {
        return joins[slot];
    }

    int joinMachine(int slot) {
        return joinMachines[slot];
    }

    /** This slot's link dropped: it is away from this tick (G0052). */
    Bundle away(int slot) {
        goesAway[slot] = true;
        return this;
    }

    boolean goesAway(int slot) {
        return goesAway[slot];
    }

    /** This slot's machine is back and re-seated: present again from this tick (G0052). */
    Bundle back(int slot) {
        comesBack[slot] = true;
        return this;
    }

    boolean comesBack(int slot) {
        return comesBack[slot];
    }

    Bundle restarting() {
        restart = true;
        return this;
    }

    Bundle switchInvulnerability(boolean on) {
        switchesInvulnerability = true;
        invulnerable = on;
        return this;
    }

    void write(WireWriter out) {
        out.putInt(tick);
        out.putByte((restart ? RESTART : 0)
                | (switchesInvulnerability ? SWITCHES_INVULNERABILITY : 0)
                | (invulnerable ? INVULNERABLE : 0));
        out.putByte(count(present));
        for (int slot = 0; slot < held.length; slot++) {
            if (present[slot]) {
                out.putByte(slot);
                out.putRaw(held[slot], 0, HELD_BYTES);
            }
        }
        writeEvents(out, Msg.EVENT_ORDER);
        out.putByte(count(joins));
        for (int slot = 0; slot < joins.length; slot++) {
            if (joins[slot]) {
                out.putByte(slot).putInt(joinMachines[slot]);
            }
        }
        writeEvents(out, Msg.EVENT_PICK);
        out.putByte(count(goesAway) + count(comesBack));
        for (int slot = 0; slot < goesAway.length; slot++) {
            if (goesAway[slot]) {
                out.putByte(slot).putByte(1);
            }
            if (comesBack[slot]) {
                out.putByte(slot).putByte(0);
            }
        }
        writeEvents(out, Msg.EVENT_CRATE);
    }

    private void writeEvents(WireWriter out, int type) {
        out.putByte(count(hasEvent[type]));
        for (int slot = 0; slot < held.length; slot++) {
            if (hasEvent[type][slot]) {
                out.putByte(slot);
                if (type == Msg.EVENT_PICK) {
                    out.putInt(eventValue[type][slot]).putByte(eventExtra[type][slot]);
                } else {
                    out.putByte(eventValue[type][slot]);
                }
            }
        }
    }

    /** Reads one bundle where the reader stands, leaving it at the next. */
    Bundle read(WireReader in) {
        clear(in.getInt());
        int flags = in.getByte();
        restart = (flags & RESTART) != 0;
        switchesInvulnerability = (flags & SWITCHES_INVULNERABILITY) != 0;
        invulnerable = (flags & INVULNERABLE) != 0;
        int heldCount = in.getByte();
        for (int i = 0; i < heldCount; i++) {
            int slot = slot(in.getByte());
            in.copyTo(held[slot], 0, HELD_BYTES);
            present[slot] = true;
        }
        readEvents(in, Msg.EVENT_ORDER);
        int joinCount = in.getByte();
        for (int i = 0; i < joinCount; i++) {
            int slot = slot(in.getByte());
            joins[slot] = true;
            joinMachines[slot] = in.getInt();
        }
        readEvents(in, Msg.EVENT_PICK);
        int presenceCount = in.getByte();
        for (int i = 0; i < presenceCount; i++) {
            int slot = slot(in.getByte());
            if (awayFromCode(in.getByte())) {
                goesAway[slot] = true;
            } else {
                comesBack[slot] = true;
            }
        }
        readEvents(in, Msg.EVENT_CRATE);
        return this;
    }

    private void readEvents(WireReader in, int type) {
        int count = in.getByte();
        for (int i = 0; i < count; i++) {
            int slot = slot(in.getByte());
            if (type == Msg.EVENT_PICK) {
                int value = in.getInt();
                event(slot, type, value, in.getByte());
            } else {
                event(slot, type, in.getByte(), 0);
            }
        }
    }

    /**
     * Steps over one bundle where the reader stands, checking every slot in it without building anything,
     * and returns its tick. How a peer files a bundle away to play later: kept as the bytes it came in,
     * decoded once, when it is played.
     */
    static int skip(WireReader in, int capacity) {
        int tick = in.getInt();
        in.getByte();
        int heldCount = in.getByte();
        for (int i = 0; i < heldCount; i++) {
            checkSlot(in.getByte(), capacity);
            in.skip(HELD_BYTES);
        }
        skipEvents(in, capacity);
        return tick;
    }

    /**
     * Writes {@code bundle} as the next one in a datagram after {@code previous}: every seat with only the
     * fields of its held input that changed from {@code previous} - its slot and which fields follow in one
     * byte - and everything else as it is. Never longer than the bundle itself. Held input changes little
     * from one tick to the next, and a datagram carries several ticks running: the 2026-10-01 playtest sent
     * every seat's whole input three times a tick, 87% of what a session sent.
     *
     * @param bundle a whole bundle, as {@link #write} wrote it
     * @param previous the whole bundle sent just before it in the same datagram
     */
    static void writeChanged(WireWriter out, byte[] bundle, int length, byte[] previous) {
        out.putRaw(bundle, 0, HEADER_BYTES);
        int seats = bundle[HEADER_BYTES - 1] & 0xFF;
        for (int i = 0; i < seats; i++) {
            int at = HEADER_BYTES + i * SEAT_BYTES;
            int slot = bundle[at] & 0xFF;
            int was = seatAt(previous, 0, slot);
            int changed = 0;
            for (int f = 0; f < FIELD_AT.length; f++) {
                int from = at + 1 + FIELD_AT[f];
                int then = was + 1 + FIELD_AT[f];
                if (was < 0
                        || !Arrays.equals(bundle, from, from + FIELD_BYTES[f], previous, then, then + FIELD_BYTES[f])) {
                    changed |= 1 << f;
                }
            }
            out.putByte(slot | changed << SLOT_BITS);
            for (int f = 0; f < FIELD_AT.length; f++) {
                if ((changed & 1 << f) != 0) {
                    out.putRaw(bundle, at + 1 + FIELD_AT[f], FIELD_BYTES[f]);
                }
            }
        }
        int rest = HEADER_BYTES + seats * SEAT_BYTES;
        out.putRaw(bundle, rest, length - rest);
    }

    /**
     * Reads a bundle {@link #writeChanged} wrote and writes it whole into {@code whole}, taking every field
     * it left out from {@code previous}; returns its tick. A peer files bundles away whole, so nothing after
     * this - the ring, a desync dump - knows a datagram carried less.
     *
     * @param previous the whole bundle before it in the same datagram, starting at {@code previousAt}
     */
    static int rebuild(WireReader in, byte[] previous, int previousAt, int capacity, WireWriter whole) {
        int tick = in.getInt();
        int flags = in.getByte();
        int seats = in.getByte();
        whole.putInt(tick).putByte(flags).putByte(seats);
        for (int i = 0; i < seats; i++) {
            int seat = in.getByte();
            int slot = checkSlot(seat & SLOT_MASK, capacity);
            int changed = seat >>> SLOT_BITS;
            int was = seatAt(previous, previousAt, slot);
            if (was < 0 && changed != ALL_FIELDS) {
                throw new WireException("slot " + slot + " unchanged from a tick that did not hold it");
            }
            whole.putByte(slot);
            for (int f = 0; f < FIELD_AT.length; f++) {
                if ((changed & 1 << f) != 0) {
                    int at = in.position();
                    in.skip(FIELD_BYTES[f]);
                    whole.putRaw(in.array(), at, FIELD_BYTES[f]);
                } else {
                    whole.putRaw(previous, was + 1 + FIELD_AT[f], FIELD_BYTES[f]);
                }
            }
        }
        int rest = in.position();
        skipEvents(in, capacity);
        whole.putRaw(in.array(), rest, in.position() - rest);
        return tick;
    }

    /** Where in the whole bundle starting at {@code at} the seat of {@code slot} starts, or -1. */
    private static int seatAt(byte[] bundle, int at, int slot) {
        int seats = bundle[at + HEADER_BYTES - 1] & 0xFF;
        for (int i = 0; i < seats; i++) {
            int seat = at + HEADER_BYTES + i * SEAT_BYTES;
            if ((bundle[seat] & 0xFF) == slot) {
                return seat;
            }
        }
        return -1;
    }

    /** Steps over a bundle's orders, joins, picks, presence and crates, checking each slot. */
    private static void skipEvents(WireReader in, int capacity) {
        skipSection(in, capacity, 1);
        skipSection(in, capacity, 4);
        skipSection(in, capacity, 5);
        int presenceCount = in.getByte();
        for (int i = 0; i < presenceCount; i++) {
            checkSlot(in.getByte(), capacity);
            awayFromCode(in.getByte());
        }
        skipSection(in, capacity, 1);
    }

    /** A count, then that many of a slot and {@code bodyBytes} more. */
    private static void skipSection(WireReader in, int capacity, int bodyBytes) {
        int count = in.getByte();
        for (int i = 0; i < count; i++) {
            checkSlot(in.getByte(), capacity);
            in.skip(bodyBytes);
        }
    }

    /** Whether a presence change is a slot going away rather than coming back. */
    private static boolean awayFromCode(int code) {
        if (code > 1) {
            throw new WireException("presence " + code + " is neither away nor back");
        }
        return code == 1;
    }

    private int slot(int slot) {
        return checkSlot(slot, held.length);
    }

    private static int checkSlot(int slot, int capacity) {
        if (slot >= capacity) {
            throw new WireException("slot " + slot + " in a session of " + capacity);
        }
        return slot;
    }

    private static int count(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) {
            count += flag ? 1 : 0;
        }
        return count;
    }
}
