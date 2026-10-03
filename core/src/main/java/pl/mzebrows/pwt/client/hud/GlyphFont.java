package pl.mzebrows.pwt.client.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.IntMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Turns a {@link GlyphTable} into a font libGDX draws: the glyphs painted into a texture in a grid, and
 * their metrics written in the AngelCode text format so libGDX's own parser decides every number the
 * way it would for a font file. Each glyph is trimmed to its lit rows, which is what puts capitals on
 * the baseline, accents above them and descenders below.
 */
public final class GlyphFont {

    private static final int COLUMNS = 32;
    private static final int GAP = 1;

    private GlyphFont() {}

    /**
     * Needs a GL context. The font owns its texture, draws at whole pixels only, and shows {@code ?} for
     * a character it lacks.
     */
    public static BitmapFont create(GlyphTable table) {
        Layout layout = layout(table);
        Pixmap pixmap = new Pixmap(layout.textureWidth, layout.textureHeight, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(Color.WHITE);
        for (int i = 0; i < layout.chars.length; i++) {
            char c = layout.chars[i];
            for (int row = 0; row < table.cellHeight(); row++) {
                for (int column = 0; column < layout.cellWidth; column++) {
                    if (table.lit(c, column, row)) {
                        pixmap.drawPixel(layout.x(i) + column, layout.y(i) + row);
                    }
                }
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(new TextFile(description(table, layout)), false);
        BitmapFont font = new BitmapFont(data, new TextureRegion(texture), true);
        font.setOwnsTexture(true);
        font.setUseIntegerPositions(true);
        data.missingGlyph = data.getGlyph('?');
        return font;
    }

    /** The font in the AngelCode text format. Pure, so a test can hand it to libGDX's parser. */
    static String description(GlyphTable table) {
        return description(table, layout(table));
    }

    private static String description(GlyphTable table, Layout layout) {
        StringBuilder text = new StringBuilder(64 * layout.chars.length);
        text.append("info face=\"pwt\" size=")
                .append(table.cellHeight())
                .append(
                        " bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1\n");
        text.append("common lineHeight=").append(table.cellHeight());
        text.append(" base=").append(table.baseline());
        text.append(" scaleW=").append(layout.textureWidth).append(" scaleH=").append(layout.textureHeight);
        text.append(" pages=1 packed=0\n");
        text.append("page id=0 file=\"pwt.png\"\n");
        text.append("chars count=").append(layout.chars.length).append('\n');
        for (int i = 0; i < layout.chars.length; i++) {
            char c = layout.chars[i];
            int[] rows = table.litRows(c);
            boolean blank = rows[0] > rows[1];
            text.append("char id=").append((int) c);
            text.append(" x=").append(layout.x(i)).append(" y=").append(layout.y(i) + (blank ? 0 : rows[0]));
            text.append(" width=").append(blank ? 0 : table.width(c));
            text.append(" height=").append(blank ? 0 : rows[1] - rows[0] + 1);
            text.append(" xoffset=0 yoffset=").append(blank ? 0 : rows[0]);
            text.append(" xadvance=").append(table.advance()).append(" page=0 chnl=15\n");
        }
        return text.toString();
    }

    private static Layout layout(GlyphTable table) {
        char[] chars = new char[table.glyphs().size];
        int count = 0;
        int widest = 1;
        for (IntMap.Entry<int[]> entry : table.glyphs().entries()) {
            chars[count++] = (char) entry.key;
        }
        Arrays.sort(chars);
        for (char c : chars) {
            widest = Math.max(widest, table.width(c));
        }
        int cellWidth = widest + GAP;
        int cellHeight = table.cellHeight() + GAP;
        int rows = (chars.length + COLUMNS - 1) / COLUMNS;
        return new Layout(chars, cellWidth, cellHeight, powerOfTwo(COLUMNS * cellWidth), powerOfTwo(rows * cellHeight));
    }

    private static int powerOfTwo(int at) {
        int size = 1;
        while (size < at) {
            size <<= 1;
        }
        return size;
    }

    /** Where each glyph sits in the texture: a grid, in the characters' own order. */
    private static final class Layout {

        final char[] chars;
        final int cellWidth;
        final int cellHeight;
        final int textureWidth;
        final int textureHeight;

        Layout(char[] chars, int cellWidth, int cellHeight, int textureWidth, int textureHeight) {
            this.chars = chars;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        int x(int index) {
            return (index % COLUMNS) * cellWidth;
        }

        int y(int index) {
            return (index / COLUMNS) * cellHeight;
        }
    }

    /** A font description held in memory, handed to libGDX's parser as if it were a file. */
    static final class TextFile extends FileHandle {

        private final byte[] bytes;

        TextFile(String text) {
            this.bytes = text.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public InputStream read() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public FileHandle parent() {
            return this;
        }

        @Override
        public FileHandle child(String name) {
            return this;
        }

        @Override
        public String path() {
            return "pwt-font";
        }

        @Override
        public String name() {
            return "pwt-font.fnt";
        }

        @Override
        public String nameWithoutExtension() {
            return "pwt-font";
        }
    }
}
