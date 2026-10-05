package pl.mzebrows.pwt.ui.page;

/**
 * Which page is open, and what an intent does there: the transition table of
 * {@code docs/ui/menu_layout_agent.md} section 5. A stack of pages owned by one screen - the main menu's
 * pages over {@link UiPage#MAIN}, a level's modals over {@link UiPage#PLAY} - and nothing else. The
 * screen performs the {@link UiEffect}; this decides it.
 *
 * <p><strong>The pause is shared; the menu is not</strong> (plan 4.3). Asking for the menu opens it here
 * at once and asks for the pause; another machine's pause, or the harness's {@code P}, opens nothing and
 * only shows the paused banner. The menu closes on RESUME or BACK here, and also whenever the session
 * stops being paused, whoever resumed it - but only once it has been seen paused, because online the
 * pause lands a few ticks after the menu opens.
 *
 * <p><strong>The result opens itself</strong> (G0066): once the level's outcome has held its time, over the
 * level - after the pause menu, if that is open - and never by an entry. Back does nothing there. It closes
 * on RETRY, and whenever the outcome stops being due - a new world, the host's retry seen from a peer.
 *
 * <p>Allocates nothing after construction: the stack is an array as deep as the page tree.
 */
public final class UiFlow {

    private static final int DEPTH = 4;

    private final UiPage[] stack = new UiPage[DEPTH];
    private int size;
    private boolean paused;
    private boolean canRestart = true;
    private boolean sawPaused;

    /** A pause this machine asked for that has not been seen yet - online it is a few ticks away. */
    private boolean pauseInFlight;

    /** Leaving ends the session for everybody else: this machine hosts one. */
    private boolean hosting;

    /** The result for the outcome now due has been opened: closing it must not open it again. */
    private boolean resultOpened;

    /** A flow whose bottom page is {@code root}: {@link UiPage#MAIN} or {@link UiPage#PLAY}. */
    public UiFlow(UiPage root) {
        stack[0] = root;
        size = 1;
    }

    public UiPage top() {
        return stack[size - 1];
    }

    /** Whether a page or modal is open over the level: the player's input is not the game's. */
    public boolean menuOpen() {
        return top() != UiPage.PLAY && top() != UiPage.CONNECTING && stack[0] == UiPage.PLAY;
    }

    /** Paused with no menu open on this machine: the banner, no page, nothing selected. */
    public boolean showsPausedBanner() {
        return paused && top() == UiPage.PLAY;
    }

    /** Whether RESTART is offered: offline, or on the machine that hosts. */
    public boolean canRestart() {
        return canRestart;
    }

    /**
     * Follows the session. Call once a frame, before intents.
     *
     * @param isPaused whether the session is paused, by anybody
     * @param restartAllowed offline, or this machine hosts
     */
    public void observe(boolean isPaused, boolean restartAllowed) {
        canRestart = restartAllowed;
        if (stack[0] == UiPage.PLAY && size > 1 && stack[1] == UiPage.PAUSE) {
            if (isPaused) {
                sawPaused = true;
                pauseInFlight = false;
            } else if (sawPaused) {
                // Somebody resumed - here, on another machine, or with the harness's P.
                size = 1;
                sawPaused = false;
            }
        }
        paused = isPaused;
    }

    /**
     * Follows the level's outcome. Call once a frame, after {@link #observe}.
     *
     * @param due the level is won or lost and the hold has run out
     * @param hostingSession this machine hosts an online session, so leaving it ends it for everybody
     */
    public void observeResult(boolean due, boolean hostingSession) {
        hosting = hostingSession;
        if (stack[0] != UiPage.PLAY) {
            return;
        }
        if (!due) {
            resultOpened = false;
            if (size > 1 && stack[1] == UiPage.RESULT) {
                size = 1;
            }
            return;
        }
        if (!resultOpened && size == 1) {
            stack[size++] = UiPage.RESULT;
            resultOpened = true;
        }
    }

    /** Applies what the player asked for on the page that is open. Unknown pairs change nothing. */
    public UiEffect apply(UiIntent intent) {
        UiPage page = top();
        return switch (intent.kind()) {
            case OPEN -> open(page, intent.page());
            case BACK -> back(page);
            case QUIT_APP -> page == UiPage.EXIT_CONFIRM ? UiEffect.QUIT_APP : UiEffect.NONE;
            case START -> page == UiPage.CAMPAIGN ? new UiEffect(UiEffect.Kind.START, intent.index()) : UiEffect.NONE;
            case HOST -> page == UiPage.ONLINE_HOST ? new UiEffect(UiEffect.Kind.HOST, intent.index()) : UiEffect.NONE;
            case JOIN -> page == UiPage.ONLINE_JOIN ? UiEffect.JOIN : UiEffect.NONE;
            case PAUSE -> pause(page);
            case RESUME -> overThePause(page) ? resume() : UiEffect.NONE;
            case RESTART -> restart(page);
            case LEAVE -> leave(page);
            case CHOOSE ->
                page == UiPage.PROVING_GROUND || page == UiPage.PROVING_UPGRADES
                        ? new UiEffect(UiEffect.Kind.CHOOSE, intent.index())
                        : UiEffect.NONE;
            case PROVE -> page == UiPage.PROVING_GROUND ? UiEffect.PROVE : UiEffect.NONE;
        };
    }

    private UiEffect open(UiPage page, UiPage next) {
        if (next.parent() != page || next == UiPage.RESULT || size == DEPTH) {
            return UiEffect.NONE;
        }
        stack[size++] = next;
        return UiEffect.NONE;
    }

    private UiEffect back(UiPage page) {
        return switch (page) {
            case MAIN -> UiEffect.SELECT_EXIT;
            case PLAY -> UiEffect.NONE;
            case CONNECTING -> UiEffect.LEAVE;
            case PAUSE -> resume();
            case RESULT -> UiEffect.NONE;
            default -> {
                size--;
                yield UiEffect.NONE;
            }
        };
    }

    private UiEffect pause(UiPage page) {
        if (page != UiPage.PLAY) {
            return UiEffect.NONE;
        }
        stack[size++] = UiPage.PAUSE;
        sawPaused = paused;
        pauseInFlight = !paused;
        return paused ? UiEffect.NONE : UiEffect.REQUEST_PAUSE;
    }

    /** Resuming a pause still on its way asks too, or it would land after the menu had closed. */
    private UiEffect resume() {
        boolean ask = paused || pauseInFlight;
        size = 1;
        sawPaused = false;
        pauseInFlight = false;
        return ask ? UiEffect.REQUEST_RESUME : UiEffect.NONE;
    }

    /** The pause menu, or the leave confirm opened from it - not from the result. */
    private boolean overThePause(UiPage page) {
        return size > 1 && stack[1] == UiPage.PAUSE && (page == UiPage.PAUSE || page == UiPage.LEAVE_CONFIRM);
    }

    /** From the result, the host is asked first: his leaving ends the session for everybody (G0066). */
    private UiEffect leave(UiPage page) {
        if (page == UiPage.RESULT && hosting) {
            stack[size++] = UiPage.LEAVE_CONFIRM;
            return UiEffect.NONE;
        }
        return page == UiPage.LEAVE_CONFIRM || page == UiPage.CONNECTING || page == UiPage.RESULT
                ? UiEffect.LEAVE
                : UiEffect.NONE;
    }

    private UiEffect restart(UiPage page) {
        if ((page != UiPage.PAUSE && page != UiPage.RESULT) || !canRestart) {
            return UiEffect.NONE;
        }
        size = 1;
        sawPaused = false;
        pauseInFlight = false;
        return UiEffect.RESTART;
    }
}
