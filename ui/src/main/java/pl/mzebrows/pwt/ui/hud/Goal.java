package pl.mzebrows.pwt.ui.hud;

/** What the goal line says, before it becomes words. */
public enum Goal {
    /** The level names no objective: the line is empty. */
    NONE,
    CAPTURE_POINTS,
    HOLD_POINT,
    CLEAR_ENEMIES,
    /** Every objective the level named is met, right now. */
    COMPLETE,
    /** Every commander out and nobody left in reserve: the level is lost, and says so above all else (G0055). */
    FAILED
}
