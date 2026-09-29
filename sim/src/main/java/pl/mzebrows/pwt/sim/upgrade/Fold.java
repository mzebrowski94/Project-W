package pl.mzebrows.pwt.sim.upgrade;

import pl.mzebrows.pwt.sim.def.Whole;

/**
 * How a number is worked out from its base and what is held against it (ADR 0017). Always from the
 * base, on every read, and nothing is written back, so nothing can drift and nothing needs undoing.
 * Percentages are whole numbers, so a sum is exact whatever order it was added in.
 *
 * <p><strong>Nothing held hands back the base exactly</strong>, not a float that went through a
 * multiply and a divide: a world with no upgrades is byte for byte the world before they existed.
 */
final class Fold {

    private static final int WHOLE = 100;

    private Fold() {}

    /** A base scaled by a sum of percentages, the sum held inside the stat's range first. */
    static float scale(float base, int percent, int minPercent, int maxPercent) {
        if (percent == 0) {
            return base;
        }
        return base * (WHOLE + clamp(percent, minPercent, maxPercent)) / WHOLE;
    }

    /**
     * The same for hit points or damage, which are whole numbers (G0054): rounded by the one rule they
     * have ({@link Whole}) - to the nearest, halves up, and never a blow that did something down to none.
     */
    static int scaleWhole(int base, int percent, int minPercent, int maxPercent) {
        if (percent == 0) {
            return base;
        }
        return Whole.percentOf(base, WHOLE + clamp(percent, minPercent, maxPercent));
    }

    /**
     * The same for a duration already in whole ticks: rounded to the nearest tick, and never shortened
     * below one - a gun that fired every tick still does, and none fires twice in one.
     */
    static int scaleTicks(int baseTicks, int percent, int minPercent, int maxPercent) {
        if (percent == 0) {
            return baseTicks;
        }
        int scaled = Math.round(baseTicks * (float) (WHOLE + clamp(percent, minPercent, maxPercent)) / WHOLE);
        return Math.max(Math.min(baseTicks, 1), scaled);
    }

    /** A base plus a sum of whole amounts, held at the stat's floor. */
    static int count(int base, int amount, int min) {
        if (amount == 0) {
            return base;
        }
        return Math.max(min, base + amount);
    }

    /**
     * A chance per second plus a sum of chances per second, never below zero. A base of zero is a
     * behaviour switched off until something turns it up.
     */
    static float chance(float base, float perSecond) {
        if (perSecond == 0f) {
            return base;
        }
        return Math.max(0f, base + perSecond);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
