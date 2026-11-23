package pl.mzebrows.pwt.sim.def;

/**
 * The kinds of **emplacement** (G0113): a standing shooter nobody crews, firing a fixed cycle of unaimed shots. Each has
 * a definition and a {@code sim.emplacement.<kind>} block (ADR 0008); a new one is a constant and a block, never a
 * class.
 */
public enum EmplacementKind {
    /** The fen's standing shooter: a cycle of bearings dealt when the level is built, one shot a step (G0113). */
    SPIRE,
    /**
     * One of a relic's four guards (G0114): a fixed volley along all four compass lines, never woken by men near it but by
     * the first attempt at its relic, and silent again once the relic is taken.
     */
    WARDEN
}
