package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GamePropertiesTest {

    private static GameProperties config() {
        return GameProperties.load("test-config.properties");
    }

    @Test
    void readsEveryValueType() {
        GameProperties properties = config();

        assertThat(properties.getInt("a.int", 0)).isEqualTo(7);
        assertThat(properties.getLong("a.long", 0L)).isEqualTo(900_000_000_000L);
        assertThat(properties.getFloat("a.float", 0f)).isEqualTo(2.5f);
        assertThat(properties.getBoolean("a.bool", false)).isTrue();
        assertThat(properties.getString("a.text", "")).isEqualTo("hello");
    }

    @Test
    void fallsBackWhenAKeyIsMissing() {
        assertThat(config().getInt("no.such.key", 42)).isEqualTo(42);
    }

    @Test
    void fallsBackWhenAValueIsBlank() {
        assertThat(config().getString("a.blank", "default")).isEqualTo("default");
    }

    @Test
    void fallsBackWhenAValueIsNotANumber() {
        assertThat(config().getInt("a.notANumber", 3)).isEqualTo(3);
    }

    @Test
    void survivesAMissingFileEntirely() {
        GameProperties missing = GameProperties.load("definitely-not-here.properties");

        assertThat(missing.getInt("anything", 5)).isEqualTo(5);
    }
}
