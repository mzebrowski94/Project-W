package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A place a squad takes by standing near it.
 *
 * <p>Capture is <strong>dwell</strong>: somebody on the player's side has to be inside the radius,
 * and stay there. It is not a race to touch it, and it is not a thing one man does instantly - the
 * seconds it takes are the seconds the level gets to send something at you, which is the only reason
 * a point is interesting at all.
 *
 * <p><strong>The progress resets when the last capturer leaves.</strong> Not decays - resets. A
 * partial capture that quietly persists means the player is tracking a number across minutes, and
 * pillar 5 would then need it drawn permanently; an all-or-nothing dwell is one thing to see and one
 * thing to defend. It is also the version that makes an enemy pushing a trooper off the point worth
 * doing.
 *
 * <p>Once taken it stays taken. Nothing in the game recaptures a point yet, and whether the other
 * side can is a question about what the aliens are trying to do, which is not designed.
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
     * Whether taking this one turns it into ground the level then tells you to hold.
     *
     * <p>False on every point on every level built so far, which is what "take it and move on"
     * looks like as data. True on one: the last ground of a march, where capturing is not the end of
     * the level but the thing that starts the last fight of it. A post is put down where it stands
     * and whatever the level was holding back is let go.
     *
     * <p>It is on the point rather than on the level because <em>which</em> ground does this is a
     * fact about that ground - and because a post that existed from the first tick would send every
     * sleeping creature on the level walking at it, and would turn "forward" round for the whole
     * march (G0027).
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
     * <p>A captured point reports full rather than nothing, so it reads as taken rather than as
     * untouched - the two must not look alike.
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
        holdsAfterwards = false;
        watchRadius = 0f;
    }
}
