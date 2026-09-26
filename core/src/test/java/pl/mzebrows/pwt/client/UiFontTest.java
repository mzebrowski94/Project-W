package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** The shipped font, read from the shipped file: what a player's screen will draw. */
class UiFontTest {

    private static final GlyphTable MONOGRAM =
            UiFont.monogram(read("../assets/ui/fonts/monogram/monogram-bitmap.json"));
    private static final String POLISH = "ĄĆĘŁŃÓŚŹŻąćęłńóśźż";

    @Test
    void coversPolish() {
        for (int i = 0; i < POLISH.length(); i++) {
            char c = POLISH.charAt(i);
            assertThat(MONOGRAM.covers(c)).as("'%s'", c).isTrue();
        }
    }

    @Test
    void coversEveryCharacterTheInterfaceSays() {
        String strings = read("../assets/i18n/ui.properties");

        for (int i = 0; i < strings.length(); i++) {
            char c = strings.charAt(i);
            if (c >= ' ' && c != '\\') {
                assertThat(MONOGRAM.covers(c)).as("'%s' in ui.properties", c).isTrue();
            }
        }
    }

    @Test
    void putsItsCapitalsAtTheTopOfTheLineWithRoomForAccentsAboveThem() {
        BitmapFont.BitmapFontData data = PixelFontTest.parse(MONOGRAM);

        assertThat(data.capHeight).isCloseTo(7f, Offset.offset(0.01f));
        assertThat(data.ascent).isCloseTo(3f, Offset.offset(0.01f));
        assertThat(MONOGRAM.litRows('Ś')[0]).isLessThan(UiFont.MONOGRAM_BASELINE - 7);
    }

    @Test
    void advancesEveryCharacterAsFarAsTheFallbackDoes() {
        BitmapFont.BitmapFontData data = PixelFontTest.parse(MONOGRAM);

        String chars = "Wi 0." + POLISH;
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            assertThat(data.getGlyph(c).xadvance).as("'%s'", c).isEqualTo(UiFont.ADVANCE);
        }
    }

    @Test
    void topsEveryCapitalSevenPixelsAboveTheBaseline() {
        for (char c = 'A'; c <= 'Z'; c++) {
            assertThat(UiFont.MONOGRAM_BASELINE - MONOGRAM.litRows(c)[0])
                    .as("'%s'", c)
                    .isEqualTo(7);
        }
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }
}
