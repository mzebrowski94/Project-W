package pl.mzebrows.pwt.ui.layout;

/**
 * What the screen layout is solved from, besides the window's size. All of it is rendering: nothing
 * here can change what the game does.
 *
 * @param canvasHeight UI pixels from the top of the screen to the bottom; the largest whole scale at
 *     which this many fit decides how big a UI pixel is
 * @param uiScale a whole scale the player asked for, or 0 for the largest that fits; never more than
 *     that, and never a fraction
 * @param aspectMin the view is never narrower than this, width over height; the rest of a tall screen's
 *     height goes to the bars
 * @param aspectMax the view is never wider than this; the rest of a wide screen's width is black
 * @param topShare the share of any height the bars get beyond what they need that goes to the top one
 * @param metrics how tall each bar must be to hold its content
 */
public record LayoutSpec(
        int canvasHeight, int uiScale, float aspectMin, float aspectMax, float topShare, HudMetrics metrics) {

    public LayoutSpec {
        if (canvasHeight < 1) {
            throw new IllegalArgumentException("canvas height must be positive: " + canvasHeight);
        }
        if (uiScale < 0) {
            throw new IllegalArgumentException("UI scale is 0 (auto) or a whole scale: " + uiScale);
        }
        if (!(aspectMin > 0f) || aspectMin > aspectMax) {
            throw new IllegalArgumentException(
                    "view aspect range must be positive and min <= max: " + aspectMin + " .. " + aspectMax);
        }
        if (!(topShare >= 0f && topShare <= 1f)) {
            throw new IllegalArgumentException("top share is a fraction: " + topShare);
        }
    }
}
