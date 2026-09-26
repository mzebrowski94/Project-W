package pl.mzebrows.pwt.ui.hud;

/** What the goal line says, before it becomes words. */
public enum Goal {
    /** The level names no objective: the line is empty. */
    NONE,
    CAPTURE_POINTS,
    HOLD_POINT,
    CLEAR_ENEMIES,
    /** Every objective the level named is met, right now. */
    COMPLETE
}
