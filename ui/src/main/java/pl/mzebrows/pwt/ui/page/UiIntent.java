package pl.mzebrows.pwt.ui.page;

/**
 * What the player asked for - by confirming an entry, pressing back, or pressing the menu button in a
 * level. What it does depends on the page it is asked on: that is {@link UiFlow}'s transition table.
 *
 * @param page the page an {@link Kind#OPEN} opens; ignored by the rest
 * @param index the scenario a {@link Kind#START} or {@link Kind#HOST} means, the row a {@link Kind#CHOOSE}
 *     steps, the entry of the missions page a {@link Kind#MISSION} was asked on; ignored by the rest
 */
public record UiIntent(Kind kind, UiPage page, int index) {

    public enum Kind {
        OPEN,
        BACK,
        QUIT_APP,
        START,
        HOST,
        JOIN,
        PAUSE,
        RESUME,
        RESTART,
        LEAVE,
        /**
         * Step a choice row of the proving ground forward, or flip the controls page's PROMPTS switch - its
         * confirm; left and right step it too.
         */
        CHOOSE,
        /** Fight the proving ground's setup (G0060). */
        PROVE,
        /** Play what an entry of the missions page names - a daily, RANDOM, or the code line (G0065). */
        MISSION,
        /** The mission's code to the clipboard, from the result (G0066). */
        COPY_CODE
    }

    public static final UiIntent BACK = of(Kind.BACK);
    public static final UiIntent QUIT_APP = of(Kind.QUIT_APP);
    public static final UiIntent JOIN = of(Kind.JOIN);
    public static final UiIntent PAUSE = of(Kind.PAUSE);
    public static final UiIntent RESUME = of(Kind.RESUME);
    public static final UiIntent RESTART = of(Kind.RESTART);
    public static final UiIntent LEAVE = of(Kind.LEAVE);
    public static final UiIntent PROVE = of(Kind.PROVE);
    public static final UiIntent COPY_CODE = of(Kind.COPY_CODE);

    public static UiIntent open(UiPage page) {
        return new UiIntent(Kind.OPEN, page, -1);
    }

    public static UiIntent start(int scenario) {
        return new UiIntent(Kind.START, UiPage.PLAY, scenario);
    }

    /**
     * A choice row: on the proving ground its place in {@code ProvingRows.Row}, on its upgrades page the kind's,
     * on the controls page its entry - the PROMPTS switch.
     */
    public static UiIntent choose(int row) {
        return new UiIntent(Kind.CHOOSE, UiPage.PLAY, row);
    }

    /** An entry of the missions page: a tier's daily by its place, {@code MenuTree.MISSION_RANDOM} or {@code .MISSION_CODE}. */
    public static UiIntent mission(int entry) {
        return new UiIntent(Kind.MISSION, UiPage.PLAY, entry);
    }

    /** What HOST GAME's OPEN means: open the session on its briefing, before any mission is chosen (G0101). */
    public static final UiIntent OPEN_SESSION = new UiIntent(Kind.HOST, UiPage.PLAY, -1);

    public static UiIntent host(int scenario) {
        return new UiIntent(Kind.HOST, UiPage.PLAY, scenario);
    }

    private static UiIntent of(Kind kind) {
        return new UiIntent(kind, UiPage.PLAY, -1);
    }
}
