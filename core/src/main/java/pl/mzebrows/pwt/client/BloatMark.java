package pl.mzebrows.pwt.client;

/**
 * How to draw a thing being pumped up by a beam.
 *
 * <p>The odd one out on the charge channel, and deliberately. Every other charge is a brighter core
 * <em>inside</em> the body - see {@link ChargeMark} - because every other charge is something the
 * thing is doing. This one is something being done <em>to</em> it and is about to kill whatever is
 * standing nearby, so it grows out past the body instead: a ring the player reads as a size rather
 * than as a fill, from across a fight somebody else is having.
 *
 * <p>Half-transparent, because it has to be read <em>and</em> seen through. It sits on top of the
 * creature it is charging and the ground around it, and a solid disc at this size would hide both -
 * which matters, because the thing it is drawn on is usually still worth looking at.
 *
 * @param scale how far past the body it reaches at full charge, as a multiple of the body's radius.
 *     Above one on purpose: this is the one mark in the game that is bigger than what it is drawn on.
 *     <strong>It ramps from the body's own size rather than from nothing</strong> - scaled from zero
 *     it spends everything under two thirds charge <em>inside</em> the creature, which on anything
 *     small is a dot nobody can see and on everything else reads as not working. A ring that starts
 *     at the body and grows outwards is a halo from the first shot
 * @param alpha how much of it you can see through, in {@code [0, 1]}. One is a solid disc and hides
 *     the creature underneath
 */
public record BloatMark(float scale, float alpha) {

    public BloatMark {
        if (scale < 1f) {
            throw new IllegalArgumentException("a bloat ring may not be smaller than the body, was " + scale);
        }
        if (alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("bloat alpha must be in [0, 1], was " + alpha);
        }
    }

    /** The radius of the ring for a body of the given radius at the given charge. */
    public float radiusFor(float bodyRadius, float charge) {
        return bodyRadius * (1f + (scale - 1f) * charge);
    }
}
