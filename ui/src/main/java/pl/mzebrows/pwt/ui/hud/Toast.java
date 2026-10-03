package pl.mzebrows.pwt.ui.hud;

/**
 * An upgrade earned, passed on or taken, or an adaptation, over the view where the paused banner goes ({@code UI:hud/view/toast}):
 * the same words the terminal says, typed out with a cursor and held a while once typed, then gone. One at
 * a time; the rest wait in a short queue, the oldest dropped when it is full. Only the kinds that
 * {@link DispatchKind#toasts()} are kept.
 *
 * <p>Runs on frame time, so it keeps typing while the game is paused. Nothing here allocates after
 * construction, except the words of a toast as it is shown.
 */
public final class Toast implements DispatchSink {

    /**
     * How the toast paces itself ({@code graphic.hud.toast.*}).
     *
     * @param charsPerSecond how fast it types; 0 shows it whole at once
     * @param holdSeconds how long it stays once typed
     * @param queue toasts kept waiting
     * @param blinkSeconds how long the cursor stays on, and off - the terminal's
     */
    public record Settings(float charsPerSecond, float holdSeconds, int queue, float blinkSeconds) {

        public Settings {
            if (queue < 1) {
                throw new IllegalArgumentException("a toast needs a queue: " + queue);
            }
            if (charsPerSecond < 0f || holdSeconds < 0f || blinkSeconds <= 0f) {
                throw new IllegalArgumentException("toast timings out of range");
            }
        }
    }

    private final Settings settings;
    private final DispatchWords words;

    /** Waiting toasts, oldest first, in {@code [0, pending)}; spare pooled ones after them. */
    private final Dispatch[] waiting;

    private int pending;
    private final Dispatch shown = new Dispatch();
    private boolean showing;
    private String said = "";
    private float typing;
    private float held;
    private int revealed;
    private int revision;

    public Toast(Settings settings, DispatchWords words) {
        this.settings = settings;
        this.words = words;
        this.waiting = new Dispatch[settings.queue()];
        for (int i = 0; i < waiting.length; i++) {
            waiting[i] = new Dispatch();
        }
    }

    @Override
    public void offer(DispatchKind kind, int slot, int a, int b) {
        if (!kind.toasts()) {
            return;
        }
        if (pending == waiting.length) {
            remove(0);
        }
        waiting[pending++].set(kind, slot, a, b, 0f);
    }

    /** Moves the clock on by a frame: types, lets a held toast go, and shows the next one. */
    public void advance(float frameSeconds) {
        if (showing) {
            if (revealed < said.length()) {
                typing += frameSeconds;
                reveal();
            } else {
                held += frameSeconds;
                if (held >= settings.holdSeconds()) {
                    showing = false;
                    revision++;
                }
            }
        }
        if (!showing && pending > 0) {
            show();
        }
    }

    @Override
    public void reset() {
        pending = 0;
        showing = false;
        revision++;
    }

    /** Whether a toast is on show. */
    public boolean showing() {
        return showing;
    }

    /** Its words, whole; typed out to {@link #revealed()}. */
    public String line() {
        return said;
    }

    /** How many characters of it are typed so far. */
    public int revealed() {
        return revealed;
    }

    /** What it is about: an upgrade earned, passed on or taken, or the swarm adapting. */
    public DispatchKind kind() {
        return shown.kind();
    }

    /** The player who earned, took over or took it, or -1 when it names nobody. */
    public int slot() {
        return shown.slot();
    }

    /** Whether the cursor after it is lit this frame. Lit throughout while it types. */
    public boolean cursor() {
        if (!showing) {
            return false;
        }
        float lit = revealed < said.length() ? typing : typing + held;
        return ((int) (lit / settings.blinkSeconds()) & 1) == 0;
    }

    /** Changes whenever a toast comes or goes, so a painter fits its text again only then. */
    public int revision() {
        return revision;
    }

    private void show() {
        shown.copyFrom(waiting[0]);
        remove(0);
        said = words.words(shown);
        showing = true;
        typing = 0f;
        held = 0f;
        revision++;
        reveal();
    }

    private void reveal() {
        float rate = settings.charsPerSecond();
        revealed = rate <= 0f ? said.length() : Math.min(said.length(), (int) (typing * rate));
    }

    private void remove(int index) {
        Dispatch gone = waiting[index];
        System.arraycopy(waiting, index + 1, waiting, index, pending - index - 1);
        waiting[--pending] = gone;
    }
}
