package pl.mzebrows.pwt.sim.def;

/**
 * Something the commander can do to whatever he is standing next to, on one key.
 *
 * <p><strong>One key, and the game decides what it means.</strong> The player never picks from a
 * list: what is within reach is what is on offer, and if two things are, the nearer one wins. That
 * is pillar 1 - a menu in the middle of a firefight is a moment spent computing - and it is why this
 * is an enum published for the interface rather than a set of separate bindings.
 *
 * <p>{@link #NONE} is what almost every tick reports, and it is a real answer rather than a missing
 * one: it is what tells the interface to show nothing.
 */
public enum ActionKind {
    /** Nothing within reach is worth doing anything to. */
    NONE,
    /** Put a man who is down back on his feet. */
    STABILISE,
    /** Get on the gun he is standing next to. */
    MAN_TURRET,
    /** Get off the gun he is on. The only thing on offer while he is on one. */
    LEAVE_TURRET,
    /**
     * Open the crate he is standing next to (G0056). What he takes from it he chooses from the cards in
     * his top bar - the one place the key leads to a choice, and the second named exception to pillar 1.
     */
    OPEN_CRATE,
    /**
     * Open the shut door he is standing at (G0069): a hold, on stabilise's rules. Last on a tie - it goes to
     * whatever is already on the list.
     */
    OPEN_DOOR,
    /**
     * Download the mainframe he is standing at (G0111): a hold, on a door's rules - the work is the mainframe's, one
     * tick of it a tick however many hands, and lost the moment he lets go, steps away, dashes or takes a wound.
     */
    DOWNLOAD,
    /**
     * Extract the relic he is standing at (G0114): a download's hold under another name - the work is the relic's, lost
     * as a download's is.
     */
    EXTRACT;

    /**
     * Whether this works a site (G0111): a hold whose work is the site's, and whose prompt says what is being done
     * while it is - {@code DECRYPTING…} at a mainframe, {@code EXTRACTING…} at a relic. Every worked site's action is
     * one.
     */
    public boolean worksASite() {
        return this == DOWNLOAD || this == EXTRACT;
    }
}
