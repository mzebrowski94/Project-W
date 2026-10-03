package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a unit whose gun goes off where its shots land.
 *
 * <p>A ring around the body, drawn as an outline so it never hides the unit inside it - the same
 * shape {@link OrderMark} uses, and in the same pass. The simulation publishes a flag and says
 * nothing about how it should look (G0003).
 *
 * <p><strong>A colour of its own rather than a shift of the body's.</strong> An order ring says
 * something about a unit the player already knows is his; this says a creature has become a
 * different problem, and it has to be findable at a glance in a line of otherwise identical ones.
 * The default is the barrel's own orange-red, so the ring is the colour of the thing it swallowed.
 *
 * @param scale how far out the ring sits, as a multiple of the body's radius
 * @param color what the ring is drawn in
 */
public record ChargedMark(float scale, Color color) {

    public ChargedMark {
        if (scale <= 0f) {
            throw new IllegalArgumentException("charged mark scale must be positive, was " + scale);
        }
    }

    /** The radius of the ring for a body of the given radius. */
    public float radiusFor(float bodyRadius) {
        return bodyRadius * scale;
    }
}
