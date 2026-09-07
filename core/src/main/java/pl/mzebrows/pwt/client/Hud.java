package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * Text drawn in screen space, shared by the menu and the debug overlay.
 *
 * <p>Separate from {@link WorldRenderer} because the world is measured in world units - the arena is
 * 160 across - and text measured in those would be several arena-widths tall. This has its own
 * viewport where one unit is one pixel.
 *
 * <p>The font is libGDX's built-in one, which lives inside the gdx jar rather than in {@code
 * assets/}. That is why the harness works with no art at all and why this does not need an entry in
 * {@link Assets}: there is no path to get wrong.
 */
public final class Hud implements Disposable {

    private static final float MARGIN = 24f;

    private final ScreenViewport viewport = new ScreenViewport();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /** Starts a text pass. Every {@link #line} call must sit between this and {@link #end()}. */
    public void begin() {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
    }

    /** Draws one line, counting down from the top of the screen. Row 0 is the topmost. */
    public void line(int row, String text) {
        font.draw(batch, text, MARGIN, viewport.getWorldHeight() - MARGIN - row * lineHeight());
    }

    /** Draws one line indented, for list items and hints. */
    public void indentedLine(int row, String text) {
        font.draw(batch, text, MARGIN * 2f, viewport.getWorldHeight() - MARGIN - row * lineHeight());
    }

    public void end() {
        batch.end();
    }

    public float lineHeight() {
        return font.getLineHeight() * 1.15f;
    }

    /** How many lines fit on screen, so a caller can anchor something to the bottom. */
    public int rows() {
        return (int) ((viewport.getWorldHeight() - MARGIN * 2f) / lineHeight());
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
