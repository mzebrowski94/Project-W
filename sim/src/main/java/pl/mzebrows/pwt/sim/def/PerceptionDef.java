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
 * <p>Detection is range only: rock does not block it. A unit on the far side of a wall hears you and
 * comes round, which is the behaviour a level built out of corridors wants - the alternative, line of
 * sight, turns every wall into a place the player is safe to stand still behind.
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
 */
@With
public record PerceptionDef(
        boolean alwaysAggressive, boolean losesInterest, float detectionRange, float loseRange, float secondaryRange) {

    public PerceptionDef {
        Require.notNegative("perception detectionRange", detectionRange);
        Require.notNegative("perception loseRange", loseRange);
        Require.notNegative("perception secondaryRange", secondaryRange);
        if (loseRange < detectionRange) {
            throw new IllegalArgumentException("perception loseRange must not be below detectionRange");
        }
    }

    /** A strain that hunts from the first tick and never stops, whatever the ranges say. */
    public static PerceptionDef alwaysAggressive(float detectionRange, float loseRange) {
        return new PerceptionDef(true, false, detectionRange, loseRange, 0f);
    }
}
