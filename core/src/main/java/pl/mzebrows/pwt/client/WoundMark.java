package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw the wounds a man on his feet is carrying (G0053).
 *
 * <p>One dot per wound in a row under his feet, so the count is read rather than guessed - the player
 * never tracks a number the game does not show (pillar 5). The simulation publishes the count and says
 * nothing about how it should look (G0003). Filled circles in the same pass as the body, in a colour of
 * their own: a wound asks for a reaction nothing else on a man does.
 *
 * @param size how big one dot is, as a multiple of the body's radius
 * @param drop how far below his middle the row sits, as a multiple of the body's radius
 * @param gap how far apart two dots are, centre to centre, as a multiple of the body's radius
 * @param color what a wound is drawn in
 */
public record WoundMark(float size, float drop, float gap, Color color) {

    public WoundMark {
        if (size <= 0f) {
            throw new IllegalArgumentException("wound mark size must be positive, was " + size);
        }
        if (gap <= 0f) {
            throw new IllegalArgumentException("wound mark gap must be positive, was " + gap);
        }
    }

    /** The radius of one dot for a body of the given radius. */
    public float radiusFor(float bodyRadius) {
        return bodyRadius * size;
    }

    /** Where the row sits below his middle for a body of the given radius. */
    public float dropFor(float bodyRadius) {
        return bodyRadius * drop;
    }

    /** Where the dot at {@code index} of {@code count} sits across from his middle, centring the row. */
    public float offsetFor(float bodyRadius, int index, int count) {
        return (index - (count - 1) * 0.5f) * gap * bodyRadius;
    }
}
