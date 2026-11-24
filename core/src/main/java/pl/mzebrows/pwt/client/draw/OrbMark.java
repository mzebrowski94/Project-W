package pl.mzebrows.pwt.client.draw;

/**
 * How to draw an orb (G0128): a green ball - its body, the one part that touches anything - inside a thin ring drawn
 * outside it, its middle a little off the ball's, flickering between the orb's green and white on the client's clock;
 * the ball dims as its power is spent, down to {@code dimmest} of its colour, so what is left is seen (pillar 5).
 *
 * @param ring the ring's radius, as a multiple of the orb's; above one, so it lies outside the body that collides
 * @param ringWidth how thick the ring is, as a multiple of the orb's radius
 * @param ringOffset how far the ring's middle sits off the ball's, as a multiple of the orb's radius
 * @param flickerHz how many times a second the ring flickers green to white and back
 * @param dimmest how much of its colour a spent orb keeps, in {@code (0, 1]}
 */
public record OrbMark(float ring, float ringWidth, float ringOffset, float flickerHz, float dimmest) {

    public OrbMark {
        if (ring <= 1f || ringWidth <= 0f || ringOffset < 0f) {
            throw new IllegalArgumentException(
                    "an orb's ring lies outside it, with a width: ring " + ring + ", width " + ringWidth);
        }
        if (!(flickerHz > 0f && flickerHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException("an orb's ring may flicker at most " + GraphicConfig.MAX_PULSE_HZ
                    + " times a second, not " + flickerHz);
        }
        if (!(dimmest > 0f && dimmest <= 1f)) {
            throw new IllegalArgumentException("a spent orb keeps some of its colour, at most all: " + dimmest);
        }
    }

    /** How white the ring is this many seconds into the client's clock, in {@code [0, 1]}: up and down once a flicker. */
    public float whiteAt(float seconds) {
        float turns = seconds * flickerHz;
        float phase = turns - (float) Math.floor(turns);
        return phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
    }

    /** How much of its colour an orb with this much of its power left is drawn in. */
    public float brightnessFor(float power) {
        return dimmest + (1f - dimmest) * Math.max(0f, Math.min(1f, power));
    }
}
