package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a shield in front of a man (G0053).
 *
 * <p>An outline ring outside every other ring, in a colour of its own, so it never hides the man inside
 * it and cannot be read as an order or a charged gun. A shield that runs out on its own is drawn as
 * much of the ring as it has left, so the player can see when he is on his own again; one that lasts
 * until it is hit is the whole ring. The simulation publishes how much is left and nothing about how it
 * looks (G0003).
 *
 * @param scale how far out the ring sits, as a multiple of the body's radius
 * @param segments how many straight pieces a whole ring is drawn in
 * @param color what a shield is drawn in
 */
public record ShieldMark(float scale, int segments, Color color) {

    public ShieldMark {
        if (scale <= 0f) {
            throw new IllegalArgumentException("shield mark scale must be positive, was " + scale);
        }
        if (segments < 3) {
            throw new IllegalArgumentException("shield mark segments must be at least three, was " + segments);
        }
    }

    /** The radius of the ring for a body of the given radius. */
    public float radiusFor(float bodyRadius) {
        return bodyRadius * scale;
    }

    /** How many of the ring's pieces to draw for this much shield left, in {@code [0, 1]}. */
    public int segmentsFor(float fraction) {
        return Math.max(1, Math.round(segments * Math.min(1f, fraction)));
    }

    /** The angle, in degrees from straight up and going clockwise, where piece {@code step} ends. */
    public float angleAt(int step) {
        return 90f - step * 360f / segments;
    }
}
