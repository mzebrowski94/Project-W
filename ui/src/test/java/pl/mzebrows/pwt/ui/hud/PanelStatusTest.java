package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PanelStatusTest {

    @ParameterizedTest(name = "{0}, pinned {1}, stunned {2} -> {3}")
    @CsvSource({
        "ABSENT, false, false, NONE",
        "STANDING, false, false, NONE",
        "CASUALTY, false, false, DOWN",
        "DYING, false, false, REDEPLOY",
        "RESPAWNING, false, false, REDEPLOY",
        "ABOARD, false, false, ABOARD",
        "STANDING, true, false, PINNED",
        "STANDING, false, true, STUNNED",
        "CASUALTY, true, false, DOWN",
        "STANDING, true, true, PINNED",
        "ABOARD, false, true, STUNNED"
    })
    void putsTheWorstNewsFirst(Posture posture, boolean pinned, boolean stunned, PanelStatus expected) {
        PlayerReadout player = new PlayerReadout();
        player.posture = posture;
        player.pinned = pinned;
        player.stunned = stunned;

        assertThat(PanelStatus.of(player)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} in alert: {1}")
    @CsvSource({"NONE, false", "REDEPLOY, true", "DOWN, true", "PINNED, true", "STUNNED, false", "ABOARD, false"})
    void raisesTheAlertOnlyForWhatSomebodyMustActOn(PanelStatus status, boolean alert) {
        assertThat(status.alert()).isEqualTo(alert);
    }
}
