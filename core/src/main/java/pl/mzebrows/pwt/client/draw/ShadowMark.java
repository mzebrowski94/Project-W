package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a shadow under whatever is off the ground - a slung rider (G0074) and a thrown grenade (G0077): a disc
 * on the ground below it and to one side, so what is in the air reads as in the air. One look for both, so it is
 * learnt once.
 *
 * @param offset how far below and to the right of the thing its shadow lies, as a fraction of its radius
 * @param scale the shadow's radius, as a fraction of the thing's
 * @param alpha how dark the shadow is
 * @param color what the shadow is drawn in, before its alpha
 */
public record ShadowMark(float offset, float scale, float alpha, Color color) {

    public ShadowMark {
        if (offset < 0f || scale <= 0f) {
            throw new IllegalArgumentException("shadow offset must not be negative and scale must be positive");
        }
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("shadow alpha must be in [0, 1], was " + alpha);
        }
    }
}
