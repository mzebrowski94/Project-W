package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw the wounds a man on his feet is carrying (G0053).
 *
 * <p>One red disc in the middle of his body, larger for every wound he carries, so a wounded man reads
 * as wounded at a glance and a badly wounded one as worse - the count itself is on his panel (pillar
 * 5). The simulation publishes the count and says nothing about how it should look (G0003). Filled, in
 * the same pass as the body and on top of it, in a colour of its own: a wound asks for a reaction
 * nothing else on a man does.
 *
 * @param size how big the disc is at one wound, as a multiple of the body's radius
 * @param growth how much bigger each further wound makes it, as a multiple of the body's radius
 * @param maxSize the most it grows to, as a multiple of the body's radius - it never covers the man
 * @param color what a wound is drawn in
 */
public record WoundMark(float size, float growth, float maxSize, Color color) {

    public WoundMark {
        if (size <= 0f) {
            throw new IllegalArgumentException("wound mark size must be positive, was " + size);
        }
        if (growth < 0f) {
            throw new IllegalArgumentException("wound mark growth must not be negative, was " + growth);
        }
        if (maxSize < size) {
            throw new IllegalArgumentException("wound mark maxSize must be at least its size, was " + maxSize);
        }
    }

    /** The radius of the disc for a body of the given radius carrying {@code wounds}, at least one. */
    public float radiusFor(float bodyRadius, int wounds) {
        float grown = size + growth * Math.max(0, wounds - 1);
        return bodyRadius * Math.min(maxSize, grown);
    }
}
