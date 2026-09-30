package pl.mzebrows.pwt.ui.hud;

import org.jspecify.annotations.Nullable;

/**
 * Every kind of line the terminal can say (plan 8.3). A new one is a constant here, its words under
 * {@code ui.dispatch.<NAME>} in {@code ui.properties}, and - only if the fact is new - a reporter. A
 * constant and a definition, not a class per event (G0008).
 *
 * <p>The terminal is the battlefield's voice, not a log (plan 8.0): a kind earns a place here only if it
 * is news in the story of the fight, said the way a radio would say it. Anything the goal line or a
 * player panel already shows - waves, points, objectives, the mission done, a gun's heat - is not
 * repeated here, and neither is anything technical about the session.
 */
public enum DispatchKind {
    MISSION_STARTED(Priority.HIGH, Coalesce.NONE),
    COMMANDER_JOINED(Priority.NORMAL, Coalesce.NONE),
    WEAPON_ACQUIRED(Priority.LOW, Coalesce.NEWEST),
    REINFORCED(Priority.LOW, Coalesce.SUM),
    FEW_TROOPERS(Priority.NORMAL, Coalesce.NONE),
    COMMANDER_DOWN(Priority.HIGH, Coalesce.NEWEST),
    COMMANDER_LOST(Priority.HIGH, Coalesce.NEWEST),
    /** A player's slot earned an upgrade draft (G0049). */
    DRAFT_EARNED(Priority.NORMAL, Coalesce.NONE),
    /** Somebody took an upgrade: its kind as the first number, its new rank as the second. */
    UPGRADE_TAKEN(Priority.NORMAL, Coalesce.NONE),
    /** A draft went on to another player because its chooser left (D4). */
    DRAFT_PASSED(Priority.NORMAL, Coalesce.NONE),
    /** A draft was owed and the pool had nothing left to deal. */
    NOTHING_LEFT(Priority.LOW, Coalesce.NEWEST),
    /** The swarm took an adaptation (D11): its kind as the first number, its new rank as the second. */
    SWARM_ADAPTS(Priority.NORMAL, Coalesce.NONE);

    /**
     * How much a line matters. A high one is an alarm: it is shown at once, never dropped for being
     * stale, and never pushed out of a full queue by anything lower.
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH
    }

    /** What a repeat of the same kind, for the same player, inside the coalescing window does. */
    public enum Coalesce {
        /** Nothing: every one is its own line. */
        NONE,
        /** Replaces the arguments of the one already filed - "point taken 2/3" rather than two lines. */
        NEWEST,
        /** Adds its first argument to the first argument of the one already filed - "+2" and "+1" make "+3". */
        SUM
    }

    private final Priority priority;
    private final Coalesce coalesce;
    private final String key;

    DispatchKind(Priority priority, Coalesce coalesce) {
        this.priority = priority;
        this.coalesce = coalesce;
        this.key = "ui.dispatch." + name();
    }

    public Priority priority() {
        return priority;
    }

    public Coalesce coalesce() {
        return coalesce;
    }

    /** Its words' key in {@code ui.properties}. */
    public String key() {
        return key;
    }

    boolean high() {
        return priority == Priority.HIGH;
    }

    /**
     * The kind this one rewrites in place for the same player, wherever that line is - waiting or on
     * show, however long ago: a commander down who dies is one line that changes from DOWN to LOST, not
     * a second line scrolling the first away. Null for most.
     */
    @Nullable
    DispatchKind supersedes() {
        return this == COMMANDER_LOST ? COMMANDER_DOWN : null;
    }
}
