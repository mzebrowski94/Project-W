package pl.mzebrows.pwt.client;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A 5 x 8 pixel font for printable ASCII, drawn in code: the font the interface falls back to when no
 * font file is present, and the one it uses until one is chosen (plan Q3, decision D6).
 *
 * <p>It exists because the fallback must have the <b>real metrics</b>. Every slot in the bars is sized by
 * arithmetic - characters times the advance - and a proportional fallback such as libGDX's own font
 * would make every fit a lie on exactly the machine where the file went missing. So: capitals seven
 * pixels tall on a baseline, lowercase five with descenders below it, every glyph five wide, advancing
 * six. Anything outside printable ASCII draws as {@code ?}.
 *
 * <p>The glyphs are written as rows of {@code #} and {@code .}, top row first, row 7 below the baseline.
 * Editing one is editing its string; nothing else knows the shapes.
 */
public final class PixelFont {

    /** Pixels from the top of a line to the bottom of its descenders. */
    public static final int SIZE = 8;

    /** Pixels from one character's left edge to the next's. */
    public static final int ADVANCE = 6;

    /** Pixels from the top of a line down to the baseline capitals stand on. */
    static final int BASELINE = 7;

    static final int GLYPH_WIDTH = 5;
    static final char FIRST = ' ';
    static final char LAST = '~';

    private static final int COLUMNS = 16;
    private static final int CELL_WIDTH = 8;
    private static final int CELL_HEIGHT = 10;
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 64;

    /** One string per character from {@link #FIRST}: its eight rows concatenated, {@code #} lit. */
    private static final String[] ROWS = new String[LAST - FIRST + 1];

    static {
        glyph(' ', ".....|.....|.....|.....|.....|.....|.....|.....");
        glyph('!', "..#..|..#..|..#..|..#..|..#..|.....|..#..|.....");
        glyph('"', ".#.#.|.#.#.|.....|.....|.....|.....|.....|.....");
        glyph('#', ".#.#.|.#.#.|#####|.#.#.|#####|.#.#.|.#.#.|.....");
        glyph('$', "..#..|.####|#.#..|.###.|..#.#|####.|..#..|.....");
        glyph('%', "##...|##..#|...#.|..#..|.#...|#..##|...##|.....");
        glyph('&', ".##..|#..#.|#.#..|.#...|#.#.#|#..#.|.##.#|.....");
        glyph('\'', "..#..|..#..|.....|.....|.....|.....|.....|.....");
        glyph('(', "...#.|..#..|.#...|.#...|.#...|..#..|...#.|.....");
        glyph(')', ".#...|..#..|...#.|...#.|...#.|..#..|.#...|.....");
        glyph('*', ".....|..#..|#.#.#|.###.|#.#.#|..#..|.....|.....");
        glyph('+', ".....|..#..|..#..|#####|..#..|..#..|.....|.....");
        glyph(',', ".....|.....|.....|.....|.....|..#..|..#..|.#...");
        glyph('-', ".....|.....|.....|#####|.....|.....|.....|.....");
        glyph('.', ".....|.....|.....|.....|.....|.##..|.##..|.....");
        glyph('/', ".....|....#|...#.|..#..|.#...|#....|.....|.....");
        glyph('0', ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.|.....");
        glyph('1', "..#..|.##..|..#..|..#..|..#..|..#..|.###.|.....");
        glyph('2', ".###.|#...#|....#|...#.|..#..|.#...|#####|.....");
        glyph('3', "#####|...#.|..#..|...#.|....#|#...#|.###.|.....");
        glyph('4', "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.|.....");
        glyph('5', "#####|#....|####.|....#|....#|#...#|.###.|.....");
        glyph('6', "..##.|.#...|#....|####.|#...#|#...#|.###.|.....");
        glyph('7', "#####|....#|...#.|..#..|.#...|.#...|.#...|.....");
        glyph('8', ".###.|#...#|#...#|.###.|#...#|#...#|.###.|.....");
        glyph('9', ".###.|#...#|#...#|.####|....#|...#.|.##..|.....");
        glyph(':', ".....|.##..|.##..|.....|.##..|.##..|.....|.....");
        glyph(';', ".....|.....|.....|.....|.##..|.##..|..#..|.#...");
        glyph('<', "...#.|..#..|.#...|#....|.#...|..#..|...#.|.....");
        glyph('=', ".....|.....|#####|.....|#####|.....|.....|.....");
        glyph('>', ".#...|..#..|...#.|....#|...#.|..#..|.#...|.....");
        glyph('?', ".###.|#...#|....#|...#.|..#..|.....|..#..|.....");
        glyph('@', ".###.|#...#|....#|.##.#|#.#.#|#.#.#|.###.|.....");
        glyph('A', ".###.|#...#|#...#|#...#|#####|#...#|#...#|.....");
        glyph('B', "####.|#...#|#...#|####.|#...#|#...#|####.|.....");
        glyph('C', ".###.|#...#|#....|#....|#....|#...#|.###.|.....");
        glyph('D', "###..|#..#.|#...#|#...#|#...#|#..#.|###..|.....");
        glyph('E', "#####|#....|#....|####.|#....|#....|#####|.....");
        glyph('F', "#####|#....|#....|####.|#....|#....|#....|.....");
        glyph('G', ".###.|#...#|#....|#.###|#...#|#...#|.####|.....");
        glyph('H', "#...#|#...#|#...#|#####|#...#|#...#|#...#|.....");
        glyph('I', ".###.|..#..|..#..|..#..|..#..|..#..|.###.|.....");
        glyph('J', "..###|...#.|...#.|...#.|...#.|#..#.|.##..|.....");
        glyph('K', "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#|.....");
        glyph('L', "#....|#....|#....|#....|#....|#....|#####|.....");
        glyph('M', "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#|.....");
        glyph('N', "#...#|#...#|##..#|#.#.#|#..##|#...#|#...#|.....");
        glyph('O', ".###.|#...#|#...#|#...#|#...#|#...#|.###.|.....");
        glyph('P', "####.|#...#|#...#|####.|#....|#....|#....|.....");
        glyph('Q', ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#|.....");
        glyph('R', "####.|#...#|#...#|####.|#.#..|#..#.|#...#|.....");
        glyph('S', ".####|#....|#....|.###.|....#|....#|####.|.....");
        glyph('T', "#####|..#..|..#..|..#..|..#..|..#..|..#..|.....");
        glyph('U', "#...#|#...#|#...#|#...#|#...#|#...#|.###.|.....");
        glyph('V', "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..|.....");
        glyph('W', "#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.|.....");
        glyph('X', "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#|.....");
        glyph('Y', "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..|.....");
        glyph('Z', "#####|....#|...#.|..#..|.#...|#....|#####|.....");
        glyph('[', ".###.|.#...|.#...|.#...|.#...|.#...|.###.|.....");
        glyph('\\', ".....|#....|.#...|..#..|...#.|....#|.....|.....");
        glyph(']', ".###.|...#.|...#.|...#.|...#.|...#.|.###.|.....");
        glyph('^', "..#..|.#.#.|#...#|.....|.....|.....|.....|.....");
        glyph('_', ".....|.....|.....|.....|.....|.....|.....|#####");
        glyph('`', ".#...|..#..|.....|.....|.....|.....|.....|.....");
        glyph('a', ".....|.....|.###.|....#|.####|#...#|.####|.....");
        glyph('b', "#....|#....|####.|#...#|#...#|#...#|####.|.....");
        glyph('c', ".....|.....|.###.|#....|#....|#...#|.###.|.....");
        glyph('d', "....#|....#|.####|#...#|#...#|#...#|.####|.....");
        glyph('e', ".....|.....|.###.|#...#|#####|#....|.###.|.....");
        glyph('f', "..##.|.#..#|.#...|###..|.#...|.#...|.#...|.....");
        glyph('g', ".....|.....|.####|#...#|#...#|.####|....#|.###.");
        glyph('h', "#....|#....|####.|#...#|#...#|#...#|#...#|.....");
        glyph('i', "..#..|.....|.##..|..#..|..#..|..#..|.###.|.....");
        glyph('j', "...#.|.....|..##.|...#.|...#.|...#.|#..#.|.##..");
        glyph('k', "#....|#....|#..#.|#.#..|##...|#.#..|#..#.|.....");
        glyph('l', ".##..|..#..|..#..|..#..|..#..|..#..|.###.|.....");
        glyph('m', ".....|.....|##.#.|#.#.#|#.#.#|#.#.#|#.#.#|.....");
        glyph('n', ".....|.....|####.|#...#|#...#|#...#|#...#|.....");
        glyph('o', ".....|.....|.###.|#...#|#...#|#...#|.###.|.....");
        glyph('p', ".....|.....|####.|#...#|#...#|####.|#....|#....");
        glyph('q', ".....|.....|.####|#...#|#...#|.####|....#|....#");
        glyph('r', ".....|.....|#.##.|##..#|#....|#....|#....|.....");
        glyph('s', ".....|.....|.####|#....|.###.|....#|####.|.....");
        glyph('t', ".#...|.#...|###..|.#...|.#...|.#..#|..##.|.....");
        glyph('u', ".....|.....|#...#|#...#|#...#|#..##|.##.#|.....");
        glyph('v', ".....|.....|#...#|#...#|#...#|.#.#.|..#..|.....");
        glyph('w', ".....|.....|#...#|#...#|#.#.#|#.#.#|.#.#.|.....");
        glyph('x', ".....|.....|#...#|.#.#.|..#..|.#.#.|#...#|.....");
        glyph('y', ".....|.....|#...#|#...#|#...#|.####|....#|.###.");
        glyph('z', ".....|.....|#####|...#.|..#..|.#...|#####|.....");
        glyph('{', "...##|..#..|..#..|.#...|..#..|..#..|...##|.....");
        glyph('|', "..#..|..#..|..#..|..#..|..#..|..#..|..#..|.....");
        glyph('}', "##...|..#..|..#..|...#.|..#..|..#..|##...|.....");
        glyph('~', ".....|.....|.#...|#.#.#|...#.|.....|.....|.....");
    }

    private PixelFont() {}

    /** Whether this pixel of this character is lit. Row 0 is the top of the line. */
    static boolean lit(char c, int column, int row) {
        return ROWS[c - FIRST].charAt(row * GLYPH_WIDTH + column) == '#';
    }

    static boolean covers(char c) {
        return c >= FIRST && c <= LAST && ROWS[c - FIRST] != null;
    }

    /**
     * The font described in the AngelCode text format libGDX reads, so its own parser decides every
     * metric from the same numbers a font file would carry. Each glyph is trimmed to its lit rows, which
     * is what puts capitals on the baseline and descenders below it.
     */
    static String description() {
        StringBuilder text = new StringBuilder(4096);
        text.append("info face=\"pwt-pixel\" size=")
                .append(SIZE)
                .append(
                        " bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1\n");
        text.append("common lineHeight=")
                .append(SIZE)
                .append(" base=")
                .append(BASELINE)
                .append(" scaleW=")
                .append(TEXTURE_WIDTH)
                .append(" scaleH=")
                .append(TEXTURE_HEIGHT)
                .append(" pages=1 packed=0\n");
        text.append("page id=0 file=\"pixel.png\"\n");
        text.append("chars count=").append(ROWS.length).append('\n');
        for (char c = FIRST; c <= LAST; c++) {
            int first = firstLitRow(c);
            int last = lastLitRow(c);
            boolean blank = first > last;
            text.append("char id=").append((int) c);
            text.append(" x=").append(cellX(c)).append(" y=").append(cellY(c) + (blank ? 0 : first));
            text.append(" width=").append(blank ? 0 : GLYPH_WIDTH);
            text.append(" height=").append(blank ? 0 : last - first + 1);
            text.append(" xoffset=0 yoffset=").append(blank ? 0 : first);
            text.append(" xadvance=").append(ADVANCE).append(" page=0 chnl=15\n");
        }
        return text.toString();
    }

    /**
     * Paints the glyphs into a texture and hands it to libGDX as a font. Needs a GL context. The font
     * owns the texture, draws at whole pixels only, and shows {@code ?} for a character it lacks.
     */
    public static BitmapFont create() {
        Pixmap pixmap = new Pixmap(TEXTURE_WIDTH, TEXTURE_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(Color.WHITE);
        for (char c = FIRST; c <= LAST; c++) {
            for (int row = 0; row < SIZE; row++) {
                for (int column = 0; column < GLYPH_WIDTH; column++) {
                    if (lit(c, column, row)) {
                        pixmap.drawPixel(cellX(c) + column, cellY(c) + row);
                    }
                }
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(new TextFile(description()), false);
        BitmapFont font = new BitmapFont(data, new TextureRegion(texture), true);
        font.setOwnsTexture(true);
        font.setUseIntegerPositions(true);
        data.missingGlyph = data.getGlyph('?');
        return font;
    }

    private static int cellX(char c) {
        return ((c - FIRST) % COLUMNS) * CELL_WIDTH;
    }

    private static int cellY(char c) {
        return ((c - FIRST) / COLUMNS) * CELL_HEIGHT;
    }

    private static int firstLitRow(char c) {
        for (int row = 0; row < SIZE; row++) {
            if (rowLit(c, row)) {
                return row;
            }
        }
        return SIZE;
    }

    private static int lastLitRow(char c) {
        for (int row = SIZE - 1; row >= 0; row--) {
            if (rowLit(c, row)) {
                return row;
            }
        }
        return -1;
    }

    private static boolean rowLit(char c, int row) {
        for (int column = 0; column < GLYPH_WIDTH; column++) {
            if (lit(c, column, row)) {
                return true;
            }
        }
        return false;
    }

    private static void glyph(char c, String rows) {
        String packed = rows.replace("|", "");
        if (packed.length() != GLYPH_WIDTH * SIZE) {
            throw new IllegalStateException("glyph '" + c + "' is not " + GLYPH_WIDTH + " x " + SIZE);
        }
        ROWS[c - FIRST] = packed;
    }

    /** A font description held in memory, handed to libGDX's parser as if it were a file. */
    private static final class TextFile extends FileHandle {

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
            return "pwt-pixel";
        }

        @Override
        public String name() {
            return "pwt-pixel.fnt";
        }

        @Override
        public String nameWithoutExtension() {
            return "pwt-pixel";
        }
    }
}
