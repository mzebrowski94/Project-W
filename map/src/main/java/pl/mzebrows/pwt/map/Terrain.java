package pl.mzebrows.pwt.map;

/**
 * A kind of ground. Names a role in the world, never a colour and never a number to balance - what a
 * terrain looks like is decided in the client module, and what standing on it costs a unit is decided
 * in {@code sim}. This is the same split {@code Look} and {@code Palette} already run for entities.
 *
 * <p><strong>Passability is the one exception, and it is here because it is geometry rather than
 * gameplay.</strong> Map generation itself cannot work without it: checking that a level is
 * connected, and finding somewhere to put an object, are questions about shape that have to be
 * answerable inside this module. Anything else terrain might do to a unit - slow it, hurt it, hide
 * it - is a gameplay number and belongs in {@code assets/sim/*.properties}, read by a system.
 *
 * <p>{@link #PLAIN} is deliberately first: a grid that has not been written to yet is open ground
 * rather than solid rock, so a half-built map reads as empty instead of as a bug in the generator.
 */
public enum Terrain {

    /** Open ground. The default a map starts as. */
    PLAIN(true),

    /** Scoured, dusty ground. Passable; where wreckage tends to collect. */
    DUST(true),

    /** Open sand, yellow-bronze. Passable and costs nothing; what the proving ground is laid on (G0060). */
    SAND(true),

    /** Alien growth. Passable; where the things that feed the strains take root. */
    ORGANIC(true),

    /** Standing water and the mud round it. Passable; where the things that hop take root. */
    MIRE(true),

    /** Burnt ground. Passable; what is left where a place has already been fought over. */
    ASH(true),

    /**
     * Dense undergrowth. Passable, and the first ground that is worth anything to walk round.
     *
     * <p>Every other terrain in this list is a colour and a mood; this one is a decision, and the
     * only thing that makes it one is a number that is deliberately <em>not</em> here (see the class
     * note). A route is still a route through it - nothing is closed off, so the flow field and
     * every connectivity check treat a thicket as the open ground it is - and what it costs to cross
     * is asked of {@code sim} at the moment something is standing in it.
     */
    THICKET(true),

    /** Rock. The blocking terrain, and what anything outside the map reads as. */
    ROCK(false);

    private final boolean passable;

    Terrain(boolean passable) {
        this.passable = passable;
    }

    /** Whether a unit may stand here. Geometry, not gameplay - see the class note. */
    public boolean passable() {
        return passable;
    }
}
