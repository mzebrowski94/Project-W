package pl.mzebrows.pwt.net.session;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * What the bytes of a {@link Bundle} mean to a peer (ADR 0023): a slot's held input as a
 * {@link CommanderInput}, and an event's numbers as an order, a card or a pickup. The sequencer never
 * reads any of it; only a peer that runs the world does, so a number that names nothing is ignored - the
 * same on every peer, which is all lockstep asks - rather than refused by a sequencer that cannot know.
 *
 * <p>Held input crosses the wire the same for everyone - aim as a float's raw bits, movement as a whole
 * number of sixteen-thousandths of a push - so the host's own input, which goes through the same encoding,
 * can never round differently from anybody else's. A value that is not a finite number becomes zero: a
 * stick reporting NaN would otherwise put NaN into a position on every peer at once.
 *
 * <p>An instance remembers each slot's last input, so a slot whose bytes have not changed is handed back
 * as the same object and an idle or steady player allocates nothing.
 */
final class InputCodec {

    /** A crate's event that takes nothing and shuts it (G0056). */
    static final int CRATE_CLOSE = 0;

    /**
     * Movement goes as a whole number of sixteen-thousandths of a push: two bytes rather than four, and a
     * full push, its halves and its quarters stay exact.
     */
    private static final float PUSH_STEPS = 16_384f;

    /**
     * Eight held buttons, one a bit - the dash bit 64 (G0107), redeploy bit 128 (G0109). The byte is full: a ninth held
     * button needs another byte of {@link Bundle#HELD_BYTES} and a protocol bump.
     */
    private static final int BUTTON_BITS = 255;

    private static final Order[] ORDERS = Order.values();

    private static final UpgradeKind[] KINDS = UpgradeKind.values();

    private static final PickupKind[] PICKUPS = PickupKind.values();

    private final CommanderInput[] last;

    InputCodec(int capacity) {
        last = new CommanderInput[capacity];
        Arrays.fill(last, CommanderInput.IDLE);
    }

    /** Hands this tick to a world: clears {@code inputs} and fills it from {@code bundle}. */
    void applyTo(Bundle bundle, PlayerInputs inputs) {
        inputs.clear();
        for (int slot = 0; slot < last.length; slot++) {
            if (bundle.holds(slot)) {
                last[slot] = readHeld(bundle.heldBytes(slot), 0, last[slot]);
                inputs.set(slot, last[slot]);
            }
            inputs.order(
                    slot,
                    bundle.hasEvent(slot, Msg.EVENT_ORDER)
                            ? orderFromCode(bundle.eventValue(slot, Msg.EVENT_ORDER))
                            : null);
            if (bundle.joins(slot)) {
                inputs.join(slot, bundle.joinMachine(slot));
            }
            if (bundle.hasEvent(slot, Msg.EVENT_PICK)) {
                UpgradeKind picked = pickFromCode(bundle.eventExtra(slot, Msg.EVENT_PICK));
                if (picked != null) {
                    inputs.pick(slot, bundle.eventValue(slot, Msg.EVENT_PICK), picked);
                }
            }
            if (bundle.goesAway(slot)) {
                inputs.away(slot);
            }
            if (bundle.comesBack(slot)) {
                inputs.back(slot);
            }
            if (bundle.hasEvent(slot, Msg.EVENT_CRATE)) {
                int code = bundle.eventValue(slot, Msg.EVENT_CRATE);
                if (code == CRATE_CLOSE) {
                    inputs.closeCrate(slot);
                } else {
                    PickupKind taken = crateFromCode(code);
                    if (taken != null) {
                        inputs.takeFromCrate(slot, taken);
                    }
                }
            }
        }
        if (bundle.switchesInvulnerability()) {
            inputs.switchInvulnerability(bundle.invulnerable());
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

    /**
     * Reads {@link Bundle#HELD_BYTES} bytes of held input, handing back {@code previous} itself when
     * nothing has changed. Bytes no peer writes - a push past a whole one, a button nobody has - are
     * clamped or dropped, the same everywhere.
     */
    static CommanderInput readHeld(byte[] bytes, int at, CommanderInput previous) {
        float moveX = pushFrom(shortAt(bytes, at));
        float moveY = pushFrom(shortAt(bytes, at + 2));
        float aimX = finite(Float.intBitsToFloat(intAt(bytes, at + 4)));
        float aimY = finite(Float.intBitsToFloat(intAt(bytes, at + 8)));
        int buttons = (bytes[at + 12] & 0xFF) & BUTTON_BITS;
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
                (buttons & 32) != 0,
                (buttons & 64) != 0,
                (buttons & 128) != 0);
    }

    @SuppressWarnings("EnumOrdinal") // both ends run the same build - the handshake refuses anything else
    static int orderCode(Order order) {
        return order.ordinal() + 1;
    }

    static @Nullable Order orderFromCode(int code) {
        return code < 1 || code > ORDERS.length ? null : ORDERS[code - 1];
    }

    @SuppressWarnings("EnumOrdinal") // both ends run the same build - the handshake refuses anything else
    static int pickCode(UpgradeKind kind) {
        return kind.ordinal() + 1;
    }

    static @Nullable UpgradeKind pickFromCode(int code) {
        return code < 1 || code > KINDS.length ? null : KINDS[code - 1];
    }

    @SuppressWarnings("EnumOrdinal") // both ends run the same build - the handshake refuses anything else
    static int crateCode(PickupKind kind) {
        return kind.ordinal() + 1;
    }

    static @Nullable PickupKind crateFromCode(int code) {
        return code < 1 || code > PICKUPS.length ? null : PICKUPS[code - 1];
    }

    private static int shortAt(byte[] bytes, int at) {
        return (short) ((bytes[at] & 0xFF) << 8 | (bytes[at + 1] & 0xFF));
    }

    private static int intAt(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24
                | (bytes[at + 1] & 0xFF) << 16
                | (bytes[at + 2] & 0xFF) << 8
                | (bytes[at + 3] & 0xFF);
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
                | (input.aimRelative() ? 32 : 0)
                | (input.dashing() ? 64 : 0)
                | (input.redeploying() ? 128 : 0);
    }

    private static int push(float move) {
        float within = Float.isFinite(move) ? Math.max(-1f, Math.min(1f, move)) : 0f;
        return Math.round(within * PUSH_STEPS);
    }

    private static float pushFrom(int steps) {
        return Math.max(-PUSH_STEPS, Math.min(PUSH_STEPS, steps)) / PUSH_STEPS;
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }
}
