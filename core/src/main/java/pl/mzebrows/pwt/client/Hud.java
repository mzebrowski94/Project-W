package pl.mzebrows.pwt.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import pl.mzebrows.pwt.ui.layout.LayoutSpec;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * Everything drawn in screen space rather than in the world: the UI canvas the bars are painted on,
 * and the older screen-pixel text the menu and the debug overlay still use.
 *
 * <p><b>The canvas</b> is the letterbox's pixel grid ({@link ScreenLayout}): one unit is one UI pixel, a
 * whole number of screen pixels square, drawn with {@link PixelFont} at whole positions only. It owns
 * the current layout, recomputed on every resize from the back buffer's size - physical pixels, so an
 * operating system's display scaling cannot put a fraction in it.
 *
 * <p><b>Screen text</b> ({@link #line}) is libGDX's built-in font at one screen pixel per unit, which
 * lives inside the gdx jar rather than in {@code assets/}, so the harness works with no art at all.
 * It stays until the menu and the overlay move onto the canvas.
 */
public final class Hud implements Disposable {

    private static final float MARGIN = 24f;

    private final ScreenViewport viewport = new ScreenViewport();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();

    private final LayoutSpec spec;
    private final PixelViewport canvas = new PixelViewport();
    private final BitmapFont pixelFont = PixelFont.create();
    private final Texture pixel = whitePixel();
    private final Texture reticle;
    private final Cursor blankCursor = blankCursor();
    private ScreenLayout layout = ScreenLayout.NONE;
    private boolean warnedSmall;
    private boolean cursorHidden;

    public Hud(LayoutSpec spec, int reticleSize) {
        this.spec = spec;
        this.reticle = reticle(reticleSize);
    }

    /**
     * Follows the window. A window with no area - a minimised one - keeps the last layout, so nothing
     * divides by zero and a restored window is right on its first frame.
     */
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        ScreenLayout solved =
                ScreenLayout.solve(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), spec);
        if (solved.canvas().isEmpty()) {
            return;
        }
        layout = solved;
        canvas.fit(layout);
        if (!warnedSmall && layout.canvas().height() < spec.canvasHeight()) {
            warnedSmall = true;
            Log.info("Window is " + Gdx.graphics.getBackBufferHeight() + " px tall, under the UI canvas's "
                    + spec.canvasHeight() + ": text is drawn below its legibility floor");
        }
    }

    /** Where everything goes on the screen as it is now. */
    public ScreenLayout layout() {
        return layout;
    }

    /** Starts a canvas pass. Every canvas call must sit between this and {@link #endCanvas()}. */
    public void beginCanvas() {
        canvas.apply();
        batch.setProjectionMatrix(canvas.getCamera().combined);
        batch.begin();
    }

    /** Fills a rectangle of UI pixels. */
    public void fill(int x, int y, int width, int height, Color color) {
        batch.setColor(color);
        batch.draw(pixel, x, y, width, height);
    }

    /** Outlines a rectangle of UI pixels, one pixel thick, inside its bounds. */
    public void frame(int x, int y, int width, int height, Color color) {
        fill(x, y, width, 1, color);
        fill(x, y + height - 1, width, 1, color);
        fill(x, y + 1, 1, height - 2, color);
        fill(x + width - 1, y + 1, 1, height - 2, color);
    }

    /** Draws one line of text whose capitals top out at {@code top}, both in UI pixels. */
    public void text(String text, int x, int top, Color color) {
        batch.setColor(Color.WHITE);
        pixelFont.setColor(color);
        pixelFont.draw(batch, text, x, top);
    }

    /**
     * Draws the reticle centred on a screen pixel, a UI pixel per texel, tinted. Screen pixels rather than
     * the UI grid, so it follows the mouse exactly instead of stepping by a whole UI pixel.
     *
     * @param y counted up from the bottom of the screen
     */
    public void reticle(int x, int y, Color color) {
        int size = reticle.getWidth() * layout.scale();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.setColor(color);
        batch.draw(reticle, x - size / 2, y - size / 2, size, size);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** Hides the operating system's pointer - in a level, where the reticle marks the aim. */
    public void hideSystemCursor() {
        if (!cursorHidden) {
            cursorHidden = true;
            Gdx.graphics.setCursor(blankCursor);
        }
    }

    /** Brings the operating system's pointer back - on pages, and before the world has arrived. */
    public void showSystemCursor() {
        if (cursorHidden) {
            cursorHidden = false;
            Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
        }
    }

    public void endCanvas() {
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** Starts a screen-text pass. Every {@link #line} call must sit between this and {@link #end()}. */
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
        pixelFont.dispose();
        pixel.dispose();
        reticle.dispose();
        blankCursor.dispose();
    }

    /**
     * The atlas's {@code ui/reticle}, before there is an atlas: a cross with a hole where the aim is,
     * white with a one-texel dark outline, so its tint reads on every ground and through a blast.
     */
    private static Texture reticle(int size) {
        int side = size | 1;
        int middle = side / 2;
        Pixmap pixmap = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        boolean[][] lit = new boolean[side][side];
        for (int i = 1; i < side - 1; i++) {
            if (Math.abs(i - middle) > 1) {
                lit[middle][i] = true;
                lit[i][middle] = true;
            }
        }
        pixmap.setColor(0f, 0f, 0f, 0.85f);
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                if (!lit[x][y] && touches(lit, x, y)) {
                    pixmap.drawPixel(x, y);
                }
            }
        }
        pixmap.setColor(Color.WHITE);
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                if (lit[x][y]) {
                    pixmap.drawPixel(x, y);
                }
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static boolean touches(boolean[][] lit, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < lit.length && ny < lit.length && lit[nx][ny]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Cursor blankCursor() {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        Cursor cursor = Gdx.graphics.newCursor(pixmap, 0, 0);
        pixmap.dispose();
        return cursor;
    }

    /** The one texture every fill is drawn from - the atlas's {@code ui/pixel}, before there is an atlas. */
    private static Texture whitePixel() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
