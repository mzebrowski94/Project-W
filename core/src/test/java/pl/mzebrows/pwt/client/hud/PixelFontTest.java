package pl.mzebrows.pwt.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class PixelFontTest {

    private static final GlyphTable TABLE = PixelFont.table();
    private static final String CAPITALS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DESCENDERS = "gjpqy,;_";

    @Test
    void coversEveryPrintableAsciiCharacter() {
        for (char c = ' '; c <= '~'; c++) {
            assertThat(TABLE.covers(c)).as("'%s'", c).isTrue();
        }
    }

    @Test
    void standsEveryCapitalAndDigitOnTheBaselineSevenPixelsTall() {
        String chars = CAPITALS + "0123456789";
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            assertThat(rowLit(c, 0)).as("top of '%s'", c).isTrue();
            assertThat(rowLit(c, PixelFont.BASELINE - 1)).as("foot of '%s'", c).isTrue();
            assertThat(rowLit(c, PixelFont.BASELINE)).as("below '%s'", c).isFalse();
        }
    }

    @Test
    void dropsBelowTheBaselineOnlyWhereALetterHasADescender() {
        for (char c = ' '; c <= '~'; c++) {
            assertThat(rowLit(c, PixelFont.BASELINE)).as("'%s'", c).isEqualTo(DESCENDERS.indexOf(c) >= 0);
        }
    }

    @Test
    void keepsLowercaseAtLeastFivePixelsTall() {
        for (char c = 'a'; c <= 'z'; c++) {
            int[] rows = TABLE.litRows(c);

            assertThat(rows[1] - rows[0] + 1).as("'%s'", c).isGreaterThanOrEqualTo(5);
        }
    }

    /** libGDX's own parser reads the description, so what it decides is what the game will draw. */
    @Test
    void readsToLibGdxAsAMonospaceFontWhoseCapitalsTopTheLine() {
        BitmapFont.BitmapFontData data = parse(TABLE);

        assertThat(data.capHeight).isCloseTo(7f, Offset.offset(0.01f));
        assertThat(data.ascent).isCloseTo(0f, Offset.offset(0.01f));
        assertThat(data.getGlyph('W').xadvance).isEqualTo(UiFont.ADVANCE);
        assertThat(data.getGlyph(' ').xadvance).isEqualTo(UiFont.ADVANCE);
    }

    static BitmapFont.BitmapFontData parse(GlyphTable table) {
        return new BitmapFont.BitmapFontData(new GlyphFont.TextFile(GlyphFont.description(table)), false);
    }

    private static boolean rowLit(char c, int row) {
        for (int column = 0; column < PixelFont.GLYPH_WIDTH; column++) {
            if (TABLE.lit(c, column, row)) {
                return true;
            }
        }
        return false;
    }
}
