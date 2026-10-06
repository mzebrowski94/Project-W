package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a stun something declared, as it runs out (G0053, G0071).
 *
 * <p>A pale, see-through disc round the stunned body that shrinks onto it as the stun is spent, so how long the
 * window has left reads at a glance (pillar 5) - on top of the darkening the state already draws. Every declared
 * stun gets it, the pulse rifle's shock and a thumper's slam alike; a stagger or a lock does not.
 *
 * @param scale how big the disc is the instant the stun lands, as a multiple of the body's radius
 * @param alpha how opaque it is
 * @param color what it is drawn in, before its alpha
 */
public record DazeMark(float scale, float alpha, Color color) {

    public DazeMark {
        if (scale < 1f) {
            throw new IllegalArgumentException("daze scale must be at least 1, was " + scale);
        }
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("daze alpha must be in [0, 1], was " + alpha);
        }
    }

    /** The disc's radius round a body of this radius with {@code left} of the stun to run, in {@code [0, 1]}. */
    public float radiusFor(float bodyRadius, float left) {
        return bodyRadius * (1f + (scale - 1f) * left);
    }
}
