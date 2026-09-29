package pl.mzebrows.pwt.client;

/**
 * How much of the dev overlay is on screen. A level starts with it {@link #OFF}; {@code F1} steps
 * through the modes in this order.
 *
 * <p>The panel keeps its place in the top bar reserved whichever mode is on, so the layout never
 * reflows when it is toggled.
 */
public enum OverlayMode {
    /** The most useful lines, in the top bar's right-hand panel. */
    PANEL,
    /** Every line, over the view's top-right corner. */
    EXPANDED,
    /** Nothing; the panel's space stays empty. */
    OFF;

    /** The mode {@code F1} moves to from this one. */
    public OverlayMode next() {
        return switch (this) {
            case PANEL -> EXPANDED;
            case EXPANDED -> OFF;
            case OFF -> PANEL;
        };
    }
}
