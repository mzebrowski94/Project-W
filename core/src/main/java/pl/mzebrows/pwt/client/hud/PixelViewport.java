package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.Viewport;
import pl.mzebrows.pwt.ui.layout.Rect;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * The UI canvas as a viewport: one world unit is one UI pixel, and the canvas fills its screen rect at
 * a whole scale, so a UI pixel is always a square of whole screen pixels. Everything drawn through it
 * is placed at whole UI pixels by whoever draws it.
 */
public final class PixelViewport extends Viewport {

    public PixelViewport() {
        setCamera(new OrthographicCamera());
    }

    /** Fits the canvas to a solved layout. A layout with no area is ignored: nothing to draw into. */
    public void fit(ScreenLayout layout) {
        Rect canvas = layout.canvas();
        if (canvas.isEmpty()) {
            return;
        }
        Rect onScreen = layout.toScreen(canvas);
        setWorldSize(canvas.width(), canvas.height());
        setScreenBounds(onScreen.x(), onScreen.y(), onScreen.width(), onScreen.height());
        apply(true);
    }
}
