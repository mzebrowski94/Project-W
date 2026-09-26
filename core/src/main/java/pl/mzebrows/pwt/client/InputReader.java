package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.CommanderInput;
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
 * or a level starts, so the click on RESUME does not fire and the {@code SOUTH} on it does not act. And
 * while a menu is open on this machine its commanders stand still: {@code neutral} keeps where each is
 * aiming and releases everything else.
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

    private boolean zoomToggled;
    private boolean rollCallToggled;
    private boolean pauseRequested;

    public InputReader(Viewport viewport, ControlsConfig controls, GamepadSource gamepads) {
        this.viewport = viewport;
        this.controls = controls;
        this.gamepads = gamepads;
        for (int seat = 0; seat < PlayerSeats.SEATS; seat++) {
            latchedPads[seat] = new LatchedGamepad(latch, seat, controls.feel().triggerThreshold());
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
                return pad.axis(GamepadAxis.LEFT_TRIGGER) >= controls.feel().triggerThreshold();
            }
            if (LatchedGamepad.triggerInput(seat, GamepadAxis.RIGHT_TRIGGER) == input) {
                return pad.axis(GamepadAxis.RIGHT_TRIGGER) >= controls.feel().triggerThreshold();
            }
        }
        return false;
    }

    /** Samples the devices as they are right now, one slot at a time. */
    public PlayerInputs read(KeyHolds held, KeyPresses pressed) {
        return read(held, pressed, false);
    }

    /**
     * Samples the devices, spent presses masked.
     *
     * @param neutral a menu is open on this machine: its commanders keep their aim and hold nothing
     */
    public PlayerInputs read(KeyHolds rawHeld, KeyPresses pressed, boolean neutral) {
        KeyHolds held = keycode -> latch.filter(keycode, rawHeld.held(keycode));
        seats.refresh(gamepads);
        inputs.clear();
        zoomToggled = ViewControls.zoomToggled(pressed, controls.keyboard());
        rollCallToggled = ViewControls.rollCallToggled(pressed, controls.keyboard());
        pauseRequested = false;

        pointer.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(pointer);
        inputs.set(
                        PlayerSeats.KEYBOARD_SLOT,
                        KeyControls.read(
                                controls.keyboard(),
                                held,
                                mouse(controls.keyboard().fireButton()),
                                mouse(controls.keyboard().strikeButton()),
                                pointer.x,
                                pointer.y))
                .order(PlayerSeats.KEYBOARD_SLOT, OrderKeys.poll(pressed, controls.keyboard()));

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
            zoomToggled |= pads.zoomToggled();
            pauseRequested |= pads.pauseRequested();
        }
        if (neutral) {
            for (int slot = 0; slot < PlayerSeats.SEATS; slot++) {
                CommanderInput was = inputs.get(slot);
                inputs.set(
                                slot,
                                new CommanderInput(
                                        0f,
                                        0f,
                                        was.aimX(),
                                        was.aimY(),
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        was.aimRelative()))
                        .order(slot, null);
            }
            zoomToggled = false;
        }
        return inputs;
    }

    private boolean mouse(int button) {
        return latch.filter(LatchedGamepad.MOUSE + button, Gdx.input.isButtonPressed(button));
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
