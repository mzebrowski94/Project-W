package pl.mzebrows.pwt.sim.def;

import org.jspecify.annotations.Nullable;

/**
 * One thing a {@code Lair} puts down when it wakes: a creature, a hole that sends more of them out, or
 * something lying on the ground to be picked up.
 *
 * <p>Put down exactly where it says rather than jittered as a wave is, because what a lair holds is
 * laid out round its boss by hand, the way a scenario places everything else it places by hand.
 *
 * @param form which of the three it is
 * @param strain which strain stands here, or which strain the hole sends out; null for a pickup
 * @param pickup what lies here; null for a creature or a hole
 * @param x where, in world units
 * @param y where, in world units
 */
public record Arrival(
        Form form, @Nullable UnitKind strain, @Nullable PickupKind pickup, float x, float y) {

    /** What kind of thing arrives. */
    public enum Form {
        /** A creature of {@code strain}. */
        UNIT,
        /** A hole sending out {@code strain}. */
        NEST,
        /** A {@code pickup} lying on the ground. */
        PICKUP
    }

    public Arrival {
        if ((form == Form.PICKUP) != (pickup != null) || (form == Form.PICKUP) == (strain != null)) {
            throw new IllegalArgumentException("an arrival names a strain or a pickup, whichever its form needs");
        }
    }

    /** A creature of this strain. */
    public static Arrival unit(UnitKind strain, float x, float y) {
        return new Arrival(Form.UNIT, strain, null, x, y);
    }

    /** A hole sending out this strain. */
    public static Arrival nest(UnitKind strain, float x, float y) {
        return new Arrival(Form.NEST, strain, null, x, y);
    }

    /** Something lying here for the first thing that touches it. */
    public static Arrival pickup(PickupKind pickup, float x, float y) {
        return new Arrival(Form.PICKUP, null, pickup, x, y);
    }
}
