package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The one rounding rule hit points and damage have (G0054). */
class WholeTest {

    @ParameterizedTest
    @CsvSource({"150, 125, 188", "250, 125, 313", "450, 125, 563", "7, 115, 8", "60, 125, 75", "100, 100, 100"})
    void roundsToTheNearestWithHalvesUp(int base, int percent, int whole) {
        assertThat(Whole.percentOf(base, percent)).isEqualTo(whole);
    }

    /** No percentage turns a blow that did something into one that does nothing. */
    @Test
    void neverRoundsSomethingDownToNothing() {
        assertThat(Whole.percentOf(1, 10)).isEqualTo(1);
    }

    @Test
    void leavesNothingAsNothing() {
        assertThat(Whole.percentOf(0, 200)).isZero();
    }

    @Test
    void holdsANumberFarAboveAnyInTheGameWithoutOverflowing() {
        assertThat(Whole.percentOf(1_000_000_000, 300)).isEqualTo(Integer.MAX_VALUE);
    }
}
