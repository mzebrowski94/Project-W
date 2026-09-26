package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.Viewport;
import pl.mzebrows.pwt.ui.layout.Rect;

/**
 * The world, drawn into the view rect between the bars. Its height in world units is fixed - the
 * camera's distance - and its width is whatever keeps a world unit square in that rect, so nothing is
 * ever stretched and a wider view simply sees further to the sides.
 *
 * <p>It never moves the camera: {@link CameraDirector} decides where that looks. Because the screen
 * bounds carry the rect's offset, unprojecting the pointer through this viewport still works from over
 * a bar - it lands on the world beyond the view's edge, which is where the player is aiming.
 */
public final class LetterboxViewport extends Viewport {

    public LetterboxViewport() {
        setCamera(new OrthographicCamera());
    }

    /**
     * Fits the world to a rect of screen pixels. A rect with no area is ignored, so a minimised window
     * keeps the last good fit rather than dividing by zero.
     */
    public void fit(Rect screenPixels, float worldHeight) {
        if (screenPixels.isEmpty()) {
            return;
        }
        setWorldSize(worldHeight * screenPixels.width() / screenPixels.height(), worldHeight);
        setScreenBounds(screenPixels.x(), screenPixels.y(), screenPixels.width(), screenPixels.height());
        apply(false);
    }
}
