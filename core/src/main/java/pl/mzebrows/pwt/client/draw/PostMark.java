package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a post - ground the level says you must hold: an outline just round the point it was taken from,
 * pulsing gently between the post's own dark green and a light one, under everything standing on it (pillar 5).
 * An outline rather than a disc, because a post is put down mid-fight and a filled one covered the fight.
 *
 * <p>The pulse runs on the client's own clock; the simulation says only that a post is there (G0003).
 *
 * @param width how thick the outline is, as a multiple of the post's radius
 * @param segments how many straight pieces it is drawn in
 * @param light the colour it pulses up to; the post's palette colour is the one it pulses down to
 * @param pulseHz how many times a second it pulses
 */
public record PostMark(float width, int segments, Color light, float pulseHz) {

    public PostMark {
        if (width <= 0f) {
            throw new IllegalArgumentException("post mark width must be positive, was " + width);
        }
        if (segments < 3) {
            throw new IllegalArgumentException("post mark segments must be at least three, was " + segments);
        }
        if (!(pulseHz >= 0f && pulseHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException(
                    "a post may pulse at most " + GraphicConfig.MAX_PULSE_HZ + " times a second, not " + pulseHz);
        }
    }

    /** How thick the outline is round a post of this radius. */
    public float widthFor(float radius) {
        return radius * width;
    }

    /** Where the outline runs, so all of it lies outside the point it stands round. */
    public float ringFor(float radius) {
        return radius + widthFor(radius) * 0.5f;
    }

    /** How far towards {@link #light} it is this many seconds in, in {@code [0, 1]}: a sine, so it eases. */
    public float lightAt(float seconds) {
        return 0.5f - 0.5f * (float) Math.cos(seconds * pulseHz * 2f * Math.PI);
    }
}
