package pl.mzebrows.pwt.ui.hud;

/** What a player's commander is doing, as far as his panel cares - one word or nothing. */
public enum Posture {
    /** No commander, and none on the way: an empty seat. */
    ABSENT,
    /** On his feet. The panel's status says nothing. */
    STANDING,
    /** Shot down and bleeding out; somebody can still reach him. */
    CASUALTY,
    /** In his throes: he is going, and nothing will stop it. */
    DYING,
    /** On a turret. */
    ABOARD,
    /** Dead, and coming back after a few seconds. */
    RESPAWNING;

    /** Whether he has a commander in the world at all - one that could be reached, seen or shot. */
    public boolean present() {
        return this != ABSENT && this != RESPAWNING;
    }
}
