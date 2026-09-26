package pl.mzebrows.pwt.ui.nav;

/**
 * Turns a held direction into presses: one at once, the next after {@code delaySeconds}, then one every
 * {@code intervalSeconds} for as long as it is held. One per direction per device.
 */
public final class NavRepeat {

    private final float delaySeconds;
    private final float intervalSeconds;
    private boolean held;
    private float untilNext;

    public NavRepeat(float delaySeconds, float intervalSeconds) {
        this.delaySeconds = delaySeconds;
        this.intervalSeconds = intervalSeconds;
    }

    /** Whether this frame counts as a press. */
    public boolean step(boolean holding, float frameSeconds) {
        if (!holding) {
            held = false;
            return false;
        }
        if (!held) {
            held = true;
            untilNext = delaySeconds;
            return true;
        }
        untilNext -= frameSeconds;
        if (untilNext <= 0f) {
            untilNext += Math.max(intervalSeconds, 1e-3f);
            return true;
        }
        return false;
    }

    /** Forgets the hold, so the next frame it is held counts as a fresh press. */
    public void reset() {
        held = false;
    }
}
