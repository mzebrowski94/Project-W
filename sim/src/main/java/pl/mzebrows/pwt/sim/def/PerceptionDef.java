package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How far a strain can see, and how far the commander has to get before it gives up on him.
 *
 * <p><strong>The lose range only matters to a strain that can lose interest at all.</strong> One that
 * does gives up past it and idles where it was standing when it gave up, not where it was spawned -
 * see {@code WanderSystem}. Either way, the commander going down releases it: that is not a switch.
 *
 * <p><strong>The two ranges are deliberately different numbers.</strong> One range would mean a unit
 * standing exactly on the boundary notices him, chases half a step, loses him, stops, notices him
 * again - flickering between two behaviours every tick, at the one distance the player is most likely
 * to be standing. The gap between noticing and losing is what stops that, and it is why
 * {@code loseRange} may not be below {@code detectionRange}.
 *
 * <p><strong>What a strain can notice is a list of tests it declares</strong> (G0015), run cheapest
 * first: range, then arc, then a clear line. A strain that declares range alone - which every alien
 * does - notices exactly what it always did, and rock does not block it. A unit on the far side of a
 * wall hears you and comes round, which is the behaviour a level built out of corridors wants; the
 * alternative for <em>that</em> side, line of sight, turns every wall into a place the player is safe
 * to stand still behind.
 *
 * <p>The expensive test is reached only by a candidate that has already passed the cheap ones, so
 * cost scales with how many fights are happening rather than with how many units exist. That is the
 * answer to the third objection G0011 raised.
 *
 * @param losesInterest whether it can ever stop hunting a living commander. Off, the two ranges below
 *     decide only when it <em>starts</em> and nothing short of his death makes it stop. Either way it
 *     stands down when he goes down
 * @param alwaysAggressive whether this strain skips detection entirely and hunts from the first tick.
 *     What every strain did before there was any such thing as detection, kept as a switch so a
 *     scenario built around a standing fight is not quietly changed by a feature about noticing
 * @param detectionRange how close the commander must come before an unaware unit notices him, in
 *     world units
 * @param loseRange how far he must get before a hunting unit gives up, in world units. Never below
 *     {@code detectionRange}
 * @param secondaryRange how close something edible has to be before an idle unit goes for it, in
 *     world units. Deliberately the shortest of the three: the commander is always the primary, so
 *     this is what a unit notices while it has nothing better to do, not a competing pull
 * @param arcDegrees how wide the cone it can notice within is, centred on its facing. 360 means it
 *     notices all round, which is what every alien declares and what detection has always done
 * @param requiresClearLine whether rock between it and what it might notice hides that thing. Off
 *     for anything that hunts the commander (G0011); on for a trooper, which would otherwise open
 *     fire through a wall
 */
@With
public record PerceptionDef(
        boolean alwaysAggressive,
        boolean losesInterest,
        float detectionRange,
        float loseRange,
        float secondaryRange,
        float arcDegrees,
        boolean requiresClearLine) {

    /** Notices all round, which is what detection did before an arc was a thing anyone could declare. */
    public static final float ALL_ROUND = 360f;

    public PerceptionDef {
        Require.notNegative("perception detectionRange", detectionRange);
        Require.notNegative("perception loseRange", loseRange);
        Require.notNegative("perception secondaryRange", secondaryRange);
        if (loseRange < detectionRange) {
            throw new IllegalArgumentException("perception loseRange must not be below detectionRange");
        }
        if (arcDegrees <= 0f || arcDegrees > ALL_ROUND) {
            throw new IllegalArgumentException("perception arcDegrees must be in (0, 360], was " + arcDegrees);
        }
    }

    /**
     * The smallest dot product between this unit's facing and the direction to something for that
     * thing to count as in front of it.
     *
     * <p>A cosine rather than an angle so the per-tick test is one dot product and a comparison, with
     * no trigonometry in the tick at all. Computed with {@link StrictMath} rather than {@code Math}
     * because {@code ArchitectureTest} bans the latter's transcendentals - they are specified only to
     * within an ulp and may differ between JVMs, which pillar 6 does not allow. This is called once
     * per spawn, never in a loop.
     *
     * <p>{@link #ALL_ROUND} gives -1, which every direction beats, so an all-round strain pays for
     * the test in one comparison and never fails it.
     */
    public float arcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(arcDegrees * 0.5));
    }

    /** A strain that hunts from the first tick and never stops, whatever the ranges say. */
    public static PerceptionDef alwaysAggressive(float detectionRange, float loseRange) {
        return new PerceptionDef(true, false, detectionRange, loseRange, 0f, ALL_ROUND, false);
    }

    /** A strain that notices all round and through rock: what every alien declares. */
    public static PerceptionDef allRound(
            boolean alwaysAggressive,
            boolean losesInterest,
            float detectionRange,
            float loseRange,
            float secondaryRange) {
        return new PerceptionDef(
                alwaysAggressive, losesInterest, detectionRange, loseRange, secondaryRange, ALL_ROUND, false);
    }
}
