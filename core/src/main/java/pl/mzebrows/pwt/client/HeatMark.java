package pl.mzebrows.pwt.client;

/**
 * How to draw how hot a gun has become.
 *
 * <p>An arc over the thing holding it, filling from left to right, rather than the core every other
 * fraction in the game is drawn as. That is deliberate and it is the one place the client makes a
 * distinction the simulation does not: a core says "something is happening to this", and heat is the
 * one bar on the field that fills up because of what the player <em>did</em>. It also has to be
 * readable at the same time as a charge, which the core cannot be.
 *
 * <p>The colour comes from the palette, through a {@code Look} like everything else (G0003) - one for
 * a gun getting hot and one for a gun that has stopped itself, because the second is the one that
 * changes what to do next.
 *
 * @param scale how far out the arc sits, as a multiple of the body's radius
 * @param width how thick it is drawn, as a multiple of the body's radius
 * @param spreadDegrees how much of a circle a full bar covers, centred on straight up
 * @param segments how many straight pieces the arc is drawn in at full - fewer reads as a fan
 */
public record HeatMark(float scale, float width, float spreadDegrees, int segments) {

    public HeatMark {
        if (scale <= 0f) {
            throw new IllegalArgumentException("heat scale must be positive, was " + scale);
        }
        if (width <= 0f) {
            throw new IllegalArgumentException("heat width must be positive, was " + width);
        }
        if (spreadDegrees <= 0f || spreadDegrees > 360f) {
            throw new IllegalArgumentException("heat spreadDegrees must be in (0, 360], was " + spreadDegrees);
        }
        if (segments < 2) {
            throw new IllegalArgumentException("heat segments must be at least two, was " + segments);
        }
    }

    /** The radius the arc is drawn at for a body of the given radius. */
    public float radiusFor(float bodyRadius) {
        return bodyRadius * scale;
    }

    /** How thick to draw it for a body of the given radius. */
    public float widthFor(float bodyRadius) {
        return bodyRadius * width;
    }

    /** How many pieces to draw for a bar this full, at least one once there is anything to show. */
    public int segmentsFor(float heat) {
        int drawn = Math.round(segments * heat);
        return drawn < 1 ? 1 : drawn;
    }

    /** The angle, in degrees, of the given step of a full bar - left to right, over the top. */
    public float angleAt(int step) {
        return 90f + spreadDegrees * 0.5f - spreadDegrees * ((float) step / segments);
    }
}
