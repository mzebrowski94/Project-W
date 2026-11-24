package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import pl.mzebrows.pwt.client.Log;
import pl.mzebrows.pwt.ui.hud.TallyIcon;
import pl.mzebrows.pwt.ui.layout.LayoutSpec;
import pl.mzebrows.pwt.ui.layout.ScreenLayout;

/**
 * Everything drawn in screen space rather than in the world: the UI canvas the bars, the menus and the
 * overlay are painted on, and the reticle.
 *
 * <p><b>The canvas</b> is the letterbox's pixel grid ({@link ScreenLayout}): one unit is one UI pixel, a
 * whole number of screen pixels square, drawn with the UI font at whole positions only. It owns the
 * current layout, recomputed on every resize from the back buffer's size - physical pixels, so an
 * operating system's display scaling cannot put a fraction in it.
 *
 * <p><b>The reticle</b> alone is placed in screen pixels, so it follows the mouse exactly.
 */
public final class Hud implements Disposable {

    private final ScreenViewport viewport = new ScreenViewport();
    private final SpriteBatch batch = new SpriteBatch();

    private final LayoutSpec spec;
    private final PixelViewport canvas = new PixelViewport();
    private final BitmapFont pixelFont = UiFont.load();
    private final Texture pixel = whitePixel();
    private final Texture reticle;
    private final Texture skull = icon(TallyIcon.KILLS);
    private final Texture flag = icon(TallyIcon.GOALS);
    private final Texture plus = icon(TallyIcon.STABILISED);
    private final Texture wound = icon(TallyIcon.WOUND);
    private final Texture woundSpare = icon(TallyIcon.WOUND_SPARE);
    private final Texture grenade = icon(TallyIcon.GRENADE);
    private final Texture grenadeSpare = icon(TallyIcon.GRENADE_SPARE);
    private final Texture sentry = icon(TallyIcon.SENTRY);
    private final Texture sentrySpare = icon(TallyIcon.SENTRY_SPARE);
    private final Texture dash = icon(TallyIcon.DASH);
    private final Texture dashSpare = icon(TallyIcon.DASH_SPARE);
    private final SystemPointer pointer;
    private ScreenLayout layout = ScreenLayout.NONE;
    private boolean warnedSmall;
    private boolean cursorHidden;

    public Hud(LayoutSpec spec, int reticleSize, SystemPointer pointer) {
        this.spec = spec;
        this.pointer = pointer;
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

    /** Draws a tally icon whose top is at {@code top}, like a capital's, a UI pixel per texel, tinted. */
    public void icon(TallyIcon icon, int x, int top, Color color) {
        batch.setColor(color);
        batch.draw(texture(icon), x, top - TallyIcon.SIZE, TallyIcon.SIZE, TallyIcon.SIZE);
        batch.setColor(Color.WHITE);
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
            pointer.hide();
        }
    }

    /** Brings the operating system's pointer back - on pages, and before the world has arrived. */
    public void showSystemCursor() {
        if (cursorHidden) {
            cursorHidden = false;
            pointer.show();
        }
    }

    public void endCanvas() {
        batch.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        pixelFont.dispose();
        pixel.dispose();
        reticle.dispose();
        skull.dispose();
        flag.dispose();
        plus.dispose();
        wound.dispose();
        woundSpare.dispose();
        dash.dispose();
        dashSpare.dispose();
    }

    /**
     * The atlas's {@code ui/reticle}, before there is an atlas: four short arms round an open middle,
     * white, each outlined by one dark texel along its sides and ends - never diagonally, so the middle
     * stays open and nothing dark sits where the aim is.
     */
    private static Texture reticle(int size) {
        int side = size | 1;
        int middle = side / 2;
        boolean[][] lit = new boolean[side][side];
        for (int i = 1; i < side - 1; i++) {
            if (Math.abs(i - middle) > 1) {
                lit[middle][i] = true;
                lit[i][middle] = true;
            }
        }
        Pixmap pixmap = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                if (lit[x][y]) {
                    pixmap.setColor(Color.WHITE);
                    pixmap.drawPixel(x, y);
                } else if (besideAnArm(lit, x, y)) {
                    pixmap.setColor(0f, 0f, 0f, 0.85f);
                    pixmap.drawPixel(x, y);
                }
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private Texture texture(TallyIcon icon) {
        return switch (icon) {
            case KILLS -> skull;
            case GOALS -> flag;
            case STABILISED -> plus;
            case WOUND -> wound;
            case WOUND_SPARE -> woundSpare;
            case GRENADE -> grenade;
            case GRENADE_SPARE -> grenadeSpare;
            case SENTRY -> sentry;
            case SENTRY_SPARE -> sentrySpare;
            case DASH -> dash;
            case DASH_SPARE -> dashSpare;
        };
    }

    /** A tally icon as a white texture to tint - the atlas's {@code ui/tally/*}, before there is an atlas. */
    private static Texture icon(TallyIcon icon) {
        Pixmap pixmap = new Pixmap(TallyIcon.SIZE, TallyIcon.SIZE, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        for (int y = 0; y < TallyIcon.SIZE; y++) {
            for (int x = 0; x < TallyIcon.SIZE; x++) {
                if (icon.lit(x, y)) {
                    pixmap.drawPixel(x, y);
                }
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    /** Next to a lit texel along a row or a column; the four diagonal neighbours do not count. */
    private static boolean besideAnArm(boolean[][] lit, int x, int y) {
        return isLit(lit, x - 1, y) || isLit(lit, x + 1, y) || isLit(lit, x, y - 1) || isLit(lit, x, y + 1);
    }

    private static boolean isLit(boolean[][] lit, int x, int y) {
        return x >= 0 && y >= 0 && x < lit.length && y < lit.length && lit[x][y];
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
