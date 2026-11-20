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

    @Test
    void remembersTheHostPasswordForTheRunAndStartsWithNone() {
        LocalSettings settings = LocalSettings.unsaved();

        assertThat(settings.hostPassword()).isEmpty();
        settings.hostPassword("KJ34");
        assertThat(settings.hostPassword()).isEqualTo("KJ34");
        assertThat(settings.relayHost()).isNull();
        assertThat(settings.relayPort()).isNull();
        settings.relay("relay.example.org", "");
        assertThat(settings.relayHost()).isEqualTo("relay.example.org");
        assertThat(settings.relayPort()).isNull();
    }
}
