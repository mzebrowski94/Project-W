package pl.mzebrows.pwt.client.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;
import pl.mzebrows.pwt.ui.nav.Device;
import pl.mzebrows.pwt.ui.nav.NavInput;
import pl.mzebrows.pwt.ui.nav.NavRepeat;

/**
 * Fills a {@link NavInput} from the keyboard, the mouse and every pad this copy of the game may use
 * (plan 6.2) - on the main menu nobody is seated yet, and any pad drives it.
 * Pads are read through the port (ADR 0011), by button position; what a press means on a page is
 * {@code UiFlow}'s. The menu button is read always; everything else only while a page or modal is
 * listening, because in a level those same keys, clicks and buttons are the game's.
 */
public final class NavInputReader {

    private static final int DIRECTIONS = 4;

    /** Pads read for navigation: every one this copy of the game may use, up to this many. */
    private static final int PADS = 8;

    private final ControlsConfig.Ui bindings;
    private final GamepadSource pads;

    /** Letters are being typed on this page: only the arrows move, and only Esc goes back. */
    private final boolean typing;

    private final NavRepeat[] keyRepeat = new NavRepeat[DIRECTIONS];
    private final NavRepeat[][] padRepeat = new NavRepeat[PADS][DIRECTIONS];
    private final boolean[] confirmWasDown = new boolean[PADS];
    private final boolean[] backWasDown = new boolean[PADS];

    /**
     * Pads whose buttons have been looked at once. The first look only records them: a button already
     * held when a screen opens - the SOUTH that confirmed the page before - is not a press here.
     */
    private final boolean[] primed = new boolean[PADS];

    private int anchorX = Integer.MIN_VALUE;
    private int anchorY;

    public NavInputReader(ControlsConfig.Ui bindings, GamepadSource pads) {
        this(bindings, pads, false);
    }

