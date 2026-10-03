package pl.mzebrows.pwt.client;

/**
 * Which keys were pressed this frame.
 *
 * <p>One method wide so the classes that decide what a key <em>means</em> can be tested without a
 * window, a GL context or a libGDX application. In production this is {@code
 * Gdx.input::isKeyJustPressed}; in a test it is a set of keycodes.
 */
@FunctionalInterface
public interface KeyPresses {

    /** True if the key went down this frame. Keycodes are libGDX's {@code Input.Keys} values. */
    boolean justPressed(int keycode);

    /** The same, for a key as {@code controls.properties} binds it. */
    default boolean justPressed(KeyCode key) {
        return justPressed(key.code());
    }
}
