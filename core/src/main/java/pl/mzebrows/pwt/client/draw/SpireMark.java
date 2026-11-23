package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a spire (G0113): a tall dark column seen from above - its body the palette's {@code graphic.color.spire}
 * - with a lighter cap in its middle. Nothing shows where it fires next: its pattern is learnt by watching it.
 *
 * @param cap the cap's colour
 * @param capShare how wide the cap is, a share of its radius
 */
public record SpireMark(Color cap, float capShare) {

    public SpireMark {
        if (capShare <= 0f || capShare >= 1f) {
            throw new IllegalArgumentException("a spire's cap sits inside it");
        }
    }

    /** How wide the cap is on a spire of this radius. */
    public float capFor(float radius) {
        return radius * capShare;
    }
}
