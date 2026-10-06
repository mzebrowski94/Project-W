package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw the circle a slam covers, for the moment after it lands (G0071).
 *
 * <p>A white outline over a pale fill, both fading as the simulation's {@code burstFade} runs down: the ground
 * the thumper's shots reached, read at a glance as one circle rather than as the ring of shots it is (pillar 5).
 * The simulation publishes the reach and the fade and says nothing about how they should look (G0003).
 *
 * @param fillAlpha how opaque the fill is the instant the slam lands
 * @param outlineAlpha how opaque the outline is the instant the slam lands
 * @param color what the circle is drawn in, before its alpha
 */
public record BurstMark(float fillAlpha, float outlineAlpha, Color color) {

    public BurstMark {
        if (fillAlpha < 0f || fillAlpha > 1f) {
            throw new IllegalArgumentException("burst fillAlpha must be in [0, 1], was " + fillAlpha);
        }
        if (outlineAlpha < 0f || outlineAlpha > 1f) {
            throw new IllegalArgumentException("burst outlineAlpha must be in [0, 1], was " + outlineAlpha);
        }
    }
}
