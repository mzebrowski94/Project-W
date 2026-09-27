package pl.mzebrows.pwt.ui.hud;

/**
 * The top bar's running account of the level (plan 8.4): a bounded queue of {@link Dispatch}es and the
 * last few of them on show, the newest typed out with a cursor and the older ones above it.
 *
 * <ul>
 *   <li>A repeat of the same kind for the same player inside the coalescing window merges into the line
 *       already filed or shown, rather than filling the queue; a kind that supersedes another rewrites
 *       that player's line of it in place ({@link DispatchKind#supersedes()}).
 *   <li>A full queue evicts its oldest line of the lowest priority present - never for something less
 *       important than that; a high one goes only for a newer high one.
 *   <li>A high line is an alarm: shown at once, typed at once (at the default rate), and never dropped
 *       for waiting too long. Normal and low lines that waited longer than {@code maxAgeSeconds} are
 *       stale news and go unshown.
 *   <li>Otherwise the newest line stays newest at least {@code minHoldSeconds} after it is typed.
 *   <li>Nothing fades by age: the last lines stay until newer ones push them off (pillar 5).
 * </ul>
 *
 * <p>Runs on frame time, so it keeps typing while the game is paused; a paused world files nothing.
 * Nothing here allocates after construction, except the words of a line as it is shown.
 */
public final class Terminal implements DispatchSink {

    /**
     * How the terminal paces itself ({@code graphic.hud.terminal.*}).
     *
     * @param lines lines on show
     * @param charsPerSecond how fast a normal or low line types
     * @param highCharsPerSecond how fast a high one does; 0 shows it whole at once
     * @param minHoldSeconds least time a typed line stays newest before a waiting one pushes it up
     * @param maxAgeSeconds how long a normal or low line may wait before it is dropped unshown
     * @param queue lines kept waiting
     * @param coalesceSeconds how long after a line is filed a repeat still merges into it
     * @param blinkSeconds how long the cursor stays on, and off
     * @param cursorLingerSeconds how long the cursor keeps blinking after the line is typed
     * @param fewTroopers below this many troopers standing the squad is reported as few - the one
     *     threshold the reporters have, kept with the terminal's other settings
     */
    public record Settings(
            int lines,
            float charsPerSecond,
            float highCharsPerSecond,
            float minHoldSeconds,
            float maxAgeSeconds,
            int queue,
            float coalesceSeconds,
            float blinkSeconds,
            float cursorLingerSeconds,
            int fewTroopers) {

        public Settings {
            if (lines < 1 || queue < 1) {
                throw new IllegalArgumentException("a terminal needs a line and a queue: " + lines + ", " + queue);
            }
            if (charsPerSecond <= 0f || highCharsPerSecond < 0f || blinkSeconds <= 0f) {
                throw new IllegalArgumentException("typing and blinking need positive rates");
            }
            if (minHoldSeconds < 0f || maxAgeSeconds < 0f || coalesceSeconds < 0f || cursorLingerSeconds < 0f) {
                throw new IllegalArgumentException("terminal durations cannot be negative");
            }
        }
    }

    private final Settings settings;
    private final DispatchWords words;

    /** Waiting lines, oldest first, in {@code [0, pending)}; spare pooled ones after them. */
    private final Dispatch[] waiting;

    private int pending;

    /** Lines on show, newest first, and their words. */
    private final Dispatch[] shown;

    private final String[] said;
    private final boolean[] reworded;
    private int showing;

    private float now;
    private float typing;
    private float held;
    private float blink;
    private int revealed;
    private boolean typed;
    private int revision;

    public Terminal(Settings settings, DispatchWords words) {
        this.settings = settings;
        this.words = words;
        this.waiting = new Dispatch[settings.queue()];
        for (int i = 0; i < waiting.length; i++) {
            waiting[i] = new Dispatch();
        }
        this.shown = new Dispatch[settings.lines()];
        this.said = new String[settings.lines()];
        this.reworded = new boolean[settings.lines()];
        for (int i = 0; i < shown.length; i++) {
            shown[i] = new Dispatch();
            said[i] = "";
        }
    }

    @Override
    public void offer(DispatchKind kind, int slot, int a, int b) {
        DispatchKind replaced = kind.supersedes();
        if (replaced != null && supersede(replaced, kind, slot)) {
            return;
        }
        for (int i = 0; i < showing; i++) {
            if (shown[i].repeats(kind, slot, now, settings.coalesceSeconds())) {
                shown[i].merge(a, b);
                reworded[i] = true;
                return;
            }
        }
        for (int i = 0; i < pending; i++) {
            if (waiting[i].repeats(kind, slot, now, settings.coalesceSeconds())) {
                waiting[i].merge(a, b);
                return;
            }
        }
        if (pending == waiting.length && !evictFor(kind)) {
            return;
        }
        waiting[pending++].set(kind, slot, a, b, now);
    }

