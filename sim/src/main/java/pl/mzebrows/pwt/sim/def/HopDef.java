package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a creature gets about: in bursts with a pause between them, rather than at a walk.
 *
 * <p><strong>A gait paces a course; it never chooses one.</strong> Everything that decides where a
 * unit is going has already run by the time this is applied, and all it does is spend that same
 * course in bursts - so a hopping creature goes exactly where a walking one would, and arrives in
 * the same time. {@link #burst()} is what makes that true: the pace during a hop is the strain's
 * speed scaled up by exactly as much as the rest takes away, so {@code speed} on the strain still
 * means what it means for everything else on the field.
 *
 * <p><strong>It is a clock, and deliberately so</strong>, which is the one thing here that departs
 * from how a weave works. A weave is a function of ground covered because a creature slowed down
 * must not weave faster and one stopped by a wall must not weave on the spot (G0012). A gait is the
 * opposite: legs have a rhythm, and a creature pressed against a wall is still trying to hop. The
 * phase is offset from where the unit was spawned, so two of them put down apart are out of step
 * without a number being drawn for it - and the same level hops the same way on every run.
 *
 * @param enabled whether this strain hops at all; one configured off simply walks, which is what
 *     every strain but the frog declares
 * @param hopSeconds how long one hop lasts, in seconds. The whole of the movement happens here
 * @param restSeconds how long it sits between hops, in seconds. This is what a player reads: a
 *     creature that is briefly not going anywhere is a creature you can shoot or walk away from
 */
@With
public record HopDef(boolean enabled, float hopSeconds, float restSeconds) {

    public HopDef {
        Require.notNegative("hop hopSeconds", hopSeconds);
        Require.notNegative("hop restSeconds", restSeconds);
        if (enabled && hopSeconds <= 0f) {
            throw new IllegalArgumentException("a strain that hops needs a hop to last some time");
        }
    }

    /** Something that walks. What every strain but one declares. */
    public static HopDef off() {
        return new HopDef(false, 0f, 0f);
    }

    /** One hop and the rest after it, in seconds. */
    public float cycleSeconds() {
        return hopSeconds + restSeconds;
    }

    /**
     * What a hop multiplies the pace by, so that the whole cycle covers what a walk would.
     *
     * <p>One for anything that does not hop, which is what leaves a walking strain untouched.
     */
    public float burst() {
        if (!enabled || hopSeconds <= 0f) {
            return 1f;
        }
        return cycleSeconds() / hopSeconds;
    }
}
