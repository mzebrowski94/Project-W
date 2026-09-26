package pl.mzebrows.pwt.ui.nav;

/**
 * One frame of navigation, from every device on this machine: edges only, a held direction already
 * turned into repeats. Filled by the client's reader, read by the flow, the selection and the modality.
 * Reused from frame to frame; {@link #clear()} starts a new one.
 */
public final class NavInput {

    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean confirm;
    public boolean back;

    /** The menu button: {@code Esc} on the keyboard, {@code START} on a pad. */
    public boolean menu;

    /** The menu button came from a pad's {@code START}, not from {@code Esc} - which is also back. */
    public boolean menuFromPad;

    /** The pointer, in UI canvas pixels, y up. */
    public int pointerX;

    public int pointerY;

    /** Whether the pointer moved at least the wake distance since it last woke. */
    public boolean pointerMoved;

    /** The primary button went down this frame. */
    public boolean pointerPressed;

    /** The secondary button went down this frame. */
    public boolean pointerSecondary;

    /** The device behind this frame's directions, confirms and backs. */
    public Device device = Device.NONE;

    /** The pad that last did something shows PlayStation labels rather than Xbox ones. */
    public boolean playStation;

    public void clear() {
        up = false;
        down = false;
        left = false;
        right = false;
        confirm = false;
        back = false;
        menu = false;
        menuFromPad = false;
        pointerMoved = false;
        pointerPressed = false;
        pointerSecondary = false;
        device = Device.NONE;
    }

    /** Whether anything a page reacts to happened. */
    public boolean any() {
        return up || down || left || right || confirm || back || menu || pointerPressed || pointerSecondary;
    }
}
