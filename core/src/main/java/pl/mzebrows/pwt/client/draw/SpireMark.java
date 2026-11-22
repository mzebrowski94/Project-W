package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a spire (G0113): a tall dark column seen from above - its body the palette's {@code graphic.color.spire}
 * - with a lighter cap in its middle, and a short tick on its edge at each bearing its next step fires along, so the
 * pattern reads before it is fired (pillar 5). Sizes are shares of its radius.
 *
 * @param cap the cap's colour
 * @param capShare how wide the cap is
 * @param tell the tick's colour
 * @param tellLength how far in from its edge the tick reaches
 * @param tellWidth how thick the tick is
 */
public record SpireMark(Color cap, float capShare, Color tell, float tellLength, float tellWidth) {

    public SpireMark {
        if (capShare <= 0f || capShare >= 1f || tellLength <= 0f || tellLength > 1f || tellWidth <= 0f) {
            throw new IllegalArgumentException("a spire's cap sits inside it and its tick is on its edge");
        }
    }

    /** How wide the cap is on a spire of this radius. */
    public float capFor(float radius) {
        return radius * capShare;
    }

    /** Where the tick starts, from the middle of a spire of this radius; it ends on the edge. */
    public float tellFromFor(float radius) {
        return radius * (1f - tellLength);
    }

    /** How thick the tick is on a spire of this radius. */
    public float tellWidthFor(float radius) {
        return radius * tellWidth;
    }
}
