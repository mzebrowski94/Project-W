package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw a mainframe (G0111): a big dark-grey cabinet - its body the palette's {@code graphic.color.mainframe} -
 * with a black screen on its face, a red X on the screen until it is downloaded and a green tick once it is. The X is
 * on the object, never over the view (G0098). Its download is the door's loading circle on it, in the colour its charge
 * names. Every size is a share of its radius.
 *
 * @param screen the screen's colour
 * @param locked the X's colour, while it is still to be downloaded
 * @param done the tick's colour, once it is
 * @param width how wide the cabinet is drawn
 * @param height how tall the cabinet is drawn
 * @param screenWidth how wide its screen is
 * @param screenHeight how tall its screen is
 * @param stroke how thick the X's and the tick's strokes are
 */
public record MainframeMark(
        Color screen,
        Color locked,
        Color done,
        float width,
        float height,
        float screenWidth,
        float screenHeight,
        float stroke) {

    public MainframeMark {
        if (width <= 0f || height <= 0f || screenWidth <= 0f || screenHeight <= 0f || stroke <= 0f) {
            throw new IllegalArgumentException("a mainframe's sizes must be positive");
        }
        if (screenWidth > width || screenHeight > height) {
            throw new IllegalArgumentException("a mainframe's screen fits on its face");
        }
    }

    /** How wide the cabinet is on a mainframe of this radius. */
    public float widthFor(float radius) {
        return radius * width;
    }

    /** How tall the cabinet is on a mainframe of this radius. */
    public float heightFor(float radius) {
        return radius * height;
    }

    /** How wide its screen is on a mainframe of this radius. */
    public float screenWidthFor(float radius) {
        return radius * screenWidth;
    }

    /** How tall its screen is on a mainframe of this radius. */
    public float screenHeightFor(float radius) {
        return radius * screenHeight;
    }

    /** How thick the X's or the tick's strokes are on a mainframe of this radius. */
    public float strokeFor(float radius) {
        return radius * stroke;
    }
}
