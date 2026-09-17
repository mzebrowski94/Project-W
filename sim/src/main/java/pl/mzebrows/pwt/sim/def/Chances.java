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
 * <p>Equality is by value, because it sits inside {@code SimConfig}, which is a record a test
 * compares against the defaults it fell back to.
 */
@SuppressWarnings("EnumOrdinal")
public final class Chances {

    private static final AiChance[] KINDS = AiChance.values();

    private final float[] byKind;

    private Chances(float[] byKind) {
        this.byKind = byKind;
    }

    /** Every behaviour at the number declared on its own constant. */
    public static Chances standard() {
        float[] values = new float[KINDS.length];
        for (AiChance kind : KINDS) {
            values[kind.ordinal()] = kind.standard();
        }
        return new Chances(values);
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
        return new Chances(copy);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Chances chances && Arrays.equals(byKind, chances.byKind);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(byKind);
    }

    @Override
    public String toString() {
        return "Chances" + Arrays.toString(byKind);
    }
}
