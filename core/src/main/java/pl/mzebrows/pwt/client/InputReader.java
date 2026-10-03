package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.ui.nav.ReleaseLatch;

/**
 * Reads every device once per frame and packages them as the simulation's per-tick input.
 *
 * <p>This is the only class that touches the input devices - the simulation is handed a value and
 * never polls anything, which {@code ArchitectureTest} enforces. It is also, deliberately, the only
 * class here that decides nothing: what a key means is {@link KeyControls}, what a pad's stick means
 * is {@link PadControls}, and who is sitting where is {@link PlayerSeats}, all three of which are
 * unit-tested because none of them can see a window.
 *
 * <p>Player one is the keyboard and mouse, and a pad never takes his seat in the alpha (1.a). Every
 * other slot is a pad, seated by plugging it in.
 *
 * <p><b>A press that changes context is spent</b> (plan 6.2): everything read here for the game goes
 * through a {@link ReleaseLatch}, armed by {@link #spendHeldPresses()} whenever a menu opens or closes
 * or a level starts, so the click on RESUME does not fire and the {@code SOUTH} on it does not act. What the
 * input means for the top bar, and holding everybody still under a menu, is {@code TopBarSeats}'s.
 */
public final class InputReader {

    private final Viewport viewport;
    private final ControlsConfig controls;
    private final GamepadSource gamepads;
    private final PlayerSeats seats = new PlayerSeats();
    private final ReleaseLatch latch = new ReleaseLatch(LatchedGamepad.INPUTS);
    private final LatchedGamepad[] latchedPads = new LatchedGamepad[PlayerSeats.SEATS];

    /** One per slot, kept across frames: a pad remembers where it was pointing and what it last held. */
    private final @Nullable PadControls[] padControls = new PadControls[PlayerSeats.SEATS];

    /** Reused, so reading a frame of input allocates nothing but the records it fills in. */
    private final PlayerInputs inputs = new PlayerInputs();

    private final Vector3 pointer = new Vector3();

    /** Which seats pressed their draft key this frame: the keyboard's, or a pad's. */
    private final boolean[] draftPressed = new boolean[PlayerSeats.SEATS];

    /** Whether the keyboard's crate key went down this frame; a pad shuts its crate with an order. */
    private boolean crateClosePressed;

    private boolean zoomToggled;
    private boolean rollCallToggled;
    private boolean pauseRequested;

