package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What something leaves lying on the ground where it ends.
 *
 * <p>A spatter of overlapping circles, each an ordinary entity with a body and a blow. The side comes
 * from whatever died, not from here (G0035): venom is the frog's, fire nobody's. Zero blobs is off.
 *
 * <p><strong>A circle that only slows is not a blow</strong> (G0073): no damage, no harm and no shove, and what is
 * left is a slow on whoever stands in it - glue. It breaks no shield and turns nobody round; it is ground.
 *
 * @param blobs how many circles one burst leaves. One is a puddle; a handful is a spatter
 * @param radius how wide each of them is at its widest, in world units - the drawn circle too
 * @param spread how far from the burst their centres may fall, in world units
 * @param seconds the shortest a circle stays on the ground
 * @param secondsMax the longest; drawn per circle between the two
 * @param damage what touching one takes off anything with health. A man it wounds (G0053)
 * @param victimInvulnerableSeconds how long an alien it has hurt is protected from it afterwards; a man's
 *     protection is his own grace
 * @param growFraction how much of its life a circle spends swelling to {@code radius}
 * @param fadeFraction how much of its life it spends collapsing again. <strong>The body is the
 *     drawing at every instant</strong>, so drawn and dangerous stay the same interval (G0034)
 * @param look what the renderer draws it as
 * @param knockbackSpeed how hard a man it wounds is thrown off it, in world units per second
 * @param harm what touching one does to a man (G0053): a wound for venom and fire, none for glue
 * @param slowFactor how much of his speed whoever stands in one keeps, in {@code (0, 1]}; one is no slow
 * @param slowSeconds how long that lasts after he steps out, refreshed every tick he is in it rather than added to
 */
@With
public record SlickDef(
        int blobs,
        float radius,
        float spread,
        float seconds,
        float secondsMax,
        int damage,
        float victimInvulnerableSeconds,
        float growFraction,
        float fadeFraction,
        Look look,
        float knockbackSpeed,
        Harm harm,
        float slowFactor,
        float slowSeconds) {

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
        Require.notNegative("slick knockbackSpeed", knockbackSpeed);
        Require.notNegative("slick slowSeconds", slowSeconds);
        if (slowFactor <= 0f || slowFactor > 1f) {
            throw new IllegalArgumentException("slick slowFactor must be in (0, 1], was " + slowFactor);
        }
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
        return new SlickDef(0, 0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, Look.NONE, 0f, Harm.NONE, 1f, 0f);
    }

    /** True if a burst of this leaves anything on the ground. */
    public boolean leavesAnything() {
        return blobs > 0;
    }
}
