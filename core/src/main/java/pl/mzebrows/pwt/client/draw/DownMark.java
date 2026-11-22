package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw the **down mark** (G0109): a black disc with a bold red X on it - a saltire, never a red cross - over a
 * commander who is down, see-through, for every player on every machine. The one red mark over the view. The
 * simulation says only that he wears it ({@code Appearance.downMarked}); nothing about how it looks (G0003).
 *
 * @param alpha how opaque the whole mark is, in {@code [0, 1]}
 * @param radius the disc's radius, in world units - about a commander's
 * @param lift how far above the top of his body the disc's foot is, in world units
 * @param ring the disc's colour
 * @param cross the X's colour
 * @param stroke how thick each bar of the X is, in world units; less than the disc is wide
 * @param reach how far from the middle each bar of the X reaches, as a share of the radius: below one, so a rim of the
 *     disc shows round it
 */
public record DownMark(float alpha, float radius, float lift, Color ring, Color cross, float stroke, float reach) {

    public DownMark {
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("down mark alpha must be in [0, 1], was " + alpha);
        }
        if (radius <= 0f || lift < 0f) {
            throw new IllegalArgumentException("a down mark's radius must be positive and its lift not negative");
        }
        if (stroke <= 0f || stroke >= radius) {
            throw new IllegalArgumentException("down mark stroke must be in (0, radius), was " + stroke);
        }
        if (reach <= 0f || reach >= 1f) {
            throw new IllegalArgumentException("down mark reach must be in (0, 1), was " + reach);
        }
    }

    /** How high above his middle the disc's centre is, for a body of this radius. */
    public float centreAbove(float bodyRadius) {
        return bodyRadius + lift + radius;
    }

    /** How high above his middle the disc's top is, for a body of this radius - where a prompt over it starts. */
    public float topAbove(float bodyRadius) {
        return centreAbove(bodyRadius) + radius;
    }

    /** How far along each diagonal a bar of the X reaches from the middle, in world units. */
    public float barReach() {
        return radius * reach;
    }
}
