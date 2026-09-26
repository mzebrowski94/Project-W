package pl.mzebrows.pwt.ui.nav;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NavRepeatTest {

    private static final float FRAME = 0.01f;

    @Test
    void pressesOnceAtOnceThenRepeatsAfterTheDelayAtTheInterval() {
        NavRepeat repeat = new NavRepeat(0.35f, 0.09f);
        int presses = 0;

        for (int frame = 0; frame < 60; frame++) {
            if (repeat.step(true, FRAME)) {
                presses++;
            }
        }

        assertThat(presses).isEqualTo(1 + 1 + (int) ((0.6f - 0.35f) / 0.09f));
    }

    @Test
    void startsAgainAfterItIsLetGo() {
        NavRepeat repeat = new NavRepeat(0.35f, 0.09f);
        repeat.step(true, FRAME);
        repeat.step(false, FRAME);

        assertThat(repeat.step(true, FRAME)).isTrue();
    }

    @Test
    void pressesNothingWhileNothingIsHeld() {
        NavRepeat repeat = new NavRepeat(0.35f, 0.09f);

        assertThat(repeat.step(false, FRAME)).isFalse();
    }
}
