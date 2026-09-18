package pl.mzebrows.pwt.client;

/**
 * An analogue control on a gamepad, normalised by whatever is behind {@link GamepadSource}.
 *
 * <p>Sticks read {@code [-1, 1]} with y positive <strong>upwards</strong>, which is the world's
 * convention rather than the screen's - the adapter flips whatever its library hands it, so nothing
 * above the port has to remember which way round a given library is.
 *
 * <p>Triggers read {@code [0, 1]}. They are axes rather than buttons because that is what they are;
 * where the line between held and released falls is a number in {@code controls.properties} and not
 * a fact about the hardware.
 */
public enum GamepadAxis {
    NONE,
    LEFT_X,
    LEFT_Y,
    RIGHT_X,
    RIGHT_Y,
    LEFT_TRIGGER,
    RIGHT_TRIGGER
}
