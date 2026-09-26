package pl.mzebrows.pwt.ui.page;

/**
 * The one motion a page makes: arriving. Going in slides the new page a few UI pixels in from the right
 * as it fades up; coming back slides it in from the left - the direction of travel, and nothing else
 * (plan 6.4). It lasts at most {@code seconds} times the motion scale, and <b>input never waits on it</b>:
 * any press finishes it at once and is applied as if it had never been there.
 *
 * <p>Frame time, whole UI pixels.
 */
public final class PageMotion {

    private final float seconds;
    private final int slide;
    private float elapsed;
    private int direction;

    /**
     * @param seconds how long an arrival takes at motion scale one
     * @param slide how far it slides, in UI pixels
     * @param motionScale 0 turns it off
     */
    public PageMotion(float seconds, int slide, float motionScale) {
        this.seconds = seconds * motionScale;
        this.slide = slide;
        this.elapsed = this.seconds;
    }

    /** A page arrived: +1 going in, -1 coming back out. */
    public void arrive(int travel) {
        direction = travel >= 0 ? 1 : -1;
        elapsed = 0f;
    }

    public void advance(float frameSeconds) {
        elapsed = Math.min(seconds, elapsed + frameSeconds);
    }

    /** The player did something: the page is where it was going, now. */
    public void finish() {
        elapsed = seconds;
    }

    public boolean moving() {
        return elapsed < seconds;
    }

    /** How far the page is from where it ends, in whole UI pixels. */
    public int offsetX() {
        return moving() ? Math.round(direction * slide * (1f - progress())) : 0;
    }

    /** How opaque the page is, 0 to 1. */
    public float alpha() {
        return moving() ? progress() : 1f;
    }

    private float progress() {
        return seconds <= 0f ? 1f : elapsed / seconds;
    }
}
