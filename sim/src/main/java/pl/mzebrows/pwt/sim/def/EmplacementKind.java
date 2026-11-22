package pl.mzebrows.pwt.sim.def;

/**
 * The kinds of **emplacement** (G0113): a standing shooter nobody crews, firing a fixed cycle of unaimed shots. Each has
 * a definition and a {@code sim.emplacement.<kind>} block (ADR 0008); a new one is a constant and a block, never a
 * class. The desert relic's warden is the next (ticket 09).
 */
public enum EmplacementKind {
    /** The fen's standing shooter: a cycle of bearings dealt when the level is built, one shot a step (G0113). */
    SPIRE
}
