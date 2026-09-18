package pl.mzebrows.pwt.client;

/**
 * The bindings that change how the players are looking at the field rather than what they are doing
 * in it. A game binding, not a harness one - it is in the input map in {@code docs/design/README.md}
 * and its key is in {@code controls.properties}.
 *
 * <p>Separate from {@link HarnessControls} because the harness keys are dev tools that will be
 * removed, and this one is not. Separate from {@code InputReader} because nothing here ever reaches
 * the simulation: the view is the client's alone, and a rendering setting that could change what the
 * game does would be a bug rather than a feature.
 *
 * <p><strong>One view, so one toggle.</strong> There is one camera framing everybody (pillar 4), and
 * a pad's zoom button reaches this same switch - so whoever presses it pulls the view back for all
 * four players. A per-player zoom on a shared view is not a setting, it is an argument.
 */
public final class ViewControls {

    private ViewControls() {}

    /** True on the frame the player asked to swap between the normal and pulled-back view. */
    public static boolean zoomToggled(KeyPresses keys, ControlsConfig.Keyboard bindings) {
        return keys.justPressed(bindings.zoom());
    }
}
