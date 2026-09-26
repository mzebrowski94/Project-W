package pl.mzebrows.pwt.ui.nav;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleaseLatchTest {

    private static final int FIRE = 3;
    private static final int ACT = 5;

    @Test
    void ignoresAPressHeldAcrossAContextChangeUntilItIsReleased() {
        ReleaseLatch latch = new ReleaseLatch(8);

        latch.arm(input -> input == FIRE);

        assertThat(latch.filter(FIRE, true)).isFalse();
        assertThat(latch.filter(FIRE, true)).isFalse();
        assertThat(latch.filter(FIRE, false)).isFalse();
        assertThat(latch.filter(FIRE, true)).isTrue();
    }

    @Test
    void letsAFreshPressThroughAtOnce() {
        ReleaseLatch latch = new ReleaseLatch(8);

        latch.arm(input -> input == FIRE);

        assertThat(latch.filter(ACT, true)).isTrue();
    }

    @Test
    void passesThroughAnInputItDoesNotNumber() {
        ReleaseLatch latch = new ReleaseLatch(8);
        latch.arm(input -> true);

        assertThat(latch.filter(99, true)).isTrue();
    }
}
