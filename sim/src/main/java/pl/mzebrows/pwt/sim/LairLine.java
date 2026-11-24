package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * One thing a finale puts down at an offset from its anchor (G0064), written {@code unit|nest|pickup <kind> dx dy} or
 * {@code hole dx dy}: a lair's contents, in the order written. Positions, never counts - a tier never reaches one.
 */
public sealed interface LairLine {

    /** Where it stands. */
    Offset at();

    /** The same line with the set piece flipped north-south. */
    LairLine flipped();

    /** A creature of one strain - a lair's boss. */
    record Unit(UnitKind strain, int dx, int dy) implements LairLine {
        @Override
        public Offset at() {
            return new Offset(dx, dy);
        }

        @Override
        public LairLine flipped() {
            return new Unit(strain, dx, -dy);
        }
    }

    /** A hole sending that strain out. */
    record Nest(UnitKind strain, int dx, int dy) implements LairLine {
        @Override
        public Offset at() {
            return new Offset(dx, dy);
        }

        @Override
        public LairLine flipped() {
            return new Nest(strain, dx, -dy);
        }
    }

    /** A hole whose strain the finale's pool deals (G0120): the keep's nest spots, fixed, their strains not. */
    record Hole(int dx, int dy) implements LairLine {
        @Override
        public Offset at() {
            return new Offset(dx, dy);
        }

        @Override
        public LairLine flipped() {
            return new Hole(dx, -dy);
        }
    }

    /** Something lying there for whoever reaches it first - a gun's cache included, which a biome never holds. */
    record Pickup(PickupKind kind, int dx, int dy) implements LairLine {
        @Override
        public Offset at() {
            return new Offset(dx, dy);
        }

        @Override
        public LairLine flipped() {
            return new Pickup(kind, dx, -dy);
        }
    }
}
