package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import java.nio.charset.StandardCharsets;
import pl.mzebrows.pwt.client.Assets;
import pl.mzebrows.pwt.client.Log;

/**
 * The interface's font: <b>monogram</b> by Vinícius Menézio (CC0, credits beside the file), built from
 * its glyph table at start-up - capitals seven pixels tall, room above them for accents and below for
 * descenders, every character six pixels on. It covers Latin-1, Latin Extended-A (Polish included) and
 * Cyrillic.
 *
 * <p>Built from the table rather than read as a TrueType file, so it needs no FreeType natives and is
 * exactly the pixels monogram draws. Missing or unreadable, the game falls back to {@link PixelFont},
 * which has the same metrics and no accents - logged once, never a crash.
 */
public final class UiFont {

    /** Pixels from one character to the next, in either font. Every fit in the bars is built on it. */
    public static final int ADVANCE = 6;

    /** Monogram's row just below its capitals: three rows of accents above them, seven of capital. */
    static final int MONOGRAM_BASELINE = 10;

    private UiFont() {}

    /** Needs a GL context. */
    public static BitmapFont load() {
        return GlyphFont.create(table());
    }

    /** Monogram's glyph table, or the fallback's if it cannot be read. */
    static GlyphTable table() {
        FileHandle file = Gdx.files.internal(Assets.UI_FONT);
        if (!file.exists()) {
            Log.error("Font " + Assets.UI_FONT + " not found; falling back to the built-in pixel font", null);
            return PixelFont.table();
        }
        try {
            return monogram(file.readString(StandardCharsets.UTF_8.name()));
        } catch (RuntimeException e) {
            Log.error("Font " + Assets.UI_FONT + " could not be read; falling back to the built-in pixel font", e);
            return PixelFont.table();
        }
    }

    /** Monogram from the text of its glyph table. */
    static GlyphTable monogram(String json) {
        return GlyphTable.fromJson(json, MONOGRAM_BASELINE, ADVANCE);
    }
}
