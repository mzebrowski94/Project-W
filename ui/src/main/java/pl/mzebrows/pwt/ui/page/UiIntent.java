package pl.mzebrows.pwt.ui.page;

/**
 * What the player asked for - by confirming an entry, pressing back, or pressing the menu button in a
 * level. What it does depends on the page it is asked on: that is {@link UiFlow}'s transition table.
 *
 * @param page the page an {@link Kind#OPEN} opens; ignored by the rest
 * @param index the scenario a {@link Kind#START} or {@link Kind#HOST} means; ignored by the rest
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
        LEAVE
    }

    public static final UiIntent BACK = of(Kind.BACK);
    public static final UiIntent QUIT_APP = of(Kind.QUIT_APP);
    public static final UiIntent JOIN = of(Kind.JOIN);
    public static final UiIntent PAUSE = of(Kind.PAUSE);
    public static final UiIntent RESUME = of(Kind.RESUME);
    public static final UiIntent RESTART = of(Kind.RESTART);
    public static final UiIntent LEAVE = of(Kind.LEAVE);

    public static UiIntent open(UiPage page) {
        return new UiIntent(Kind.OPEN, page, -1);
    }

    public static UiIntent start(int scenario) {
        return new UiIntent(Kind.START, UiPage.PLAY, scenario);
    }

    public static UiIntent host(int scenario) {
        return new UiIntent(Kind.HOST, UiPage.PLAY, scenario);
    }

    private static UiIntent of(Kind kind) {
        return new UiIntent(kind, UiPage.PLAY, -1);
    }
}
