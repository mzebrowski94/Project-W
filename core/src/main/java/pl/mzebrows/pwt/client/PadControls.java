package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * What one gamepad is asking for, as one tick of {@link CommanderInput}.
 *
 * <p>One of these per seated player, kept across frames, because three of the things a pad needs are
 * not in a single frame's worth of state:
 *
 * <ul>
 *   <li><strong>The aim it remembers.</strong> Letting go of the pointing stick must leave him
 *       pointing where he was, not swing his aim onto himself. A mouse has nowhere else to be; a
 *       stick springs back to centre, and a stick at centre is a player who has stopped turning
 *       rather than a player aiming at his own feet.
 *   <li><strong>The d-pad edges.</strong> An order is given once and then stands on whoever heard it
 *       (G0017), so a held direction must not keep recruiting whatever walks into earshot.
 *   <li><strong>Pause and zoom edges</strong>, for the same reason.
 * </ul>
 *
 * <p>It touches no libGDX application state - only a {@link Gamepad}, which is the port - so it is
 * unit-tested against a pad made of plain values. That is also why it is named {@code Controls}:
 * {@code ClientArchitectureTest} fails the build if anything with that name reaches for {@code Gdx}.
 *
 * <p>The aim it sends is a <strong>direction of length one</strong>, marked relative, which the
 * simulation turns into a place by adding the commander's own position. Length one rather than how
 * hard the stick is pushed, because aim is a direction and never a distance - a half-pushed stick
 * turns him exactly as far as a fully pushed one, and only walking is analogue.
 */
public final class PadControls {

    private final ControlsConfig.Pad bindings;
    private final ControlsConfig.Feel feel;

    /** Where he was last told to point. Starts pointing right, which is where a fresh commander looks. */
    private float aimX = 1f;

    private float aimY;

    private boolean zoomWasDown;
    private boolean pauseWasDown;
    private boolean zoomToggled;
    private boolean pauseRequested;
    private boolean draftWasDown;
    private boolean drafted;
    private @Nullable Order ordered;

    private final boolean[] orderWasDown = new boolean[4];

    public PadControls(ControlsConfig controls) {
        this.bindings = controls.pad();
        this.feel = controls.feel();
    }

    /** Samples the pad as it is right now, and notes any view or harness press for the caller to collect. */
    public CommanderInput read(Gamepad pad) {
        float rawMoveX = pad.axis(bindings.moveX());
        float rawMoveY = pad.axis(bindings.moveY());
        float moveScale = scale(rawMoveX, rawMoveY, feel.moveDeadZone());

        aim(pad);

        boolean firing = held(pad, bindings.fire());
        boolean striking = held(pad, bindings.strike());
        boolean throwing = held(pad, bindings.grenade());
        boolean acting = held(pad, bindings.action());
        boolean dragging = held(pad, bindings.drag());

        zoomToggled = wentDown(pad, bindings.zoom(), zoomWasDown);
        zoomWasDown = held(pad, bindings.zoom());
        pauseRequested = wentDown(pad, bindings.pause(), pauseWasDown);
        pauseWasDown = held(pad, bindings.pause());
        drafted = wentDown(pad, bindings.draft(), draftWasDown);
        draftWasDown = held(pad, bindings.draft());
        ordered = order(pad);

        return new CommanderInput(
                rawMoveX * moveScale,
                rawMoveY * moveScale,
                aimX,
                aimY,
                firing,
                striking,
                throwing,
                acting,
                dragging,
                true);
    }

    /**
     * The order this pad gave on the last {@link #read}, or null. An input event rather than held
     * state, so it travels beside the held input rather than inside it (ADR 0012).
     */
    public @Nullable Order ordered() {
        return ordered;
    }

    /** True on the frame this pad asked to swap between the normal and pulled-back view. */
    public boolean zoomToggled() {
        return zoomToggled;
    }

    /** True on the frame this pad pressed its draft button: open his draft, or take the card. */
    public boolean drafted() {
        return drafted;
    }

    /** True on the frame this pad asked to stop or start the clock. */
    public boolean pauseRequested() {
        return pauseRequested;
    }

    /** Turns the pointing stick into a direction, or leaves the last one alone if it is at rest. */
    private void aim(Gamepad pad) {
        float x = pad.axis(bindings.aimX());
        float y = pad.axis(bindings.aimY());
        float length = (float) Math.sqrt(x * x + y * y);
        if (length <= feel.aimDeadZone()) {
            return;
        }
        aimX = x / length;
        aimY = y / length;
    }

    private @Nullable Order order(Gamepad pad) {
        if (wentDown(pad, bindings.orderFollowMe(), orderWasDown[0])) {
            return remember(pad, Order.FOLLOW_ME);
        }
        if (wentDown(pad, bindings.orderForward(), orderWasDown[1])) {
            return remember(pad, Order.FORWARD);
        }
        if (wentDown(pad, bindings.orderDefence(), orderWasDown[2])) {
            return remember(pad, Order.DEFENCE);
        }
        if (wentDown(pad, bindings.orderCancel(), orderWasDown[3])) {
            return remember(pad, Order.CANCEL);
        }
        rememberOrderButtons(pad);
        return null;
    }

    private Order remember(Gamepad pad, Order given) {
        rememberOrderButtons(pad);
        return given;
    }

    private void rememberOrderButtons(Gamepad pad) {
        orderWasDown[0] = held(pad, bindings.orderFollowMe());
        orderWasDown[1] = held(pad, bindings.orderForward());
        orderWasDown[2] = held(pad, bindings.orderDefence());
        orderWasDown[3] = held(pad, bindings.orderCancel());
    }

    /**
     * Whether this action is being asked for, by either half of its binding.
     *
     * <p>One question for every action, because a trigger is not a second-class button here: the hand
     * strike lives on one, and firing is a face button and a trigger at once. Where the line between
     * squeezed and released falls is a number in {@code controls.properties} rather than a fact about
     * the hardware.
     */
    private boolean held(Gamepad pad, ControlsConfig.PadBinding binding) {
        return pad.pressed(binding.button()) || pad.axis(binding.trigger()) >= feel.triggerThreshold();
    }

    private boolean wentDown(Gamepad pad, ControlsConfig.PadBinding binding, boolean wasDown) {
        return held(pad, binding) && !wasDown;
    }

    /**
     * What to multiply both halves of a stick by, having taken the dead zone off.
     *
     * <p><strong>Radial rather than per-axis.</strong> Testing each axis against the dead zone on its
     * own makes a diagonal push cross the line sooner than a straight one, and players feel that as
     * the stick snapping to the axes. Taking the dead zone off the length and rescaling what is left
     * over the rest of the range keeps full push meaning full speed in every direction.
     */
    private static float scale(float x, float y, float deadZone) {
        float length = (float) Math.sqrt(x * x + y * y);
        if (length <= deadZone) {
            return 0f;
        }
        float capped = Math.min(length, 1f);
        return (capped - deadZone) / (1f - deadZone) / length;
    }
}
