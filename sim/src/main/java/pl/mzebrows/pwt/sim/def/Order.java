package pl.mzebrows.pwt.sim.def;

/**
 * One thing the commander can tell the troopers near him to do (G0017).
 *
 * <p>Every one of these is a <strong>verb and never a target</strong>. That is what keeps his own
 * position the thing that decides who hears him: an order that named a place would need a cursor with
 * authority over the field, and pillar 2 says he is a unit rather than a cursor - his mouse is his
 * aim, and G0002 fixed that as a direction rather than a point on the ground.
 *
 * <p>An order outranks the level's objectives and never an ongoing fight. A trooper that is shooting
 * keeps shooting and carries out what it was told when the shooting stops, which needs no queue: the
 * order sits on the trooper as a standing instruction, and what it is doing is chosen fresh every
 * tick from the fight first and the order second.
 */
public enum Order {
    /** Come with me. Troopers keep a loose formation and keep looking around. */
    FOLLOW_ME,
    /** Push on. A burst of pace towards the objective, for a short distance only. */
    MOVE_OUT,
    /** Hold here. Stationary, still watching. */
    STOP,
    /** As you were. Drops whatever is standing and goes back to the level's objectives. */
    CANCEL
}
