package pl.mzebrows.pwt.sim.def;

/**
 * The kinds of **worked site** (G0111): a site taken by one man's work held at it, rather than by standing on it as a
 * control point is. Each has a definition and a {@code sim.site.<kind>} block (ADR 0008); a new one is a constant
 * and a block, never a class.
 */
public enum WorkedSiteKind {
    /** A big computer inside a house, downloaded at it (G0111). */
    MAINFRAME,
    /**
     * The desert's site (G0114): a glowing square over a stone plinth, out in the open sand, extracted under four wardens'
     * fire, rousing far wider than a download - and hatching snakes once it is taken.
     */
    RELIC
}
