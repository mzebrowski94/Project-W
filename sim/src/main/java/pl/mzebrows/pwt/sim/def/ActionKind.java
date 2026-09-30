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
    OPEN_CRATE
}
