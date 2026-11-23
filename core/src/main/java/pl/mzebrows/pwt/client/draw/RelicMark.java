package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a relic and its wardens (G0114). The plinth is the palette's {@code graphic.color.relic}, a disc its body;
 * over it, until the relic is taken, a square of {@code glow} turning slowly in a fainter halo of the same gold - gone
 * once it is taken, the plinth left bare. A warden is an oval in {@code graphic.color.warden}: its body is a circle (ADR
 * 0007), its width that circle's and its length {@code wardenLength} times that, north to south.
 *
 * @param glow the square's colour, a warm gold
 * @param size how wide the square is, a share of the plinth's radius
 * @param spinDegreesPerSecond how far it turns a second, on the client's clock times {@code graphic.ui.motionScale}
 * @param halo how wide the halo round it is, a share of the square's width
 * @param haloAlpha how see-through the halo is: its alpha
 * @param wardenLength how long a warden's oval is, a share of its width
 */
public record RelicMark(
        Color glow, float size, float spinDegreesPerSecond, float halo, float haloAlpha, float wardenLength) {

    public RelicMark {
        if (size <= 0f || halo < 1f || haloAlpha < 0f || haloAlpha > 1f || wardenLength <= 0f) {
            throw new IllegalArgumentException(
                    "a relic's square is positive, its halo at least as wide and its alpha a share; a warden's oval positive");
        }
    }

    /** How wide the square is over a plinth of this radius. */
    public float squareFor(float radius) {
        return radius * size;
    }

    /** How far it has turned this many seconds in, in degrees; still at no motion. */
    public float angleAt(float seconds, float motionScale) {
        return spinDegreesPerSecond * seconds * motionScale;
    }
}
