package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * The one way anything in the simulation asks the dice a question.
 *
 * <p>Every occasional behaviour goes through here, so {@link AiChance} is a complete index. <strong>A
 * chance per second</strong>, converted with the tick length.
 *
 * <p><strong>Dealt, not tossed.</strong> Each unit carries what it is still <em>due</em>: a budget
 * averaging one, drawn after each occurrence and spent by every tick of opportunity. The average is
 * the stated chance exactly, and no wait exceeds {@link Chances#longestGap()} times it; zero there is
 * a coin. The due lives on the unit's component; zero means not yet dealt.
 *
 * <p><strong>Zero draws nothing</strong>, so switching a behaviour off reshuffles nothing (pillar 6).
 *
 * <p>The number spent is always the one {@link Modifiers#chance} gives for the unit rolling: this is
 * where the {@code CHANCE} stat is read, so every {@code AiChance} is a number an upgrade can move.
 */
final class Odds {

    /** What a spend hands back when it came off with no overshoot to carry: the smallest spent value. */
    private static final float JUST_SPENT = -Float.MIN_VALUE;

    private Odds() {}

    /**
     * Spends a tick of opportunity on this behaviour, at the number the table gives it. Returns the
     * new due, to be stored back where it came from; {@link #spent} says whether it happened.
     */
    static float spend(
            Modifiers modifiers, Entity unit, AiChance kind, Rng rng, float due, Chances chances, float deltaTime) {
        return spend(modifiers, unit, kind, rng, due, chances, chances.of(kind), deltaTime);
    }

    /**
     * The same, against a number the caller already has in hand - a strain's own override, copied
     * onto a component at spawn rather than looked up per entity. The table still says how it is
     * dealt.
     *
     * <p>Overshoot carries into the next due, capped at one tick's worth.
     */
    static float spend(
            Modifiers modifiers,
            Entity unit,
            AiChance kind,
            Rng rng,
            float due,
            Chances chances,
            float chancePerSecond,
            float deltaTime) {
        float chance = modifiers.chance(unit, kind, chancePerSecond);
        if (chance <= 0f) {
            return due < 0f ? 0f : due;
        }
        float owed = chance * deltaTime;
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
