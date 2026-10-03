package pl.mzebrows.pwt.sim.def;

/**
 * A gun's <em>weapon type</em> - what an upgrade or an adaptation that cares about more than one
 * gun names, rather than a list of kinds. Several kinds share one.
 *
 * <p>{@link #NONE} is "not for me": what every gun no man carries declares - a creature's, a mount's, the
 * boss's.
 */
public enum WeaponType {
    /** No weapon type: no man carries it. */
    NONE,
    /** Bullets and pellets: the sidearm and the shotgun. */
    BALLISTIC,
    /** Fire and rockets, the guns that leave the ground burning or blown: the bazooka and the flamethrower. */
    HEAVY,
    /** Beams, bolts, slugs and discs: the laser, the pulse rifle, the railgun and the disc gun. */
    ENERGY
}
