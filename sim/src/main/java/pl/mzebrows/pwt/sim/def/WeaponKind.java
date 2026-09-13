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
    RAILGUN,
    /**
     * What a spitter fires. Slow enough in the air to be seen coming and walked out of, which is
     * what makes a firing line something to read rather than something to be hit by.
     *
     * <p>It is in the same table as the commander's guns and cannot be picked up, because nothing
     * grants it: what a pickup offers each side is in its {@code PickupDef}, and no pickup offers
     * this.
     */
    SPIT
}
