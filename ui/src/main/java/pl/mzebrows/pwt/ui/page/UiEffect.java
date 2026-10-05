package pl.mzebrows.pwt.ui.page;

/**
 * What the screen that owns a page must do after an intent - the part of a transition that is not a
 * page change: swapping screens, asking the session to pause, quitting. {@link UiFlow} decides it; the
 * screen carries it out and decides nothing.
 *
 * @param index the scenario a {@link Kind#START} or {@link Kind#HOST} means, the row a {@link Kind#CHOOSE}
 *     steps, the missions page's entry a {@link Kind#MISSION} or {@link Kind#HOST_MISSION} plays, else -1
 */
public record UiEffect(Kind kind, int index) {

    public enum Kind {
        NONE,
        /** Back on the main page: the selection moves to EXIT; nothing quits. */
        SELECT_EXIT,
        QUIT_APP,
        START,
        HOST,
        JOIN,
        /** Ask the session to pause - a shared pause, online everybody's. */
        REQUEST_PAUSE,
        /** Ask the session to resume. */
        REQUEST_RESUME,
        RESTART,
        /** Leave the level, or stop connecting, for the main menu. */
        LEAVE,
        /** Step one choice row of the proving ground forward. */
        CHOOSE,
        /** Fight the proving ground's setup (G0060). */
        PROVE,
        /** Play the mission an entry of the missions page names, offline. */
        MISSION,
        /** Host it: the missions page was opened from HOST GAME. */
        HOST_MISSION,
        /** The mission's code to the clipboard. */
        COPY_CODE
    }

    public static final UiEffect NONE = of(Kind.NONE);
    public static final UiEffect SELECT_EXIT = of(Kind.SELECT_EXIT);
    public static final UiEffect QUIT_APP = of(Kind.QUIT_APP);
    public static final UiEffect JOIN = of(Kind.JOIN);
    public static final UiEffect REQUEST_PAUSE = of(Kind.REQUEST_PAUSE);
    public static final UiEffect REQUEST_RESUME = of(Kind.REQUEST_RESUME);
    public static final UiEffect RESTART = of(Kind.RESTART);
    public static final UiEffect LEAVE = of(Kind.LEAVE);
    public static final UiEffect PROVE = of(Kind.PROVE);
    public static final UiEffect COPY_CODE = of(Kind.COPY_CODE);

    private static UiEffect of(Kind kind) {
        return new UiEffect(kind, -1);
    }
}
