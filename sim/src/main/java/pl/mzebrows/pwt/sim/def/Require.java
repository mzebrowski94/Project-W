package pl.mzebrows.pwt.sim.def;

/**
 * Argument checks shared by every definition and config record.
 *
 * <p>A definition with a negative radius or a negative cooldown does not fail where it was written;
 * it fails ticks later inside a system, as a shape that never collides or a weapon that never
 * reloads. Rejecting it at construction turns a puzzling symptom into a message naming the field.
 */
public final class Require {

    private Require() {}

    /** Rejects zero and below, for a value that is meaningless at zero - a radius, a speed. */
    public static void positive(String name, float value) {
        if (value <= 0f) {
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        }
    }

    /** Rejects below zero, for a value that is meaningful at zero - a cooldown, a count. */
    public static void notNegative(String name, float value) {
        if (value < 0f) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }

    /** Rejects below one, for a count of things that makes no sense empty - passes, a leap sequence. */
    public static void atLeastOne(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1, was " + value);
        }
    }
}
