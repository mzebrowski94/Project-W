package pl.mzebrows.pwt.client.input;

import com.badlogic.gdx.Input;
import java.util.Locale;

/**
 * A mouse button, as {@code controls.properties} names it: {@code LEFT}, {@code RIGHT} or {@code MIDDLE}.
 *
 * @param code the button's {@code Input.Buttons} value
 */
public record MouseButton(int code) {

    /** The button of this name. */
    public static MouseButton named(String name) {
        return new MouseButton(
                switch (name.toUpperCase(Locale.ROOT)) {
                    case "LEFT" -> Input.Buttons.LEFT;
                    case "RIGHT" -> Input.Buttons.RIGHT;
                    case "MIDDLE" -> Input.Buttons.MIDDLE;
                    default -> throw new IllegalArgumentException("'" + name + "' names no mouse button");
                });
    }

    /** The name the file gives it - {@code LEFT}, {@code RIGHT} or {@code MIDDLE}. */
    public String name() {
        return switch (code) {
            case Input.Buttons.LEFT -> "LEFT";
            case Input.Buttons.RIGHT -> "RIGHT";
            default -> "MIDDLE";
        };
    }
}
