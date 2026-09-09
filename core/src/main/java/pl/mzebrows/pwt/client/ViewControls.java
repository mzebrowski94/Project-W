package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Input;

/**
 * The bindings that change how the player is looking at the field rather than what they are doing in
 * it. A game binding, not a harness one - it is in the input map in {@code docs/design/README.md}.
 *
 * <p>Separate from {@link HarnessControls} because the harness keys are dev tools that will be
 * removed, and this one is not. Separate from {@code InputReader} because nothing here ever reaches
 * the simulation: the view is the client's alone, and a rendering setting that could change what the
 * game does would be a bug rather than a feature.
 */
public final class ViewControls {

    private ViewControls() {}

    /** True on the frame the player asked to swap between the normal and pulled-back view. */
    public static boolean zoomToggled(KeyPresses keys) {
        return keys.justPressed(Input.Keys.TAB);
    }
}
