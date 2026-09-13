package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import lombok.RequiredArgsConstructor;
import pl.mzebrows.pwt.sim.CommanderInput;

/**
 * Reads the keyboard and mouse once per frame and packages them as the simulation's per-tick input
 * value object. This is the only class that touches the input devices - the simulation is handed a
 * record and never polls anything, which {@code ArchitectureTest} enforces.
 *
 * <p>Movement and aim are read independently (G0002): the mouse decides where the commander points
 * and the keys decide where it walks, and neither is derived from the other.
 *
 * <p>The grenade, action and drag keys are read the same way as the mouse buttons, and for the same
 * reason: how often a throw may happen is a {@code Thrower} cooldown in the simulation, and how long
 * a stabilise takes is a number on a casualty - neither is a question about key repeat.
 *
 * <p>Both mouse buttons are read as held rather than as pressed. The simulation rate-limits what they
 * do - a weapon cooldown, a melee cooldown - so a button that repeats is the simulation's decision to
 * make and not this class's.
 */
@RequiredArgsConstructor
public final class InputReader {

    private final Viewport viewport;

    /** Reused across frames so reading input allocates nothing but the returned record. */
    private final Vector3 pointer = new Vector3();

    /** Samples the devices as they are right now. */
    public CommanderInput read(KeyPresses keys) {
        float moveX = axis(Input.Keys.D, Input.Keys.RIGHT, Input.Keys.A, Input.Keys.LEFT);
        float moveY = axis(Input.Keys.W, Input.Keys.UP, Input.Keys.S, Input.Keys.DOWN);

        pointer.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(pointer);

        boolean firing = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean striking = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        boolean throwing = Gdx.input.isKeyPressed(Input.Keys.Q);
        boolean acting = Gdx.input.isKeyPressed(Input.Keys.E);
        boolean dragging = Gdx.input.isKeyPressed(Input.Keys.F);
        return new CommanderInput(
                moveX, moveY, pointer.x, pointer.y, firing, striking, throwing, acting, dragging, OrderKeys.poll(keys));
    }

    private static float axis(int positive, int positiveAlias, int negative, int negativeAlias) {
        float value = 0f;
        if (Gdx.input.isKeyPressed(positive) || Gdx.input.isKeyPressed(positiveAlias)) {
            value += 1f;
        }
        if (Gdx.input.isKeyPressed(negative) || Gdx.input.isKeyPressed(negativeAlias)) {
            value -= 1f;
        }
        return value;
    }
}
