package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a relic and its wardens (G0114). The plinth is the palette's {@code graphic.color.relic}, a disc its body;
 * over it, until the relic is taken, a square of {@code glow} turning slowly in a fainter halo of the same gold - gone
 * once it is taken, the plinth left bare. A warden is a token seen from above: a disc of {@code graphic.color.warden}
 * its body (ADR 0007), over a disc of {@code wardenSide} the same size dropped {@code wardenDrop} of its radius to the
 * south - the token's side showing below its top - and on the top {@code wardenRings} concentric rings of {@code
 * wardenRing}, each {@code wardenRingWidth} of its radius wide and {@code wardenRingGap} of it in from the last.
 *
 * @param glow the square's colour, a warm gold
 * @param size how wide the square is, a share of the plinth's radius
 * @param spinDegreesPerSecond how far it turns a second, on the client's clock times {@code graphic.ui.motionScale}
 * @param halo how wide the halo round it is, a share of the square's width
 * @param haloAlpha how see-through the halo is: its alpha
 * @param wardenSide the colour of a warden's side, darker than its top
 * @param wardenDrop how far below its top a warden's side shows, a share of its radius
 * @param wardenRing the colour of the rings on a warden's top, lighter than it
 * @param wardenRings how many rings are on its top; zero is a plain top
 * @param wardenRingWidth how wide each ring is, a share of its radius
 * @param wardenRingGap how far in from the edge of its top - and from each ring to the next - a ring starts, a share of
 *     its radius
 */
public record RelicMark(
        Color glow,
        float size,
        float spinDegreesPerSecond,
        float halo,
        float haloAlpha,
        Color wardenSide,
        float wardenDrop,
        Color wardenRing,
        int wardenRings,
        float wardenRingWidth,
        float wardenRingGap) {

    public RelicMark {
        if (size <= 0f || halo < 1f || haloAlpha < 0f || haloAlpha > 1f) {
            throw new IllegalArgumentException(
                    "a relic's square is positive, its halo at least as wide and its alpha a share");
        }
        if (wardenDrop < 0f || wardenRings < 0 || wardenRingWidth <= 0f || wardenRingGap < wardenRingWidth) {
            throw new IllegalArgumentException(
                    "a warden's side drops a share of it, and its rings are positive and apart: each gap at least a ring wide");
        }
        if (wardenRings * wardenRingGap >= 1f) {
            throw new IllegalArgumentException("a warden's rings fit on its top: wardenRings * wardenRingGap below 1");
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

    /** How far below its top a warden of this radius shows its side. */
    public float dropFor(float radius) {
        return radius * wardenDrop;
    }

    /** The outer radius of a warden's {@code ring}th ring, counted from one at its edge. */
    public float ringOuterFor(float radius, int ring) {
        return radius * (1f - ring * wardenRingGap);
    }

    /** The inner radius of the same ring. */
    public float ringInnerFor(float radius, int ring) {
        return ringOuterFor(radius, ring) - radius * wardenRingWidth;
    }
}
