package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Something in the air off a fixed throw: how much of its flight is left, and what it has just come
 * off.
 *
 * <p><strong>A distance, not a countdown.</strong> A thrown thing travels the same total distance
 * whatever it bounces off, which is what makes the throw learnable: the reach is the same every time,
 * and a wall changes where it ends up rather than how far it went.
 *
 * <p>A bounce is <em>recorded</em> here by contact resolution and acted on afterwards, exactly as
 * damage owed and a pickup claim are. The solver knows two circles overlapped and nothing about what
 * a bounce means - not even whether this thing is still heading into what it touched.
 *
 * <p>Normals are summed rather than replaced, so something wedged into a corner comes off the corner
 * rather than off whichever of the two walls the collision grid happened to visit last.
 */
public final class Thrown implements Component, Pool.Poolable {

    /** How fast it travels, in world units per second. A bounce changes the direction and not this. */
    public float speed;

    public float remainingDistance;

    /** The surface it touched this tick, pointing away from whatever it touched. Not normalised. */
    public float bounceX;

    public float bounceY;

    /** True if anything was recorded this tick. Distinct from a zero normal, which is two centres. */
    public boolean bounced;

    /** Sets the throw and returns this component, for fluent spawning. */
    public Thrown set(float newSpeed, float newRange) {
        this.speed = newSpeed;
        this.remainingDistance = newRange;
        this.bounceX = 0f;
        this.bounceY = 0f;
        this.bounced = false;
        return this;
    }

    /** Records a surface this has run into, to be come off once something knows which way it is going. */
    public void bounceOff(float normalX, float normalY) {
        this.bounceX += normalX;
        this.bounceY += normalY;
        this.bounced = true;
    }

    /** Clears this tick's contacts, once they have been answered. */
    public void settled() {
        this.bounceX = 0f;
        this.bounceY = 0f;
        this.bounced = false;
    }

    @Override
    public void reset() {
        speed = 0f;
        remainingDistance = 0f;
        bounceX = 0f;
        bounceY = 0f;
        bounced = false;
    }
}
