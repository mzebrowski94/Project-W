package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a tree's foliage over its trunk (G0072).
 *
 * <p>Two discs round the trunk, both scaled from it: an inner one, opaque, that hides everything beneath it - a man,
 * a creature, a shot, a stun - and an outer one, see-through, that half hides it. Drawn over everything else on the
 * field, the view pulled back included; the trunk is drawn over the foliage in its own colour, so what stops a man
 * and his shots is always seen (pillar 5). The simulation publishes only the trunk (G0003).
 *
 * @param innerScale the inner disc's radius, as a multiple of the trunk's
 * @param outerScale the outer disc's radius, as a multiple of the trunk's
 * @param outerAlpha how opaque the outer disc is
 * @param inner what the inner disc is drawn in; always opaque
 * @param outer what the outer disc is drawn in, before its alpha
 */
public record TreeMark(float innerScale, float outerScale, float outerAlpha, Color inner, Color outer) {

    public TreeMark {
        if (innerScale < 1f) {
            throw new IllegalArgumentException("tree innerScale must be at least 1, was " + innerScale);
        }
        if (outerScale < innerScale) {
            throw new IllegalArgumentException(
                    "tree outerScale must be at least innerScale " + innerScale + ", was " + outerScale);
        }
        if (outerAlpha < 0f || outerAlpha > 1f) {
            throw new IllegalArgumentException("tree outerAlpha must be in [0, 1], was " + outerAlpha);
        }
    }

    /** The inner disc's radius round a trunk of this radius: what it hides entirely. */
    public float innerFor(float trunk) {
        return trunk * innerScale;
    }

    /** The outer disc's radius round a trunk of this radius. */
    public float outerFor(float trunk) {
        return trunk * outerScale;
    }
}
