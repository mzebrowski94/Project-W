package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PanelAlarmsTest {

    private static final float ALARM = 0.15f;
    private static final float FRAME = 1f / 60f;

    @Test
    void flashesAndShakesOnceWhenHeGoesDown() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 1f);
        alarms.update(readout, FRAME);

        readout.player(0).posture = Posture.CASUALTY;
        alarms.update(readout, FRAME);

        assertThat(alarms.flashing(0)).isTrue();
        assertThat(alarms.shakeOffset(0)).isNotZero();
        assertThat(alarms.flashing(1)).isFalse();
    }

    @Test
    void settlesOnceTheMomentHasPassedThoughHeStaysDown() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 1f);
        alarms.update(readout, FRAME);
        readout.player(0).posture = Posture.CASUALTY;

        for (int frame = 0; frame < 30; frame++) {
            alarms.update(readout, FRAME);
        }

        assertThat(alarms.flashing(0)).isFalse();
        assertThat(alarms.shakeOffset(0)).isZero();
    }

    @Test
    void raisesItAgainWhenADownedManDies() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 1f);
        alarms.update(readout, FRAME);
        readout.player(0).posture = Posture.CASUALTY;
        for (int frame = 0; frame < 30; frame++) {
            alarms.update(readout, FRAME);
        }

        readout.player(0).posture = Posture.DYING;
        alarms.update(readout, FRAME);

        assertThat(alarms.flashing(0)).isTrue();
    }

    @Test
    void flashesWhenHisGunOverheats() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 1f);
        alarms.update(readout, FRAME);

        readout.player(0).overheated = true;
        alarms.update(readout, FRAME);

        assertThat(PanelAlarms.of(readout.player(0))).isEqualTo(PanelAlarms.Alarm.OVERHEAT);
        assertThat(alarms.flashing(0)).isTrue();
    }

    @Test
    void staysSilentOnTheFrameAWorldArrives() {
        Readout readout = standing();
        readout.player(0).posture = Posture.CASUALTY;
        PanelAlarms alarms = alarms(1f, 1f);

        alarms.update(readout, FRAME);

        assertThat(alarms.flashing(0)).isFalse();
    }

    @Test
    void keepsEveryFrameStillWithMotionOff() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(0f, 1f);
        alarms.update(readout, FRAME);

        readout.player(0).posture = Posture.CASUALTY;
        alarms.update(readout, FRAME);

        assertThat(alarms.shakeOffset(0)).isZero();
        assertThat(alarms.flashing(0)).isTrue();
    }

    @Test
    void neitherFlashesNorPulsesWithFlashesOff() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 0f);
        alarms.update(readout, FRAME);

        readout.player(0).posture = Posture.CASUALTY;
        for (int frame = 0; frame < 60; frame++) {
            alarms.update(readout, FRAME);
            assertThat(alarms.flashing(0)).isFalse();
            assertThat(alarms.alertBright()).isTrue();
        }
    }

    @Test
    void swellsFromDarkToLitAndBackOncePerCycle() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 1f);

        float start = alarms.swell(1f);
        alarms.update(readout, 0.5f);
        float peak = alarms.swell(1f);
        alarms.update(readout, 0.5f);
        float end = alarms.swell(1f);

        assertThat(start).isZero();
        assertThat(peak).isEqualTo(1f);
        assertThat(end).isZero();
    }

    @Test
    void holdsTheSwellLitWithFlashesOff() {
        Readout readout = standing();
        PanelAlarms alarms = alarms(1f, 0f);

        alarms.update(readout, 0.3f);

        assertThat(alarms.swell(1f)).isEqualTo(1f);
    }

    private static PanelAlarms alarms(float motionScale, float flashScale) {
        return new PanelAlarms(ALARM, 1, 2f, motionScale, flashScale);
    }

    private static Readout standing() {
        Readout readout = new Readout();
        readout.player(0).posture = Posture.STANDING;
        readout.player(1).posture = Posture.STANDING;
        return readout;
    }
}
