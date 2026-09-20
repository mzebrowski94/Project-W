package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What a shot leaves lying on the ground where it bursts.
 *
 * <p>A handful of overlapping circles rather than one, because the shape is the point: a single
 * circle reads as a second blast and is walked round, and a spatter reads as ground that has been
 * ruined and has to be gone round. Every circle is an ordinary entity with a body and a blow, so
 * standing in one is the contact rule the game already has rather than a new kind of damage.
 *
 * <p><strong>A slick takes a side; a blast does not.</strong> Each circle carries the faction of
 * whatever fired the shot, so venom is a problem for the player's men and not for the things that
 * spat it. That is a departure from the blast rule and is written down as an invariant rather than
 * left here, because it is the kind of difference that otherwise looks like a bug.
 *
 * <p>Zero blobs means a gun that leaves nothing, which is what every gun but one declares - the same
 * "zero means not for me" {@link RangedDef#off()} and {@link WeaveDef#off()} use.
 *
 * @param blobs how many circles one burst leaves. One is a puddle; a handful is a spatter
 * @param radius how wide each of them is, in world units. Also the circle the player sees, because
 *     the body <em>is</em> the drawing - there is one number rather than two kept in step
 * @param spread how far from the burst their centres may fall, in world units. Below {@code radius}
 *     they are guaranteed to overlap into one shape; well above it they read as separate puddles
 * @param seconds how long the ground stays ruined. Long enough to have to go round, short enough
 *     that a fight does not end with half the level fenced off
 * @param damage what touching one takes off. Far above any unit's health on purpose, exactly as a
 *     blast's is: it is a blow that puts a man down rather than one he can walk off
 * @param victimInvulnerableSeconds how long a man it has put down is protected from it afterwards.
 *     Without it, a man who went down inside a slick would be finished by the next circle on the
 *     same tick, and a blow that leaves a casualty would be an outright kill everywhere it matters
 */
@With
public record SlickDef(
        int blobs, float radius, float spread, float seconds, float damage, float victimInvulnerableSeconds) {

    public SlickDef {
        Require.notNegative("slick blobs", blobs);
        Require.notNegative("slick radius", radius);
        Require.notNegative("slick spread", spread);
        Require.notNegative("slick seconds", seconds);
        Require.notNegative("slick damage", damage);
        Require.notNegative("slick victimInvulnerableSeconds", victimInvulnerableSeconds);
        if (blobs > 0 && (radius <= 0f || seconds <= 0f)) {
            throw new IllegalArgumentException("a slick needs a radius and a life to be anything at all");
        }
    }

    /** A gun that leaves nothing behind. What every gun but one declares. */
    public static SlickDef off() {
        return new SlickDef(0, 0f, 0f, 0f, 0f, 0f);
    }

    /** True if a burst of this leaves anything on the ground. */
    public boolean leavesAnything() {
        return blobs > 0;
    }
}
