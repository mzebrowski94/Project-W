package pl.mzebrows.pwt.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Every word the interface shows, by key; a missing one draws as itself rather than as nothing. */
class UiStringsTest {

    private static final Path SHIPPED = Path.of("..", "assets", "i18n", "ui.properties");

    /** The file is read as every config file is; every word in it reads as {@code java.util.Properties} read it. */
    @Test
    void readsEveryShippedWordAsItIsWritten() throws IOException {
        String text = Files.readString(SHIPPED, StandardCharsets.UTF_8);
        Properties properties = new Properties();
        properties.load(new StringReader(text));

        UiStrings strings = UiStrings.parse(text);

        assertThat(properties).isNotEmpty();
        for (String key : properties.stringPropertyNames()) {
            assertThat(strings.get(key)).as(key).isEqualTo(properties.getProperty(key));
        }
    }

    @Test
    void drawsAWordTheFileLacksAsItsKey() {
        assertThat(UiStrings.parse("ui.a=A\n").get("ui.b")).isEqualTo("#ui.b");
    }

    @Test
    void fillsInAWordsArguments() {
        assertThat(UiStrings.parse("ui.a=take {0} of {1}\n").format("ui.a", 2, "three"))
                .isEqualTo("take 2 of three");
    }
}
