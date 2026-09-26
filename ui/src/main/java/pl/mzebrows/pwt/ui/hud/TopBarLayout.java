package pl.mzebrows.pwt.ui.hud;

/**
 * How the top bar's content is split inside the view's span: text on the left, the dev overlay's panel
 * on the right. The panel's share stays reserved when the overlay is hidden, so nothing reflows.
 *
 * @param padding UI pixels between the bar's edge and its content
 * @param overlayShare the share of the span the overlay's panel takes
 */
public record TopBarLayout(int padding, float overlayShare) {

    /** Width of the overlay's panel in a span this wide. */
    public int overlayWidth(int span) {
        return Math.round(span * overlayShare);
    }

    /** Width left for the mission, the goal and the terminal. */
    public int textWidth(int span) {
        return span - overlayWidth(span) - 2 * padding;
    }
}
