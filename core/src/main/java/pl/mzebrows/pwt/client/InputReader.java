package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;

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
 */
public final class InputReader {

    private final Viewport viewport;
    private final ControlsConfig controls;
    private final GamepadSource gamepads;
    private final PlayerSeats seats = new PlayerSeats();

    /** One per slot, kept across frames: a pad remembers where it was pointing and what it last held. */
    private final @Nullable PadControls[] padControls = new PadControls[Players.MAX];

    /** Reused, so reading a frame of input allocates nothing but the records it fills in. */
    private final PlayerInputs inputs = new PlayerInputs();

    private final Vector3 pointer = new Vector3();

    private boolean zoomToggled;
    private boolean pauseRequested;

    public InputReader(Viewport viewport, ControlsConfig controls, GamepadSource gamepads) {
        this.viewport = viewport;
        this.controls = controls;
        this.gamepads = gamepads;
    }

    /** Samples the devices as they are right now, one slot at a time. */
    public PlayerInputs read(KeyHolds held, KeyPresses pressed) {
        seats.refresh(gamepads);
        inputs.clear();
        zoomToggled = ViewControls.zoomToggled(pressed, controls.keyboard());
        pauseRequested = false;

        pointer.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(pointer);
        inputs.set(
                PlayerSeats.KEYBOARD_SLOT,
                KeyControls.read(
                        controls.keyboard(),
                        held,
                        pressed,
                        Gdx.input.isButtonPressed(controls.keyboard().fireButton()),
                        Gdx.input.isButtonPressed(controls.keyboard().strikeButton()),
                        pointer.x,
                        pointer.y));

        for (int slot = PlayerSeats.KEYBOARD_SLOT + 1; slot < Players.MAX; slot++) {
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
            inputs.set(slot, pads.read(pad));
            zoomToggled |= pads.zoomToggled();
            pauseRequested |= pads.pauseRequested();
        }
        return inputs;
    }

    /** Who is sitting where, so a new arrival can be given a commander. */
    public PlayerSeats seats() {
        return seats;
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
