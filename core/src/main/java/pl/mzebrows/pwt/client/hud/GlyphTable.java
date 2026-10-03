package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * A monospace pixel font as data: for each character, one bit mask per row of its cell, bit 0 the
 * leftmost pixel. Both interface fonts are one - monogram, read from its glyph table, and the
 * hand-drawn {@link PixelFont} it falls back to - so there is one way to turn a font into something
 * libGDX draws ({@link GlyphFont}) and it can be tested without a display.
 *
 * <p>Every glyph shares one advance: widths are arithmetic ({@code TextFit}), whatever the font.
 *
 * @param cellHeight rows in every glyph's cell, from the top of accents to the bottom of descenders
 * @param baseline the row just below the capitals - where they stand
 * @param advance pixels from one character to the next
 */
public record GlyphTable(int cellHeight, int baseline, int advance, IntMap<int[]> glyphs) {

    /**
     * Reads a glyph table in monogram's format: a JSON object from each character to an array of row
     * masks, top row first.
     */
    public static GlyphTable fromJson(String json, int baseline, int advance) {
        JsonValue root = new JsonReader().parse(json);
        IntMap<int[]> glyphs = new IntMap<>();
        int height = 0;
        for (JsonValue glyph = root.child; glyph != null; glyph = glyph.next) {
            String name = glyph.name;
            if (name == null || name.length() != 1) {
                continue;
            }
            int[] rows = glyph.asIntArray();
            glyphs.put(name.charAt(0), rows);
            height = Math.max(height, rows.length);
        }
        return new GlyphTable(height, baseline, advance, glyphs);
    }

    public boolean covers(char c) {
        return glyphs.containsKey(c);
    }

    public boolean lit(char c, int column, int row) {
        int[] rows = glyphs.get(c);
        return rows != null && row < rows.length && (rows[row] >> column & 1) != 0;
    }

    /** Pixels from a glyph's left edge to its rightmost lit column, at least one. */
    public int width(char c) {
        int[] rows = glyphs.get(c);
        int bits = 0;
        if (rows != null) {
            for (int row : rows) {
                bits |= row;
            }
        }
        return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(bits));
    }

    /** The first and last row with anything lit, or {@code {cellHeight, -1}} for a blank glyph. */
    int[] litRows(char c) {
        int[] rows = glyphs.get(c);
        int first = cellHeight;
        int last = -1;
        if (rows != null) {
            for (int row = 0; row < rows.length; row++) {
                if (rows[row] != 0) {
                    first = Math.min(first, row);
                    last = row;
                }
            }
        }
        return new int[] {first, last};
    }
}
