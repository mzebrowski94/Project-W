package pl.mzebrows.pwt.ui.layout;

/**
 * The letterbox: where the UI canvas sits on the screen, and where on it the top bar, the view and the
 * bottom bar go. Solved from the window's size and a {@link LayoutSpec} alone.
 *
 * <p>Two rules make it look right on every screen. The canvas is scaled by a <b>whole</b> number, so one
 * UI pixel is always a square of screen pixels and text is never resampled. And the bars are sized by
 * what they hold while the view takes the rest, with its shape kept inside an aspect range rather than
 * pinned: a 16:9 screen gets a wide view and two thin bars, a Steam Deck or a 4:3 screen a taller view
 * rather than bars full of nothing, and an ultrawide black pillars rather than a fight stretched past
 * where anybody's eyes go.
 *
 * <p>Rects are in UI pixels, origin at the canvas's bottom left; {@link #toScreen} turns one into screen
 * pixels. Bars span the canvas's width, but what is in them belongs inside the view's horizontal span
 * ({@link #contentLeft()} to {@link #contentRight()}), so text never drifts to the corners of a wide screen.
 *
 * @param scale screen pixels per UI pixel, a whole number
 * @param offsetX screen pixels left of the canvas - the remainder of a width that is not a whole
 *     number of UI pixels, split between the sides and drawn black
 * @param offsetY screen pixels under the canvas, the same for the height
 */
public record ScreenLayout(int scale, int offsetX, int offsetY, Rect canvas, Rect topBar, Rect view, Rect bottomBar) {

    /** What a window with no area gets - a minimised one. Nothing to draw into, nothing to divide by. */
    public static final ScreenLayout NONE = new ScreenLayout(1, 0, 0, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY);

    /** A double's worth of slack, so a view exactly at the narrowest shape is not cut a pixel short. */
    private static final double SLACK = 1e-4;

    /**
     * Solves the layout for a screen of this many back-buffer pixels.
     *
     * @param screenWidth the back buffer's width - physical pixels, not the operating system's logical ones
     * @param screenHeight the back buffer's height
     */
    public static ScreenLayout solve(int screenWidth, int screenHeight, LayoutSpec spec) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return NONE;
        }
        int largest = Math.max(1, screenHeight / spec.canvasHeight());
        int scale = spec.uiScale() == 0 ? largest : Math.min(spec.uiScale(), largest);
        int canvasWidth = screenWidth / scale;
        int canvasHeight = screenHeight / scale;
        int offsetX = (screenWidth - canvasWidth * scale) / 2;
        int offsetY = (screenHeight - canvasHeight * scale) / 2;

        int topMin = spec.metrics().topMin();
        int bottomMin = spec.metrics().bottomMin();
        int viewWidth = canvasWidth;
        int viewHeight = canvasHeight - topMin - bottomMin;
        if (viewHeight * 2 < canvasHeight) {
            // Too short for both bars and a fight worth seeing: the fight wins, whole.
            topMin = 0;
            bottomMin = 0;
            viewHeight = canvasHeight;
        }
        if (viewWidth > viewHeight * (double) spec.aspectMax()) {
            viewWidth = (int) Math.round(viewHeight * (double) spec.aspectMax());
        }
        if (viewWidth < viewHeight * (double) spec.aspectMin()) {
            viewHeight = (int) Math.floor(viewWidth / (double) spec.aspectMin() + SLACK);
        }
        int viewX = (canvasWidth - viewWidth) / 2;
        int spare = canvasHeight - viewHeight - topMin - bottomMin;
        int top = topMin + (int) Math.ceil(spare * (double) spec.topShare() - SLACK);
        int bottom = canvasHeight - viewHeight - top;

        return new ScreenLayout(
                scale,
                offsetX,
                offsetY,
                new Rect(0, 0, canvasWidth, canvasHeight),
                new Rect(0, bottom + viewHeight, canvasWidth, top),
                new Rect(viewX, bottom, viewWidth, viewHeight),
                new Rect(0, 0, canvasWidth, bottom));
    }

    /** The same rectangle in screen pixels, for a viewport or a scissor. */
    public Rect toScreen(Rect ui) {
        return new Rect(offsetX + ui.x() * scale, offsetY + ui.y() * scale, ui.width() * scale, ui.height() * scale);
    }

    /** Where content in a bar may start: the view's left edge, not the screen's. */
    public int contentLeft() {
        return view.x();
    }

    /** Where content in a bar must end: the view's right edge. */
    public int contentRight() {
        return view.right();
    }
}
