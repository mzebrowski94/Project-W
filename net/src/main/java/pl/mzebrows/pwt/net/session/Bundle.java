package pl.mzebrows.pwt.net.session;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * One sealed tick (ADR 0012, "tick bundle"): what every seated slot is holding, the input events the
 * sequencer stamped on it, and the session's own - a restart, a switch of invulnerability. Every peer
 * steps its world with exactly this, which is the whole of lockstep.
 *
 * <p><strong>Held input crosses the wire losslessly</strong> - a float goes as its raw bits - so the
 * host's own input, which goes through the same encoding, can never round differently from anybody
 * else's. The one change made to it is that a value that is not a finite number becomes zero: a stick
 * reporting NaN would otherwise put NaN into a position on every peer at once.
 *
 * <p>Mutable and reused, for the reason {@link PlayerInputs} is: one is decoded per tick. A slot's held
 * input is only rebuilt when its bits have changed, so an idle or steady player allocates nothing.
 *
 * <p>Layout: tick (int); flags (byte: 1 restart, 2 switches invulnerability, 4 invulnerable); held
 * count, then per slot its number and 17 bytes (four floats, then a byte of buttons); order count,
 * then per order a slot and an order; join count, then the slots.
 */
final class Bundle {

    /** The bytes one slot's held input takes, its slot number not included. */
    static final int HELD_BYTES = 17;

    private static final Order[] ORDERS = Order.values();

    private static final int RESTART = 1;
    private static final int SWITCHES_INVULNERABILITY = 2;
    private static final int INVULNERABLE = 4;

    private final CommanderInput[] held;
    private final boolean[] present;
    private final @Nullable Order[] orders;
    private final boolean[] joins;

    private int tick;
    private boolean restart;
    private boolean switchesInvulnerability;
    private boolean invulnerable;

    Bundle(int capacity) {
        held = new CommanderInput[capacity];
        present = new boolean[capacity];
        orders = new Order[capacity];
        joins = new boolean[capacity];
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

    Bundle join(int slot) {
        joins[slot] = true;
        return this;
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
                inputs.join(slot);
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
                out.putByte(slot);
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
            joins[slot(in.getByte())] = true;
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
        int orderCount = in.getByte();
        for (int i = 0; i < orderCount; i++) {
            checkSlot(in.getByte(), capacity);
            orderFromCode(in.getByte());
        }
        int joinCount = in.getByte();
        for (int i = 0; i < joinCount; i++) {
            checkSlot(in.getByte(), capacity);
        }
        return tick;
    }

    /** Writes one slot's held input: four floats as raw bits, then its buttons. */
    static void writeHeld(WireWriter out, CommanderInput input) {
        out.putInt(Float.floatToRawIntBits(input.moveX()))
                .putInt(Float.floatToRawIntBits(input.moveY()))
                .putInt(Float.floatToRawIntBits(input.aimX()))
                .putInt(Float.floatToRawIntBits(input.aimY()))
                .putByte(buttons(input));
    }

    /** Reads one slot's held input, handing back {@code previous} itself when nothing has changed. */
    static CommanderInput readHeld(WireReader in, CommanderInput previous) {
        float moveX = finite(Float.intBitsToFloat(in.getInt()));
        float moveY = finite(Float.intBitsToFloat(in.getInt()));
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
