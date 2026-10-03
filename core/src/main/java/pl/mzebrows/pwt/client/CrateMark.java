package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.sim.ConfigKey;

/**
 * How to draw a crate (G0056): a small square in the crate's colour with a dot in the middle, so it reads
 * as a thing to open rather than a gun's cache to walk over - one look for every crate, whatever is inside.
 * The simulation says it is a crate and nothing about how that looks (G0003).
 *
 * @param dot the dot's radius, as a share of the crate's
 * @param color what the dot is drawn in
 */
public record CrateMark(float dot, @ConfigKey("dotColor") Color color) {

    public CrateMark {
        if (dot <= 0f || dot >= 1f) {
            throw new IllegalArgumentException("crate dot must be a share of the crate in (0, 1), was " + dot);
        }
    }

    /** The dot's radius for a crate of the given radius. */
    public float dotFor(float bodyRadius) {
        return bodyRadius * dot;
    }
}
