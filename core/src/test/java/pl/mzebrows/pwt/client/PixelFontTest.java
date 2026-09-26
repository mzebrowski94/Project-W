package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class PixelFontTest {

    private static final String CAPITALS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DESCENDERS = "gjpqy,;_";

    @Test
    void coversEveryPrintableAsciiCharacter() {
        for (char c = ' '; c <= '~'; c++) {
            assertThat(PixelFont.covers(c)).as("'%s'", c).isTrue();
        }
    }

    @Test
    void standsEveryCapitalAndDigitOnTheBaselineSevenPixelsTall() {
        for (char c : (CAPITALS + "0123456789").toCharArray()) {
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
            int height = 0;
            for (int row = 0; row < PixelFont.SIZE; row++) {
                if (rowLit(c, row)) {
                    height++;
                }
            }
            assertThat(height).as("'%s'", c).isGreaterThanOrEqualTo(5);
        }
    }

    /** libGDX's own parser reads the description, so what it decides is what the game will draw. */
    @Test
    void readsToLibGdxAsAMonospaceFontWhoseCapitalsTopTheLine() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(memory(PixelFont.description()), false);

        assertThat(data.capHeight).isCloseTo(PixelFont.BASELINE, Offset.offset(0.01f));
        assertThat(data.ascent).isCloseTo(0f, Offset.offset(0.01f));
        assertThat(data.getGlyph('W').xadvance).isEqualTo(PixelFont.ADVANCE);
        assertThat(data.getGlyph('i').xadvance).isEqualTo(PixelFont.ADVANCE);
        assertThat(data.getGlyph(' ').xadvance).isEqualTo(PixelFont.ADVANCE);
    }

    private static boolean rowLit(char c, int row) {
        for (int column = 0; column < PixelFont.GLYPH_WIDTH; column++) {
            if (PixelFont.lit(c, column, row)) {
                return true;
            }
        }
        return false;
    }

    private static FileHandle memory(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return new FileHandle() {
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
                return "memory";
            }

            @Override
            public String nameWithoutExtension() {
                return "memory";
            }
        };
    }
}
