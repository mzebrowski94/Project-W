package pl.mzebrows.pwt.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.PlayerSeats;
import pl.mzebrows.pwt.ui.hud.PromptKind;

/** What a prompt tells each seat to press, in that seat's own device's words (G0098). */
class PromptKeysTest {

    private final PromptKeys keys = new PromptKeys(ControlsConfig.defaults());

    @Test
    void namesTheKeyboardsKeysAndItsMouseButtonsShort() {
        keys.follow(PlayerSeats.KEYBOARD_SLOT, true, false);

        assertThat(keys.of(PromptKind.ACTION)).isEqualTo("E");
        assertThat(keys.of(PromptKind.STRIKE)).isEqualTo("RMB");
        assertThat(keys.of(PromptKind.UPGRADE)).isEqualTo("C");
        assertThat(keys.of(PromptKind.REDEPLOY)).isEqualTo("F");
    }

    @Test
    void namesAPadsButtonsByItsOwnLabels() {
        keys.follow(1, false, false);
        assertThat(keys.of(PromptKind.ACTION)).isEqualTo("A");
        assertThat(keys.of(PromptKind.STRIKE)).isEqualTo("LT");
        assertThat(keys.of(PromptKind.UPGRADE)).isEqualTo("Y");
        assertThat(keys.of(PromptKind.REDEPLOY)).isEqualTo("B");

        keys.follow(1, false, true);
        assertThat(keys.of(PromptKind.ACTION)).isEqualTo("Cross");
        assertThat(keys.of(PromptKind.STRIKE)).isEqualTo("L2");
    }

    /** Player one on a pad is told his pad's buttons, in seat zero as in any other. */
    @Test
    void namesAPadsButtonsInTheKeyboardsSeatWhenPlayerOneIsOnAPad() {
        keys.follow(PlayerSeats.KEYBOARD_SLOT, true, false);
        keys.follow(PlayerSeats.KEYBOARD_SLOT, false, false);

        assertThat(keys.of(PromptKind.ACTION)).isEqualTo("A");
        assertThat(keys.of(PromptKind.STRIKE)).isEqualTo("LT");
    }

    @Test
    void sayNothingForNoPrompt() {
        keys.follow(PlayerSeats.KEYBOARD_SLOT, true, false);

        assertThat(keys.of(PromptKind.NONE)).isEmpty();
    }

    @Test
    void namesEveryMouseButton() {
        assertThat(PromptKeys.mouse("LEFT")).isEqualTo("LMB");
        assertThat(PromptKeys.mouse("RIGHT")).isEqualTo("RMB");
        assertThat(PromptKeys.mouse("MIDDLE")).isEqualTo("MMB");
    }
}
