package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.ui.nav.ReleaseLatch;

/**
 * A seat's pad as the game sees it: every button and trigger held across a context change reads as
 * released until it has been let go once ({@link ReleaseLatch}). Sticks pass through - a stick held
 * over from a menu is a direction, not a press. One per seat, pointed at whichever pad sits there.
 */
final class LatchedGamepad implements Gamepad {

    /** Keys, then mouse buttons, then each seat's pad buttons and triggers - one range for the latch. */
    static final int MOUSE = 256;

    static final int PADS = MOUSE + 8;

    private static final int PER_SEAT = GamepadButton.values().length + 2;

    static final int INPUTS = PADS + PlayerSeats.SEATS * PER_SEAT;

    private final ReleaseLatch latch;
    private final int seat;
    private final float triggerThreshold;
    private @Nullable Gamepad pad;

    LatchedGamepad(ReleaseLatch latch, int seat, float triggerThreshold) {
        this.latch = latch;
        this.seat = seat;
        this.triggerThreshold = triggerThreshold;
    }

    LatchedGamepad over(Gamepad live) {
        this.pad = live;
        return this;
    }

    @Override
    public String id() {
        return live().id();
    }

    @Override
    public String name() {
        return live().name();
    }

    @Override
    public float axis(GamepadAxis axis) {
        float value = live().axis(axis);
        int trigger = triggerInput(seat, axis);
        if (trigger < 0) {
            return value;
        }
        return latch.filter(trigger, value >= triggerThreshold) ? value : 0f;
    }

    @Override
    public boolean pressed(GamepadButton button) {
        return latch.filter(buttonInput(seat, button), live().pressed(button));
    }

    @SuppressWarnings("EnumOrdinal")
    static int buttonInput(int seat, GamepadButton button) {
        return PADS + seat * PER_SEAT + button.ordinal();
    }

    /** The latch's number for a trigger, or -1 for a stick. */
    static int triggerInput(int seat, GamepadAxis axis) {
        int base = PADS + seat * PER_SEAT + GamepadButton.values().length;
        return switch (axis) {
            case LEFT_TRIGGER -> base;
            case RIGHT_TRIGGER -> base + 1;
            default -> -1;
        };
    }

    private Gamepad live() {
        Gamepad live = pad;
        if (live == null) {
            throw new IllegalStateException("no pad in seat " + seat);
        }
        return live;
    }
}
