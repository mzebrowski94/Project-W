package pl.mzebrows.pwt.client;

/**
 * Which keys are down this frame.
 *
 * <p>The held counterpart of {@link KeyPresses}, and here for the same reason: the classes that
 * decide what a key <em>means</em> are testable without a window, a GL context or a libGDX
 * application. In production this is {@code Gdx.input::isKeyPressed}; in a test it is a set of
 * keycodes.
 */
@FunctionalInterface
public interface KeyHolds {

    /** True while the key is down. Keycodes are libGDX's {@code Input.Keys} values. */
    boolean held(int keycode);
}
