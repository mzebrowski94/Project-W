package pl.mzebrows.pwt.sim.def;

/**
 * Every strain a unit can be in. One constant per form, and the form an entity currently has is data
 * on its {@code Strain} component rather than which marker components it carries.
 *
 * <p>That distinction is what makes evolution possible at all. Swapping a marker would mean adding or
 * removing a component in the middle of a tick, which mutates the families being iterated and
 * silently skips entities; swapping this enum is a field write that nothing can miss.
 *
 * <p>Adding a strain is one constant here, one {@link UnitDef} in {@code SimConfig.defaults()} and
 * one block under {@code sim.unit.<name>} in {@code game.properties}. Nothing else - the loader and
 * the registry both iterate this enum, and both fail loudly if a definition is missing.
 */
public enum UnitKind {
    /** The basic enemy: fast, fragile, dies to one hand strike. */
    GRUNT,
    /** The heavy enemy: slower, tougher, survives a hand strike and is knocked back instead. */
    BRUTE,
    /**
     * The shooting enemy: it stops at a distance and spits, roughly, rather than closing. Fragile up
     * close, and a different problem from the two above - one you have to go and solve rather than
     * one that comes to you.
     */
    SPITTER
}