    /** Moves the clock on by a frame: drops stale lines, types, and shows the next line when it is due. */
    public void advance(float frameSeconds) {
        now += frameSeconds;
        blink += frameSeconds;
        for (int i = pending - 1; i >= 0; i--) {
            Dispatch waited = waiting[i];
            if (!waited.kind().high() && now - waited.bornAt() > settings.maxAgeSeconds()) {
                remove(i);
            }
        }
        if (showing > 0) {
            if (typed) {
                held += frameSeconds;
            } else {
                typing += frameSeconds;
                reveal();
            }
        }
        for (int next = next(); next >= 0 && due(waiting[next]); next = next()) {
            show(next);
        }
        for (int i = 0; i < showing; i++) {
            if (reworded[i]) {
                reworded[i] = false;
                said[i] = words.words(shown[i]);
                revision++;
                if (i == 0 && typed) {
                    revealed = said[0].length();
                }
            }
        }
    }

    /** Clears everything: a new world - a restart, a heal, the first world online. */
    @Override
    public void reset() {
        pending = 0;
        showing = 0;
        typed = false;
        revealed = 0;
        revision++;
    }

    /** How many lines are on show, up to the setting's. */
    public int showing() {
        return showing;
    }

    /** The words of a line on show: 0 is the newest, typed out to {@link #revealed()}; the rest are whole. */
    public String line(int age) {
        return said[age];
    }

    /** How many characters of the newest line are typed so far. */
    public int revealed() {
        return revealed;
    }

    /** Whether the cursor after the newest line is lit this frame. Still once the line has been typed a while. */
    public boolean cursor() {
        if (showing == 0 || (typed && held >= settings.cursorLingerSeconds())) {
            return false;
        }
        return ((int) (blink / settings.blinkSeconds()) & 1) == 0;
    }

    /** Changes whenever what a line says changes, so a painter fits its text again only then. */
    public int revision() {
        return revision;
    }

    /** Lines waiting to be shown. */
    public int waiting() {
        return pending;
    }

    /** Turns the line of the replaced kind about this player into the new kind, if there is one. */
    private boolean supersede(DispatchKind replaced, DispatchKind kind, int slot) {
        for (int i = 0; i < showing; i++) {
            if (shown[i].kind() == replaced && shown[i].slot() == slot) {
                shown[i].become(kind);
                reworded[i] = true;
                return true;
            }
        }
        for (int i = 0; i < pending; i++) {
            if (waiting[i].kind() == replaced && waiting[i].slot() == slot) {
                waiting[i].become(kind);
                return true;
            }
        }
        return false;
    }

    private boolean evictFor(DispatchKind kind) {
        int victim = -1;
        for (int i = 0; i < pending; i++) {
            if (victim < 0
                    || waiting[i]
                                    .kind()
                                    .priority()
                                    .compareTo(waiting[victim].kind().priority())
                            < 0) {
                victim = i;
            }
        }
        if (waiting[victim].kind().priority().compareTo(kind.priority()) > 0) {
            return false;
        }
        remove(victim);
        return true;
    }

    /** The waiting line to show next: the most important, and of those the oldest. -1 if none waits. */
    private int next() {
        int best = -1;
        for (int i = 0; i < pending; i++) {
            if (best < 0
                    || waiting[i]
                                    .kind()
                                    .priority()
                                    .compareTo(waiting[best].kind().priority())
                            > 0) {
                best = i;
            }
        }
        return best;
    }

    private boolean due(Dispatch next) {
        return showing == 0 || next.kind().high() || (typed && held >= settings.minHoldSeconds());
    }

    private void show(int index) {
        Dispatch[] lines = shown;
        Dispatch oldest = lines[lines.length - 1];
        for (int i = lines.length - 1; i > 0; i--) {
            lines[i] = lines[i - 1];
            said[i] = said[i - 1];
            reworded[i] = reworded[i - 1];
        }
        lines[0] = oldest;
        oldest.copyFrom(waiting[index]);
        reworded[0] = false;
        said[0] = words.words(oldest);
        showing = Math.min(showing + 1, lines.length);
        remove(index);
        typing = 0f;
        held = 0f;
        blink = 0f;
        typed = false;
        revealed = 0;
        revision++;
        reveal();
    }

    private void reveal() {
        int length = said[0].length();
        float rate = shown[0].kind().high() ? settings.highCharsPerSecond() : settings.charsPerSecond();
        revealed = rate <= 0f ? length : Math.min(length, (int) (typing * rate));
        if (revealed >= length) {
            typed = true;
        }
    }

    private void remove(int index) {
        Dispatch gone = waiting[index];
        System.arraycopy(waiting, index + 1, waiting, index, pending - index - 1);
        waiting[--pending] = gone;
    }
}
