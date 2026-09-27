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
    SPITTER,
    /**
     * The fast one: smaller, thinner, and it notices you from much further off than anything else.
     *
     * <p>What makes it a different problem from a grunt is not its speed but its line - it weaves as
     * it closes and steps aside from shots it sees coming, so it is a target you have to lead rather
     * than one you have to out-run.
     */
    RUNNER,
    /**
     * The one that gets about in hops and ruins the ground it is shooting at.
     *
     * <p>A shooter like a spitter and a different problem from one. It holds a middle distance, and
     * what it throws is not a shot to be hit by but a spatter to be standing in - so the thing to
     * read is the ground rather than the creature. It gets about in bursts with a sit between them,
     * which is what makes it worth shooting at the moment it lands.
     *
     * <p>And it gives up easily: its lose range is barely past the range it notices you at, where
     * everything else keeps hunting you half a screen further. Backing off actually works on one,
     * which is the whole of what makes it a creature to leave alone rather than one to clear.
     */
    FROG,
    /**
     * The one in two segments, whose head turns a bullet and whose leap takes a man away (G0045).
     *
     * <p>A different problem from everything above, twice over. Shooting it in the face does
     * nothing, so a squad standing in a line in front of it is a squad doing nothing - somebody has to
     * get round it, or put a fist in its face. And what its leap lands on is not killed but carried
     * off, so the fight moves: a man is being taken somewhere and the clock on him is running.
     */
    ANT
}
