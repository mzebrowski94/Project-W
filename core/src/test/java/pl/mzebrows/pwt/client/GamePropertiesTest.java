package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A file on the classpath, read as it is written - or as nothing at all, never as a crash. */
class GamePropertiesTest {

    @Test
    void readsAFileAsItIsWritten() {
        assertThat(GameProperties.text("test-config.properties")).contains("a.int=7");
    }

    @Test
    void readsAMissingFileAsNothing() {
        assertThat(GameProperties.text("definitely-not-here.properties")).isEmpty();
    }
}
