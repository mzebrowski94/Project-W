package pl.mzebrows.pwt.ui.page;

/**
 * What the player asked for - by confirming an entry, pressing back, or pressing the menu button in a
 * level. What it does depends on the page it is asked on: that is {@link UiFlow}'s transition table.
 *
 * @param page the page an {@link Kind#OPEN} opens; ignored by the rest
 * @param index the scenario a {@link Kind#START} or {@link Kind#HOST} means, the row a {@link Kind#CHOOSE}
 *     steps; ignored by the rest
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
        /** Step a choice row of the proving ground forward - its confirm; left and right step it too. */
        CHOOSE,
        /** Fight the proving ground's setup (G0060). */
        PROVE
    }

    public static final UiIntent BACK = of(Kind.BACK);
    public static final UiIntent QUIT_APP = of(Kind.QUIT_APP);
    public static final UiIntent JOIN = of(Kind.JOIN);
    public static final UiIntent PAUSE = of(Kind.PAUSE);
    public static final UiIntent RESUME = of(Kind.RESUME);
    public static final UiIntent RESTART = of(Kind.RESTART);
    public static final UiIntent LEAVE = of(Kind.LEAVE);
    public static final UiIntent PROVE = of(Kind.PROVE);

    public static UiIntent open(UiPage page) {
        return new UiIntent(Kind.OPEN, page, -1);
    }

    public static UiIntent start(int scenario) {
        return new UiIntent(Kind.START, UiPage.PLAY, scenario);
    }

    /** A choice row: on the proving ground its place in {@code ProvingRows.Row}, on its upgrades page the kind's. */
    public static UiIntent choose(int row) {
        return new UiIntent(Kind.CHOOSE, UiPage.PLAY, row);
    }

    public static UiIntent host(int scenario) {
        return new UiIntent(Kind.HOST, UiPage.PLAY, scenario);
    }

    private static UiIntent of(Kind kind) {
        return new UiIntent(kind, UiPage.PLAY, -1);
    }
}
