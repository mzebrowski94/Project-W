package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What something leaves lying on the ground where it ends.
 *
 * <p>A handful of overlapping circles rather than one, because the shape is the point: a single
 * circle reads as a second blast and is walked round, and a spatter reads as ground that has been
 * ruined and has to be gone round. Every circle is an ordinary entity with a body and a blow, so
 * standing in one is the contact rule the game already has rather than a new kind of damage.
 *
 * <p><strong>The side is declared here rather than fixed by the mechanic</strong> (G0035). Venom
 * takes the side of the frog that spat it, so a firing line is a place rather than a mess; fire is
 * on nobody's side, exactly as the blast that left it was, so it burns whoever walks into it. Both
 * are this record with different numbers, and it is the spawn that carries the side - there is no
 * field for it here, because the thing that dies already knows whose it was.
 *
 * <p>Zero blobs means a source that leaves nothing, which is what every gun but one declares - the
 * same "zero means not for me" {@link RangedDef#off()} and {@link WeaveDef#off()} use.
 *
 * @param blobs how many circles one burst leaves. One is a puddle; a handful is a spatter
 * @param radius how wide each of them is at its widest, in world units. Also the circle the player
 *     sees, because the body <em>is</em> the drawing - there is one number rather than two kept in
 *     step
 * @param spread how far from the burst their centres may fall, in world units. Below {@code radius}
 *     they are guaranteed to overlap into one shape; well above it they read as separate puddles
 * @param seconds the shortest a circle stays on the ground. Long enough to have to go round, short
 *     enough that a fight does not end with half the level fenced off
 * @param secondsMax the longest one stays. Drawn per circle between the two, so a spatter goes out
 *     raggedly rather than vanishing all at once; equal to {@code seconds} means every circle of a
 *     burst has exactly the same life, which is what venom declares
 * @param damage what touching one takes off. Far above any unit's health on purpose, exactly as a
 *     blast's is: it is a blow that puts a man down rather than one he can walk off
 * @param victimInvulnerableSeconds how long a man it has put down is protected from it afterwards.
 *     Without it, a man who went down inside a slick would be finished by the next circle on the
 *     same tick, and a blow that leaves a casualty would be an outright kill everywhere it matters
 * @param growFraction how much of its life a circle spends swelling to {@code radius}, as a
 *     fraction. Zero is full size from the tick it is laid, which is what ruined ground does
 * @param fadeFraction how much of its life it spends collapsing again, as a fraction. Zero is a
 *     circle that is the same size until the moment it is gone. <strong>The body is the drawing at
 *     every instant</strong>, so a circle that is shrinking is losing its reach as fast as it is
 *     losing its picture - which is what keeps G0034's "drawn and dangerous are the same interval"
 *     true of something that moves
 * @param look what the renderer draws it as. Part of what the thing <em>is</em>, exactly as a
 *     pickup's look is, so no system has to ask which kind of ruined ground this is
 */
@With
public record SlickDef(
        int blobs,
        float radius,
        float spread,
        float seconds,
        float secondsMax,
        float damage,
        float victimInvulnerableSeconds,
        float growFraction,
        float fadeFraction,
        Look look) {

    public SlickDef {
        Require.notNegative("slick blobs", blobs);
        Require.notNegative("slick radius", radius);
        Require.notNegative("slick spread", spread);
        Require.notNegative("slick seconds", seconds);
        Require.notNegative("slick secondsMax", secondsMax);
        Require.notNegative("slick damage", damage);
        Require.notNegative("slick victimInvulnerableSeconds", victimInvulnerableSeconds);
        Require.notNegative("slick growFraction", growFraction);
        Require.notNegative("slick fadeFraction", fadeFraction);
        if (blobs > 0 && (radius <= 0f || seconds <= 0f)) {
            throw new IllegalArgumentException("a slick needs a radius and a life to be anything at all");
        }
        if (secondsMax < seconds) {
            throw new IllegalArgumentException("a slick cannot burn for less time than its own minimum");
        }
        if (growFraction + fadeFraction > 1f) {
            throw new IllegalArgumentException("a slick cannot spend more than its whole life changing size");
        }
    }

    /** A source that leaves nothing behind. What every gun but one declares. */
    public static SlickDef off() {
        return new SlickDef(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, Look.NONE);
    }

    /** True if a burst of this leaves anything on the ground. */
    public boolean leavesAnything() {
        return blobs > 0;
    }
}
