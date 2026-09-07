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
 */
@RequiredArgsConstructor
public final class InputReader {

    private final Viewport viewport;

    /** Reused across frames so reading input allocates nothing but the returned record. */
    private final Vector3 pointer = new Vector3();

    /** Samples the devices as they are right now. */
    public CommanderInput read() {
        float moveX = axis(Input.Keys.D, Input.Keys.RIGHT, Input.Keys.A, Input.Keys.LEFT);
        float moveY = axis(Input.Keys.W, Input.Keys.UP, Input.Keys.S, Input.Keys.DOWN);

        pointer.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        viewport.unproject(pointer);

        boolean firing = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        return new CommanderInput(moveX, moveY, pointer.x, pointer.y, firing);
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
