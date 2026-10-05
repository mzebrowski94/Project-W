package pl.mzebrows.pwt.sim.def;

/**
 * How hard a mission is (G0063): one block of numbers on the swarm under {@code sim.mission.tier.<name>}, read
 * by {@code MissionConfig} alone. The tier never moves the map - one mission seed is the same mission on every
 * tier (G0065) - and a tier's letter in a mission code is the first letter of its name.
 */
public enum TierKind {
    /** Fewer creatures, softer, slower to breed, and a swarm that never adapts. */
    EASY,
    /** The biomes as their blocks are written, and a swarm that adapts. */
    MEDIUM,
    /** More creatures, tougher, quicker to breed, and a swarm that adapts sooner. */
    HARD
}
