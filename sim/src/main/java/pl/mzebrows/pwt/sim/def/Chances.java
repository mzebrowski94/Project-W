package pl.mzebrows.pwt.sim.def;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * One number for every {@link AiChance}: how often each occasional behaviour happens.
 *
 * <p>An array indexed by the enum's ordinal, for the reasons {@link Defs} gives at length - one array
 * read, no hashing, and no iteration order that could differ between runs. It holds floats rather
 * than boxed values because a boxed lookup in a per-entity loop is banned here, and because a table
 * of primitives is what a system caching one number at construction actually wants.
 *
 * <p>It is built full rather than sparse: every constant has a value, always, so no caller anywhere
 * has a missing case to consider. Adding a constant to {@link AiChance} therefore cannot produce a
 * hole, and the number it starts at is the one declared on the constant itself.
 *
 * <p><strong>It also says how every chance is dealt</strong>: the {@link #longestGap() longest gap}.
 * That is one number for the whole table rather than one per behaviour, because it is not an opinion
 * about any one behaviour - it is how far the game lets luck run before it steps in.
 *
 * <p>Equality is by value, because it sits inside {@code SimConfig}, which is a record a test
 * compares against the defaults it fell back to.
 */
@SuppressWarnings("EnumOrdinal")
public final class Chances {

    private static final AiChance[] KINDS = AiChance.values();

    /** Twice the average: an occurrence can come at once, and never later than that. */
    private static final float STANDARD_LONGEST_GAP = 2f;

    /** The tightest deal there is - every gap exactly the average, a metronome. */
    private static final float METRONOME = 1f;

    private final float[] byKind;

    private final float longestGap;

    private Chances(float[] byKind, float longestGap) {
        this.byKind = byKind;
        this.longestGap = longestGap;
    }

    /** Every behaviour at the number declared on its own constant. */
    public static Chances standard() {
        float[] values = new float[KINDS.length];
        for (AiChance kind : KINDS) {
            values[kind.ordinal()] = kind.standard();
        }
        return new Chances(values, STANDARD_LONGEST_GAP);
    }

    /** How often this behaviour happens, as a chance per second of opportunity. Never negative. */
    public float of(AiChance kind) {
        return byKind[kind.ordinal()];
    }

    /** This table with one behaviour retuned, leaving the rest alone. Allocates; setup only. */
    public Chances with(AiChance kind, float chance) {
        Require.notNegative("ai chance " + kind, chance);
        float[] copy = byKind.clone();
        copy[kind.ordinal()] = chance;
        return new Chances(copy, longestGap);
    }

    /**
     * The longest a unit ever waits for a behaviour it has the opportunity for, as a multiple of the
     * average wait - or zero for no limit at all, which is a coin tossed every tick.
     *
     * <p>Between 1 and 2 otherwise. At 2 an occurrence can come at once and never later than twice
     * the average; at 1 every wait is exactly the average. Whatever it is, the average itself - the
     * chance per second each behaviour states - is kept to exactly ({@code Odds.spend}).
     */
    public float longestGap() {
        return longestGap;
    }

    /** This table dealt with a different longest gap. Setup only. */
    public Chances withLongestGap(float newLongestGap) {
        if (newLongestGap != 0f) {
            Require.atLeast("longest gap", newLongestGap, METRONOME, "no wait can be shorter than the average");
            if (newLongestGap > STANDARD_LONGEST_GAP) {
                throw new IllegalArgumentException("longest gap must be at most " + STANDARD_LONGEST_GAP
                        + " (a wait cannot be dealt below zero), or 0 for a coin every tick, was "
                        + newLongestGap);
            }
        }
        return new Chances(byKind, newLongestGap);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Chances chances
                && Arrays.equals(byKind, chances.byKind)
                && Float.compare(longestGap, chances.longestGap) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(byKind) + Float.hashCode(longestGap);
    }

    @Override
    public String toString() {
        return "Chances" + Arrays.toString(byKind) + " longestGap=" + longestGap;
    }
}
