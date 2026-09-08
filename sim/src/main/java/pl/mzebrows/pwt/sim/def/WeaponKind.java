package pl.mzebrows.pwt.sim.def;

/**
 * Every gun the commander can be carrying.
 *
 * <p>{@link #SIDEARM} is what he starts with and what he comes back with, so a gun is something you
 * find and then have to keep hold of rather than something you own. That is the whole of the
 * progression a weapon pickup adds, and it is deliberately the whole of it: pillar 3 says nothing is
 * bought, equipped or configured between levels.
 */
public enum WeaponKind {
    /** The gun he starts every life with. Two shots kill a grunt. */
    SIDEARM,
    /** Slow, heavy and fast-flying. One shot kills anything a sidearm needs five for. */
    RAILGUN
}
