package pl.mzebrows.pwt.client.input;

import com.badlogic.gdx.Input;

/**
 * A key on the keyboard, as {@code controls.properties} names it - {@code W}, {@code Up}, {@code Tab}, the
 * names libGDX gives them - and as {@link KeyHolds} and {@link KeyPresses} ask about it.
 *
 * @param code the key's {@code Input.Keys} value
 */
public record KeyCode(int code) {

    /** The key libGDX gives this name. */
    public static KeyCode named(String name) {
        int code = Input.Keys.valueOf(name);
        if (code == -1) {
            throw new IllegalArgumentException("'" + name + "' names no key");
        }
        return new KeyCode(code);
    }

    /** What libGDX calls it - the name the file gives it, or null for a code it has no name for. */
    public String name() {
        return Input.Keys.toString(code);
    }
}
