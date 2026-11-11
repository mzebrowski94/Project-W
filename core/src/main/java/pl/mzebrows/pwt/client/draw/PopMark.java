package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a pop (G0078): what the player's side has just taken by touch, in its own look, rising off where it was
 * taken and fading out as the simulation's {@code pop} runs down - the take seen, for a moment. The simulation says
 * how much of it is left and nothing about how that looks (G0003).
 *
 * @param rise how far it has risen by the time it is gone, as a multiple of its radius
 * @param alpha how opaque it is the instant it appears
 */
public record PopMark(float rise, float alpha) {

    public PopMark {
        if (rise < 0f) {
            throw new IllegalArgumentException("pop rise must not be negative, was " + rise);
        }
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("pop alpha must be in [0, 1], was " + alpha);
        }
    }

    /** How far above where it appeared it is drawn, for a pop of this radius with {@code left} of it to go. */
    public float riseFor(float radius, float left) {
        return rise * radius * (1f - left);
    }
}
