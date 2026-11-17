package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The player's settings on this computer, kept for the run when there is nowhere to keep them for good. */
class LocalSettingsTest {

    @Test
    void showsThePromptsUntilTheyAreSwitchedOff() {
        LocalSettings settings = LocalSettings.unsaved();

        assertThat(settings.prompts()).isTrue();
        settings.prompts(false);
        assertThat(settings.prompts()).isFalse();
        settings.prompts(true);
        assertThat(settings.prompts()).isTrue();
    }
}