    /** A reader for a page letters are typed on - W, A, S, D and Backspace are text there. */
    public NavInputReader(ControlsConfig.Ui bindings, GamepadSource pads, boolean typing) {
        this.bindings = bindings;
        this.pads = pads;
        this.typing = typing;
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            keyRepeat[direction] = new NavRepeat(
                    bindings.nav().repeatDelaySeconds(), bindings.nav().repeatIntervalSeconds());
            for (int seat = 0; seat < PADS; seat++) {
                padRepeat[seat][direction] = new NavRepeat(
                        bindings.nav().repeatDelaySeconds(), bindings.nav().repeatIntervalSeconds());
            }
        }
    }

    /**
     * Fills {@code nav} with this frame's keyboard, pointer and pads.
     *
     * @param listening a page or modal is open and takes the player's input
     * @param padPause a pad's pause button went down this frame
     * @param layout where the UI canvas is, to turn the pointer into canvas pixels
     */
    public void read(
            NavInput nav,
            float frameSeconds,
            boolean listening,
            boolean padPause,
            KeyPresses keys,
            KeyHolds held,
            ScreenLayout layout) {
        nav.clear();
        if (keys.justPressed(bindings.key().back())) {
            nav.menu = true;
            nav.device = Device.KEYBOARD;
        }
        if (padPause) {
            nav.menu = true;
            nav.menuFromPad = true;
            nav.device = Device.PAD;
        }
        pointer(nav, layout, listening);
        if (!listening) {
            forgetHolds();
        } else {
            keyboard(nav, frameSeconds, keys, held);
        }
        int count = Math.min(PADS, pads.count());
        for (int index = 0; index < count; index++) {
            pad(nav, index, pads.get(index), frameSeconds, listening);
        }
    }

    private void keyboard(NavInput nav, float frameSeconds, KeyPresses keys, KeyHolds held) {
        boolean letters = !typing;
        boolean up = keyRepeat[0].step(held.held(Input.Keys.UP) || (letters && held.held(Input.Keys.W)), frameSeconds);
        boolean down =
                keyRepeat[1].step(held.held(Input.Keys.DOWN) || (letters && held.held(Input.Keys.S)), frameSeconds);
        boolean left =
                keyRepeat[2].step(held.held(Input.Keys.LEFT) || (letters && held.held(Input.Keys.A)), frameSeconds);
        boolean right =
                keyRepeat[3].step(held.held(Input.Keys.RIGHT) || (letters && held.held(Input.Keys.D)), frameSeconds);
        boolean confirm = keys.justPressed(bindings.key().confirm())
                || keys.justPressed(Input.Keys.NUMPAD_ENTER)
                || (letters && keys.justPressed(bindings.key().confirmAlias()));
        boolean back = keys.justPressed(bindings.key().back())
                || (letters && keys.justPressed(bindings.key().backAlias()));
        if (up || down || left || right || confirm || back) {
            nav.up |= up;
            nav.down |= down;
            nav.left |= left;
            nav.right |= right;
            nav.confirm |= confirm;
            nav.back |= back;
            nav.device = Device.KEYBOARD;
        }
    }

    /**
     * A pad's buttons are tracked in a level too, so a button held from the fight - SOUTH is act - is
     * not read as a confirm the frame the menu opens.
     */
    private void pad(NavInput nav, int seat, Gamepad pad, float frameSeconds, boolean listening) {
        boolean confirmDown = pad.pressed(bindings.pad().confirm());
        boolean backDown = pad.pressed(bindings.pad().back());
        boolean confirm = primed[seat] && confirmDown && !confirmWasDown[seat];
        boolean back = primed[seat] && backDown && !backWasDown[seat];
        confirmWasDown[seat] = confirmDown;
        backWasDown[seat] = backDown;
        primed[seat] = true;
        if (!listening) {
            return;
        }
        float threshold = bindings.nav().stickThreshold();
        float x = pad.axis(GamepadAxis.LEFT_X);
        float y = pad.axis(GamepadAxis.LEFT_Y);
        boolean up = padRepeat[seat][0].step(pad.pressed(GamepadButton.DPAD_UP) || y >= threshold, frameSeconds);
        boolean down = padRepeat[seat][1].step(pad.pressed(GamepadButton.DPAD_DOWN) || y <= -threshold, frameSeconds);
        boolean left = padRepeat[seat][2].step(pad.pressed(GamepadButton.DPAD_LEFT) || x <= -threshold, frameSeconds);
        boolean right = padRepeat[seat][3].step(pad.pressed(GamepadButton.DPAD_RIGHT) || x >= threshold, frameSeconds);
        if (up || down || left || right || confirm || back) {
            nav.up |= up;
            nav.down |= down;
            nav.left |= left;
            nav.right |= right;
            nav.confirm |= confirm;
            nav.back |= back;
            nav.device = Device.PAD;
            nav.playStation = PadLabels.isPlayStation(pad.name());
        }
    }

    private void pointer(NavInput nav, ScreenLayout layout, boolean listening) {
        int screenX = Gdx.input.getX();
        int screenY = Gdx.graphics.getHeight() - 1 - Gdx.input.getY();
        nav.pointerX = Math.floorDiv(screenX - layout.offsetX(), layout.scale());
        nav.pointerY = Math.floorDiv(screenY - layout.offsetY(), layout.scale());
        if (anchorX == Integer.MIN_VALUE) {
            anchorX = screenX;
            anchorY = screenY;
        }
        int dx = screenX - anchorX;
        int dy = screenY - anchorY;
        if (dx * dx + dy * dy
                >= bindings.nav().pointerWakePixels() * bindings.nav().pointerWakePixels()) {
            anchorX = screenX;
            anchorY = screenY;
            nav.pointerMoved = listening;
        }
        if (listening) {
            nav.pointerPressed = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
            nav.pointerSecondary = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);
        }
    }

    /** Out of a page, held directions are the game's: the next page starts from a fresh press. */
    private void forgetHolds() {
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            keyRepeat[direction].reset();
            for (int seat = 0; seat < PADS; seat++) {
                padRepeat[seat][direction].reset();
            }
        }
    }
}
