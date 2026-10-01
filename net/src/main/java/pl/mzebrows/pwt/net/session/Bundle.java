package pl.mzebrows.pwt.net.session;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * One sealed tick (ADR 0012, "tick bundle"): what every seated slot is holding, the input events the
 * sequencer stamped on it, and the session's own - a restart, a switch of invulnerability. Every peer
 * steps its world with exactly this, which is the whole of lockstep.
 *
 * <p><strong>Held input crosses the wire the same for everyone</strong> - aim as a float's raw bits,
 * movement as a whole number of sixteen-thousandths of a push - so the host's own input, which goes
 * through the same encoding, can never round differently from anybody else's. A value that is not a
 * finite number becomes zero: a stick reporting NaN would otherwise put NaN into a position on every peer
 * at once.
 *
 * <p>Mutable and reused, for the reason {@link PlayerInputs} is: one is decoded per tick. A slot's held
 * input is only rebuilt when its bits have changed, so an idle or steady player allocates nothing.
 *
 * <p>Layout: tick (int); flags (byte: 1 restart, 2 switches invulnerability, 4 invulnerable); held
 * count, then per slot its number and 13 bytes (two pushes as shorts, two aims as floats, then a byte of
 * buttons); order count,
 * then per order a slot and an order; join count, then per join a slot and the machine it sits at (int);
 * pick count, then per pick a slot, the
 * draft's serial (int) and the kind taken; presence count, then per change a slot and whether it goes
 * away (1) or comes back (0); crate count, then per event a slot and the pickup taken (its code) or
 * {@link #CRATE_CLOSE}.
 */
final class Bundle {

    /** The bytes one slot's held input takes, its slot number not included. */
    static final int HELD_BYTES = 13;

    /**
     * Movement goes as a whole number of sixteen-thousandths of a push: two bytes rather than four, and a
     * full push, its halves and its quarters stay exact.
     */
    private static final float PUSH_STEPS = 16_384f;

    /** Ahead of the seats in a bundle: its tick, its flags, and how many seats hold input. */
    private static final int HEADER_BYTES = 6;

    private static final int SEAT_BYTES = 1 + HELD_BYTES;

    /** Where each field of a seat's held input starts, and how long it is: two pushes, two aims, buttons. */
    private static final int[] FIELD_AT = {0, 2, 4, 8, 12};

    private static final int[] FIELD_BYTES = {2, 2, 4, 4, 1};

    private static final int ALL_FIELDS = (1 << FIELD_AT.length) - 1;

    /** A changed seat's slot and which of its fields follow share a byte: the slot is the low three bits. */
    private static final int SLOT_BITS = 3;

    private static final int SLOT_MASK = (1 << SLOT_BITS) - 1;

    private static final Order[] ORDERS = Order.values();

    private static final UpgradeKind[] KINDS = UpgradeKind.values();

    private static final PickupKind[] PICKUPS = PickupKind.values();

    /** A crate's event that takes nothing and shuts it (G0056). */
    static final int CRATE_CLOSE = 0;

    private static final int RESTART = 1;
    private static final int SWITCHES_INVULNERABILITY = 2;
    private static final int INVULNERABLE = 4;

    private final CommanderInput[] held;
    private final boolean[] present;
    private final @Nullable Order[] orders;
    private final boolean[] joins;
    private final int[] joinMachines;
    private final @Nullable UpgradeKind[] picks;
    private final int[] pickSerials;
    private final boolean[] goesAway;
    private final boolean[] comesBack;
    private final @Nullable PickupKind[] crateTakes;
    private final boolean[] crateCloses;

    private int tick;
    private boolean restart;
    private boolean switchesInvulnerability;
    private boolean invulnerable;

    Bundle(int capacity) {
        held = new CommanderInput[capacity];
        present = new boolean[capacity];
        orders = new Order[capacity];
        joins = new boolean[capacity];
        joinMachines = new int[capacity];
        picks = new UpgradeKind[capacity];
        pickSerials = new int[capacity];
        goesAway = new boolean[capacity];
        comesBack = new boolean[capacity];
        crateTakes = new PickupKind[capacity];
        crateCloses = new boolean[capacity];
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

    /** Empties this for a new tick. Held input is kept as the reference the next decode compares against. */
    Bundle clear(int newTick) {
        tick = newTick;
        restart = false;
        switchesInvulnerability = false;
        invulnerable = false;
        for (int slot = 0; slot < held.length; slot++) {
            if (held[slot] == null) {
                held[slot] = CommanderInput.IDLE;
            }
            present[slot] = false;
            orders[slot] = null;
            joins[slot] = false;
            joinMachines[slot] = 0;
            picks[slot] = null;
            pickSerials[slot] = 0;
            goesAway[slot] = false;
            comesBack[slot] = false;
            crateTakes[slot] = null;
            crateCloses[slot] = false;
        }
        return this;
    }

    Bundle hold(int slot, CommanderInput input) {
        held[slot] = input;
        present[slot] = true;
        return this;
    }

    Bundle order(int slot, Order order) {
        orders[slot] = order;
        return this;
    }

    @Nullable
    Order orderOf(int slot) {
        return orders[slot];
    }

    /** A player takes this slot, sitting at this machine - the peer that holds it (G0056). */
    Bundle join(int slot, int machine) {
        joins[slot] = true;
        joinMachines[slot] = machine;
        return this;
    }

    /** This slot takes the card of this kind from the draft with this serial (G0049). */
    Bundle pick(int slot, int serial, UpgradeKind kind) {
        picks[slot] = kind;
        pickSerials[slot] = serial;
        return this;
    }

    @Nullable
    UpgradeKind pickOf(int slot) {
        return picks[slot];
    }

    /** This slot's link dropped: it is away from this tick (G0052). */
    Bundle away(int slot) {
        goesAway[slot] = true;
        return this;
    }

    /** This slot's machine is back and re-seated: present again from this tick (G0052). */
    Bundle back(int slot) {
        comesBack[slot] = true;
        return this;
    }

    /** This slot's crate event (G0056): a pickup taken from the crate he has open, or {@link #CRATE_CLOSE}. */
    Bundle crate(int slot, int code) {
        if (code == CRATE_CLOSE) {
            crateCloses[slot] = true;
        } else {
            crateTakes[slot] = crateFromCode(code);
        }
        return this;
    }

    /** Whether this slot has a crate event this tick already: one a tick, as every other event. */
    boolean hasCrateEvent(int slot) {
        return crateTakes[slot] != null || crateCloses[slot];
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

    /** Hands this tick to a world: clears {@code inputs} and fills it from this bundle. */
    void applyTo(PlayerInputs inputs) {
        inputs.clear();
        for (int slot = 0; slot < held.length; slot++) {
            if (present[slot]) {
                inputs.set(slot, held[slot]);
            }
            inputs.order(slot, orders[slot]);
            if (joins[slot]) {
                inputs.join(slot, joinMachines[slot]);
            }
            UpgradeKind picked = picks[slot];
            if (picked != null) {
                inputs.pick(slot, pickSerials[slot], picked);
            }
            if (goesAway[slot]) {
                inputs.away(slot);
            }
            if (comesBack[slot]) {
                inputs.back(slot);
            }
            PickupKind taken = crateTakes[slot];
            if (taken != null) {
                inputs.takeFromCrate(slot, taken);
            }
            if (crateCloses[slot]) {
                inputs.closeCrate(slot);
            }
        }
        if (switchesInvulnerability) {
            inputs.switchInvulnerability(invulnerable);
        }
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
                writeHeld(out, held[slot]);
            }
        }
        int orderCount = 0;
        for (Order order : orders) {
            orderCount += order == null ? 0 : 1;
        }
        out.putByte(orderCount);
        for (int slot = 0; slot < orders.length; slot++) {
            Order order = orders[slot];
            if (order != null) {
                out.putByte(slot).putByte(orderCode(order));
            }
        }
        out.putByte(count(joins));
        for (int slot = 0; slot < joins.length; slot++) {
            if (joins[slot]) {
                out.putByte(slot).putInt(joinMachines[slot]);
            }
        }
        int pickCount = 0;
        for (UpgradeKind picked : picks) {
            pickCount += picked == null ? 0 : 1;
        }
        out.putByte(pickCount);
        for (int slot = 0; slot < picks.length; slot++) {
            UpgradeKind picked = picks[slot];
            if (picked != null) {
                out.putByte(slot).putInt(pickSerials[slot]).putByte(pickCode(picked));
            }
        }
        out.putByte(count(goesAway) + count(comesBack));
        for (int slot = 0; slot < goesAway.length; slot++) {
            if (goesAway[slot]) {
                out.putByte(slot).putByte(1);
            }
            if (comesBack[slot]) {
                out.putByte(slot).putByte(0);
            }
        }
        int crateCount = 0;
        for (int slot = 0; slot < crateTakes.length; slot++) {
            crateCount += hasCrateEvent(slot) ? 1 : 0;
        }
        out.putByte(crateCount);
        for (int slot = 0; slot < crateTakes.length; slot++) {
            PickupKind taken = crateTakes[slot];
            if (taken != null) {
                out.putByte(slot).putByte(crateCode(taken));
            } else if (crateCloses[slot]) {
                out.putByte(slot).putByte(CRATE_CLOSE);
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
            hold(slot, readHeld(in, held[slot]));
        }
        int orderCount = in.getByte();
        for (int i = 0; i < orderCount; i++) {
            int slot = slot(in.getByte());
            orders[slot] = orderFromCode(in.getByte());
        }
        int joinCount = in.getByte();
        for (int i = 0; i < joinCount; i++) {
            int slot = slot(in.getByte());
            joins[slot] = true;
            joinMachines[slot] = in.getInt();
        }
        int pickCount = in.getByte();
        for (int i = 0; i < pickCount; i++) {
            int slot = slot(in.getByte());
            pickSerials[slot] = in.getInt();
            picks[slot] = pickFromCode(in.getByte());
        }
        int presenceCount = in.getByte();
        for (int i = 0; i < presenceCount; i++) {
            int slot = slot(in.getByte());
            if (awayFromCode(in.getByte())) {
                goesAway[slot] = true;
            } else {
                comesBack[slot] = true;
            }
        }
        int crateCount = in.getByte();
        for (int i = 0; i < crateCount; i++) {
            int slot = slot(in.getByte());
            crate(slot, in.getByte());
        }
        return this;
    }

    /**
     * Steps over one bundle where the reader stands, checking every slot and order in it without
     * building anything, and returns its tick. How a peer files a bundle away to play later: kept as the
     * bytes it came in, decoded once, when it is played.
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

    /** Steps over a bundle's orders, joins, picks, presence and crates, checking each. */
    private static void skipEvents(WireReader in, int capacity) {
        int orderCount = in.getByte();
        for (int i = 0; i < orderCount; i++) {
            checkSlot(in.getByte(), capacity);
            orderFromCode(in.getByte());
        }
        int joinCount = in.getByte();
        for (int i = 0; i < joinCount; i++) {
            checkSlot(in.getByte(), capacity);
            in.getInt();
        }
        int pickCount = in.getByte();
        for (int i = 0; i < pickCount; i++) {
            checkSlot(in.getByte(), capacity);
            in.getInt();
            pickFromCode(in.getByte());
        }
        int presenceCount = in.getByte();
        for (int i = 0; i < presenceCount; i++) {
            checkSlot(in.getByte(), capacity);
            awayFromCode(in.getByte());
        }
        int crateCount = in.getByte();
        for (int i = 0; i < crateCount; i++) {
            checkSlot(in.getByte(), capacity);
            int code = in.getByte();
            if (code != CRATE_CLOSE) {
                crateFromCode(code);
            }
        }
    }

    /** Writes one slot's held input: its pushes in sixteen-thousandths, its aim as raw bits, its buttons. */
    static void writeHeld(WireWriter out, CommanderInput input) {
        out.putShort(push(input.moveX()))
                .putShort(push(input.moveY()))
                .putInt(Float.floatToRawIntBits(input.aimX()))
                .putInt(Float.floatToRawIntBits(input.aimY()))
                .putByte(buttons(input));
    }

    /** Reads one slot's held input, handing back {@code previous} itself when nothing has changed. */
    static CommanderInput readHeld(WireReader in, CommanderInput previous) {
        float moveX = pushFrom(in.getShort());
        float moveY = pushFrom(in.getShort());
        float aimX = finite(Float.intBitsToFloat(in.getInt()));
        float aimY = finite(Float.intBitsToFloat(in.getInt()));
        int buttons = in.getByte();
        if (buttons > 63) {
            throw new WireException("button bits " + buttons + " name buttons nobody has");
        }
        if (same(previous, moveX, moveY, aimX, aimY, buttons)) {
            return previous;
        }
        if (same(CommanderInput.IDLE, moveX, moveY, aimX, aimY, buttons)) {
            return CommanderInput.IDLE;
        }
        return new CommanderInput(
                moveX,
                moveY,
                aimX,
                aimY,
                (buttons & 1) != 0,
                (buttons & 2) != 0,
                (buttons & 4) != 0,
                (buttons & 8) != 0,
                (buttons & 16) != 0,
                (buttons & 32) != 0);
    }

    @SuppressWarnings("EnumOrdinal") // both ends run the same build - the handshake refuses anything else
    static int orderCode(Order order) {
        return order.ordinal() + 1;
    }

    static Order orderFromCode(int code) {
        if (code < 1 || code > ORDERS.length) {
            throw new WireException("no order " + code);
        }
        return ORDERS[code - 1];
    }

    @SuppressWarnings("EnumOrdinal") // both ends run the same build - the handshake refuses anything else
    static int pickCode(UpgradeKind kind) {
        return kind.ordinal() + 1;
    }

    static UpgradeKind pickFromCode(int code) {
        if (code < 1 || code > KINDS.length) {
            throw new WireException("no upgrade " + code);
        }
        return KINDS[code - 1];
    }

    @SuppressWarnings("EnumOrdinal") // both ends run the same build - the handshake refuses anything else
    static int crateCode(PickupKind kind) {
        return kind.ordinal() + 1;
    }

    static PickupKind crateFromCode(int code) {
        if (code < 1 || code > PICKUPS.length) {
            throw new WireException("no pickup " + code);
        }
        return PICKUPS[code - 1];
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

    private static boolean same(CommanderInput input, float moveX, float moveY, float aimX, float aimY, int buttons) {
        return Float.floatToRawIntBits(input.moveX()) == Float.floatToRawIntBits(moveX)
                && Float.floatToRawIntBits(input.moveY()) == Float.floatToRawIntBits(moveY)
                && Float.floatToRawIntBits(input.aimX()) == Float.floatToRawIntBits(aimX)
                && Float.floatToRawIntBits(input.aimY()) == Float.floatToRawIntBits(aimY)
                && buttons(input) == buttons;
    }

    private static int buttons(CommanderInput input) {
        return (input.firing() ? 1 : 0)
                | (input.striking() ? 2 : 0)
                | (input.throwing() ? 4 : 0)
                | (input.acting() ? 8 : 0)
                | (input.dragging() ? 16 : 0)
                | (input.aimRelative() ? 32 : 0);
    }

    private static int push(float move) {
        float within = Float.isFinite(move) ? Math.max(-1f, Math.min(1f, move)) : 0f;
        return Math.round(within * PUSH_STEPS);
    }

    private static float pushFrom(int steps) {
        if (steps < -PUSH_STEPS || steps > PUSH_STEPS) {
            throw new WireException("a push of " + steps + " steps, past a whole one");
        }
        return steps / PUSH_STEPS;
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    private static int count(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) {
            count += flag ? 1 : 0;
        }
        return count;
    }
}
