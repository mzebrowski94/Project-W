package pl.mzebrows.pwt.ui.nav;

/**
 * Which kind of device the player last used meaningfully, which decides how the selection looks and
 * which button names the hints use. The pointer takes over when the mouse moves far enough or clicks;
 * a key or a pad takes it back with any direction, confirm or back. A twitch of the mouse under the wake
 * distance changes nothing - {@link NavInput#pointerMoved} is only set past it.
 */
public final class InputModality {

    public enum Mode {
        POINTER,
        DIGITAL
    }

    private Mode mode = Mode.DIGITAL;
    private Device lastDigital = Device.KEYBOARD;
    private boolean playStation;

    public void update(NavInput input) {
        if (input.pointerMoved || input.pointerPressed || input.pointerSecondary) {
            mode = Mode.POINTER;
            return;
        }
        boolean digital =
                input.up || input.down || input.left || input.right || input.confirm || input.back || input.menu;
        if (digital && (input.device == Device.KEYBOARD || input.device == Device.PAD)) {
            mode = Mode.DIGITAL;
            lastDigital = input.device;
            if (input.device == Device.PAD) {
                playStation = input.playStation;
            }
        }
    }

    public Mode mode() {
        return mode;
    }

    /** Whether hints name pad buttons rather than keys. */
    public boolean padHints() {
        return mode == Mode.DIGITAL && lastDigital == Device.PAD;
    }

    public boolean playStation() {
        return playStation;
    }

    /** Whether the system pointer shows: in pointer mode only. */
    public boolean showsPointer() {
        return mode == Mode.POINTER;
    }
}
