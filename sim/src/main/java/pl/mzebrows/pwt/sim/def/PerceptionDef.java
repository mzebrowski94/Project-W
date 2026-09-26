package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How far a strain can see, and how far the commander has to get before it gives up on him.
 *
 * <p><strong>The two ranges are hysteresis</strong>: {@code loseRange} may not be below
 * {@code detectionRange}, or a unit on the boundary flickers every tick. The commander going down
 * always releases it.
 *
 * <p><strong>What a strain can notice is a list of tests it declares</strong> (G0015), run cheapest
 * first: range, then arc, then a clear line. Aliens declare range alone, so rock does not hide you
 * from them (G0011).
 *
 * @param losesInterest whether it can ever stop hunting a living commander
 * @param alwaysAggressive whether this strain skips detection entirely and hunts from the first tick
 * @param detectionRange how close the commander must come before an unaware unit notices him, in
 *     world units
 * @param loseRange how far he must get before a hunting unit gives up, in world units. Never below
 *     {@code detectionRange}
 * @param secondaryRange how close something edible has to be before an idle unit goes for it, in
 *     world units
 * @param arcDegrees how wide the cone it can notice within is, centred on its {@code Facing}; 360 is
 *     all round
 * @param requiresClearLine whether rock between it and what it might notice hides that thing (G0011)
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

    /** Notices all round. */
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
     * <p>{@link StrictMath}, once per spawn: {@code Math}'s transcendentals may differ between JVMs
     * (pillar 6). {@link #ALL_ROUND} gives -1, which every direction beats.
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
