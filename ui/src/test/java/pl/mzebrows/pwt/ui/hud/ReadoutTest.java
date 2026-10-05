package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.Objective;

/** When the result screen is due (G0066): a level with an objective, decided, and the hold run out. */
class ReadoutTest {

    private static final float HOLD = 3f;

    @Test
    void isDueOnceEitherOutcomeHasHeldItsTime() {
        Readout won = withObjective();
        won.won = true;
        won.sinceOutcomeSeconds = HOLD;
        Readout lost = withObjective();
        lost.failed = true;
        lost.sinceOutcomeSeconds = HOLD;

        assertThat(won.resultDue(HOLD)).isTrue();
        assertThat(lost.resultDue(HOLD)).isTrue();
    }

    @Test
    void isNotDueDuringTheHold() {
        Readout readout = withObjective();
        readout.won = true;
        readout.sinceOutcomeSeconds = HOLD - 0.1f;

        assertThat(readout.resultDue(HOLD)).isFalse();
    }

    @Test
    void isNotDueBeforeAnOutcome() {
        Readout readout = withObjective();
        readout.sinceOutcomeSeconds = HOLD * 10f;

        assertThat(readout.resultDue(HOLD)).isFalse();
    }

    /** Lost with nothing to win - a sandbox, the proving ground - says so on the goal line and no more. */
    @Test
    void isNeverDueOnALevelWithoutAnObjective() {
        Readout readout = new Readout();
        readout.failed = true;
        readout.sinceOutcomeSeconds = HOLD;

        assertThat(readout.resultDue(HOLD)).isFalse();
    }

    private static Readout withObjective() {
        Readout readout = new Readout();
        readout.objectivesRequired = Readout.bit(Objective.CLEAR_ENEMIES);
        return readout;
    }
}