    public InputReader(Viewport viewport, ControlsConfig controls, GamepadSource gamepads) {
        this.viewport = viewport;
        this.controls = controls;
        this.gamepads = gamepads;
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            latchedPads[seat] = new LatchedGamepad(latch, seat, controls.pad().triggerThreshold());
        }
    }

    /**
     * Spends every key, mouse button, pad button and trigger held right now: the game ignores each until
     * it has been let go. Call on every change of context.
     */
    public void spendHeldPresses() {
        latch.arm(this::heldNow);
    }

    private boolean heldNow(int input) {
        if (input < LatchedGamepad.MOUSE) {
            return Gdx.input.isKeyPressed(input);
        }
        if (input < LatchedGamepad.PADS) {
            return Gdx.input.isButtonPressed(input - LatchedGamepad.MOUSE);
        }
        for (int seat = 1; seat < PlayerSeats.SEATS; seat++) {
            Gamepad pad = seats.padIn(seat);
            if (pad == null) {
                continue;
            }
            for (GamepadButton button : GamepadButton.values()) {
                if (LatchedGamepad.buttonInput(seat, button) == input) {
                    return pad.pressed(button);
                }
            }
            if (LatchedGamepad.triggerInput(seat, GamepadAxis.LEFT_TRIGGER) == input) {
                return pad.axis(GamepadAxis.LEFT_TRIGGER) >= controls.pad().triggerThreshold();
            }
            if (LatchedGamepad.triggerInput(seat, GamepadAxis.RIGHT_TRIGGER) == input) {
                return pad.axis(GamepadAxis.RIGHT_TRIGGER) >= controls.pad().triggerThreshold();
            }
        }
        return false;
    }

    /** Samples the devices, spent presses masked: each seat's input, by seat, as the devices gave it. */
    public PlayerInputs read(KeyHolds rawHeld, KeyPresses pressed) {
        KeyHolds held = keycode -> latch.filter(keycode, rawHeld.held(keycode));
        seats.refresh(gamepads);
        inputs.clear();
        zoomToggled = ViewControls.zoomToggled(pressed, controls.key());
        rollCallToggled = ViewControls.rollCallToggled(pressed, controls.key());
        pauseRequested = false;
        Arrays.fill(draftPressed, false);
        crateClosePressed = pressed.justPressed(controls.key().crateClose());

        pointer.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(pointer);
        inputs.set(
                        PlayerSeats.KEYBOARD_SLOT,
                        KeyControls.read(
                                controls.key(),
                                held,
                                mouse(controls.mouse().fire().code()),
                                mouse(controls.mouse().strike().code()),
                                pointer.x,
                                pointer.y))
                .order(PlayerSeats.KEYBOARD_SLOT, OrderKeys.poll(pressed, controls.key()));
        draftPressed[PlayerSeats.KEYBOARD_SLOT] =
                pressed.justPressed(controls.key().draft());

        for (int slot = PlayerSeats.KEYBOARD_SLOT + 1; slot < PlayerSeats.SEATS; slot++) {
            Gamepad pad = seats.padIn(slot);
            if (pad == null) {
                continue;
            }
            PadControls pads = padControls[slot];
            if (pads == null) {
                pads = new PadControls(controls);
                padControls[slot] = pads;
                Log.info("player " + (slot + 1) + ": "
                        + PadLabels.briefing(controls.pad(), PadLabels.isPlayStation(pad.name())));
            }
            inputs.set(slot, pads.read(latchedPads[slot].over(pad))).order(slot, pads.ordered());
            draftPressed[slot] = pads.drafted();
            zoomToggled |= pads.zoomToggled();
            pauseRequested |= pads.pauseRequested();
        }
        return inputs;
    }

    private boolean mouse(int button) {
        return latch.filter(LatchedGamepad.MOUSE + button, Gdx.input.isButtonPressed(button));
    }

    /** Which seats pressed their draft key this frame, by seat. Read it before the next {@link #read}. */
    public boolean[] draftPressed() {
        return draftPressed;
    }

    /** Whether the keyboard's crate key went down this frame. */
    public boolean crateClosePressed() {
        return crateClosePressed;
    }

    /**
     * What this seat is told to press for its draft, in its own device's words: the key, or the button
     * as his pad names it. Allocates; asked when the tell changes, not per frame.
     */
    public String draftKeyLabel(int seat) {
        if (seat == PlayerSeats.KEYBOARD_SLOT) {
            return controls.key().draft().name();
        }
        Gamepad pad = seats.padIn(seat);
        return PadLabels.of(
                controls.pad().draft(),
                controls.pad().draftTrigger(),
                pad != null && PadLabels.isPlayStation(pad.name()));
    }

    /** Whether the pad in this seat names its buttons the PlayStation way; false for the keyboard. */
    public boolean playStation(int seat) {
        Gamepad pad = seat == PlayerSeats.KEYBOARD_SLOT ? null : seats.padIn(seat);
        return pad != null && PadLabels.isPlayStation(pad.name());
    }

    /** Who is sitting where, so a new arrival can be given a commander. */
    public PlayerSeats seats() {
        return seats;
    }

    /** True on the frame the keyboard asked to swap the bottom bar's view. */
    public boolean rollCallToggled() {
        return rollCallToggled;
    }

    /** True on the frame anybody asked to swap between the normal and pulled-back view. */
    public boolean zoomToggled() {
        return zoomToggled;
    }

    /** True on the frame anybody asked to stop or start the clock from a pad. */
    public boolean pauseRequested() {
        return pauseRequested;
    }

    /** Where the view should lean, which is where the players are walking between them. */
    public float headingX() {
        float sum = 0f;
        for (int slot = 0; slot < Players.MAX; slot++) {
            sum += inputs.get(slot).moveX();
        }
        return sum;
    }

    /** The other half of {@link #headingX()}. */
    public float headingY() {
        float sum = 0f;
        for (int slot = 0; slot < Players.MAX; slot++) {
            sum += inputs.get(slot).moveY();
        }
        return sum;
    }
}
