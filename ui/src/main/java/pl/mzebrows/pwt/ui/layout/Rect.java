package pl.mzebrows.pwt.ui.layout;

/**
 * An axis-aligned rectangle in whole pixels, origin at the bottom left as libGDX has it.
 *
 * <p>Whole pixels because nothing on the UI canvas is ever drawn at a fractional position.
 */
public record Rect(int x, int y, int width, int height) {

    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public Rect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("a rectangle cannot have a negative size: " + width + " x " + height);
        }
    }

    public int right() {
        return x + width;
    }

    public int top() {
        return y + height;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }
}
