package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A place a squad takes by standing near it.
 *
 * <p>Capture is <strong>dwell</strong>, and <strong>the progress resets when the last capturer
 * leaves</strong> - not decays. Once taken it stays taken; recapture is not designed.
 */
public final class ControlPoint implements Component, Pool.Poolable {

    /** How far from the middle counts as standing on it, in world units. */
    public float radius;

    /** How long somebody has to stay inside it, in seconds. */
    public float captureSeconds;

    /** How far the noise of it being taken carries, in world units. */
    public float aggroRadius;

    /** How long it has been held so far this attempt, in seconds. Back to zero the moment it is empty. */
    public float heldSeconds;

    /** Whether it has been taken. One way, for now. */
    public boolean captured;

    /**
     * Which of the level's points this was to be taken - one for the first - so the rally point can be
     * the point taken last (G0055). Zero while it is untaken.
     */
    public int takenOrder;

    /**
     * Whether taking this one turns it into ground the level then tells you to hold.
     *
     * <p>A post is put down where it stands and what the level held back is let go (G0027).
     */
    public boolean holdsAfterwards;

    /** How far around the post it becomes must be clear, in world units. Zero means the whole level. */
    public float watchRadius;

    /** Sets what it takes to hold this one, and returns this component, for fluent spawning. */
    public ControlPoint set(float newRadius, float newCaptureSeconds, float newAggroRadius) {
        this.radius = newRadius;
        this.captureSeconds = newCaptureSeconds;
        this.aggroRadius = newAggroRadius;
        return this;
    }

    /** Makes this the point a march ends on: taking it puts a post down and starts what was held back. */
    public ControlPoint holdAfterwards(float newWatchRadius) {
        this.holdsAfterwards = true;
        this.watchRadius = newWatchRadius;
        return this;
    }

    /**
     * How far along this attempt is, in {@code [0, 1]}, for the renderer to draw (pillar 5).
     *
     * <p>A captured point reports full, so it does not read as untouched.
     */
    public float progress() {
        if (captured) {
            return 1f;
        }
        if (captureSeconds <= 0f) {
            return 0f;
        }
        float fraction = heldSeconds / captureSeconds;
        return fraction < 1f ? fraction : 1f;
    }

    @Override
    public void reset() {
        radius = 0f;
        captureSeconds = 0f;
        aggroRadius = 0f;
        heldSeconds = 0f;
        captured = false;
        takenOrder = 0;
        holdsAfterwards = false;
        watchRadius = 0f;
    }
}
