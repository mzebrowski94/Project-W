package pl.mzebrows.pwt.client.draw;

/**
 * How to draw the parts trailing a slithering head - the desert snake's (G0087): each a see-through disc in the
 * body's colour, with a smaller and denser one inside it, so a tail reads as something the shots go through and the
 * head, drawn whole, as the part that is hit.
 *
 * @param alpha how solid a part's outer disc is
 * @param coreScale the radius of the disc inside it, as a fraction of the part's
 * @param coreAlpha how solid that inner disc is - more than the outer
 */
public record SlitherMark(float alpha, float coreScale, float coreAlpha) {

    public SlitherMark {
        if (alpha < 0f || alpha > 1f || coreAlpha < 0f || coreAlpha > 1f) {
            throw new IllegalArgumentException("slither alphas must be in [0, 1], were " + alpha + ", " + coreAlpha);
        }
        if (coreScale <= 0f || coreScale > 1f) {
            throw new IllegalArgumentException("slither coreScale must be in (0, 1], was " + coreScale);
        }
    }
}
