package pl.mzebrows.pwt.sim.system;

import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/**
 * The one way anything in the simulation asks the dice a question.
 *
 * <p>Every occasional behaviour goes through here, which is what makes {@link AiChance} an index
 * rather than a list somebody has to keep current: a system that wanted its own roll would have to
 * write its own conversion, and a search for callers of this class finds all of them.
 *
 * <p><strong>A chance per second, converted with the tick length.</strong> The alternative - a chance
 * per tick - is a number nobody can reason about and one that silently changes what the game does if
 * the tick rate is ever retuned. The conversion is the whole of what this class does that a caller
 * could not, and having it in one place is why every such number in {@code game.properties} means the
 * same thing.
 *
 * <p><strong>Dealt, not tossed.</strong> A coin tossed every tick has no memory: a unit that has just
 * done something is as likely to do it again the very next tick as one that has not done it for half a
 * minute, so a chance of once every two seconds comes twice within half a second about one time in
 * five, and fails to come for six seconds one time in twenty. Instead each unit carries what it is
 * still <em>due</em>: a budget drawn when the last occurrence happened, averaging one, which every tick
 * of opportunity spends down by the chance that tick is worth. The average is the stated chance
 * exactly, and no wait is ever longer than {@link Chances#longestGap()} times it. Longest gap zero is
 * the coin, kept for comparison and for a scenario that wants it.
 *
 * <p>The due lives on the unit's own component, so a snapshot carries it like any other field. Zero
 * - what {@code reset()} leaves - means nothing has been dealt yet, and the first tick of opportunity
 * deals it.
 *
 * <p><strong>Zero draws nothing.</strong> A behaviour switched off must not consume a number from the
 * stream, or turning one off in a scenario would reshuffle every roll made after it - reproducible,
 * so no test catches it, and miserable to iterate against (pillar 6, and the reason {@code RngStream}
 * exists at all).
 */
final class Odds {

    /** What a spend hands back when it came off with no overshoot to carry: the smallest spent value. */
    private static final float JUST_SPENT = -Float.MIN_VALUE;

    private Odds() {}

    /**
     * Spends a tick of opportunity on this behaviour, at the number the table gives it. Returns the
     * new due, to be stored back where it came from; {@link #spent} says whether it happened.
     */
    static float spend(Rng rng, float due, Chances chances, AiChance kind, float deltaTime) {
        return spend(rng, due, chances, chances.of(kind), deltaTime);
    }

    /**
     * The same, against a number the caller already has in hand - a strain's own override, copied
     * onto a component at spawn rather than looked up per entity. The table still says how it is
     * dealt.
     *
     * <p>What is left over when a due runs out is carried into the next one, so the stated chance is
     * kept to exactly rather than to the nearest tick - but never more than one tick's worth of it,
     * so a spell at a chance far above one per tick cannot be banked and paid out later.
     */
    static float spend(Rng rng, float due, Chances chances, float chancePerSecond, float deltaTime) {
        if (chancePerSecond <= 0f) {
            return due < 0f ? 0f : due;
        }
        float owed = chancePerSecond * deltaTime;
        float longestGap = chances.longestGap();
        if (longestGap <= 0f) {
            return rng.nextFloat() < owed ? JUST_SPENT : 0f;
        }
        float left = due > 0f ? due : deal(rng, longestGap) + Math.max(due, -owed);
        left -= owed;
        return left > 0f ? left : Math.min(left, JUST_SPENT);
    }

    /** Whether the spend that returned this due was the one that ran it out. */
    static boolean spent(float due) {
        return due < 0f;
    }

    /**
     * A fresh due: one on average, never more than the longest gap and never below two minus it. The
     * sum of two draws rather than one, so waits near the average are the likeliest and the extremes
     * rare - a tent rather than a flat line.
     */
    private static float deal(Rng rng, float longestGap) {
        return 1f + (longestGap - 1f) * (rng.nextFloat() + rng.nextFloat() - 1f);
    }
}
