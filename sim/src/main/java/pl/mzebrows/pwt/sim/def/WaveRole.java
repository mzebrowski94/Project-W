package pl.mzebrows.pwt.sim.def;

/**
 * What part a finale's wave plays in its schedule (G0120): a <strong>role</strong>, which the finale's block fills with
 * strains of its own, one dealt to each wave from its stream. The schedule's rhythm is learnt; who comes is not.
 */
public enum WaveRole {
    /** Many and weak: the bulk a wave throws at the line. */
    FODDER,
    /** Quick, or leaping: what pulls the squad off its line. */
    FAST,
    /** What shoots from range. */
    RANGED,
    /** Few and hard: what the line has to stop. */
    HEAVY
}
