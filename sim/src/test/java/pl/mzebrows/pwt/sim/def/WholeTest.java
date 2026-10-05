package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The one rounding rule a whole quantity has - hit points, damage, a count (G0054, G0063). */
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

    /**
     * Two percents are multiplied before the one rounding, never rounded in turn (G0063): a grunt's 150 at the
     * swarm's 125 and easy's 85 is 159.4, so 159, where 85 of the rounded 188 would be 160.
     */
    @ParameterizedTest
    @CsvSource({"150, 125, 115, 216", "250, 125, 85, 266", "150, 125, 85, 159", "11, 75, 130, 11", "7, 100, 100, 7"})
    void multipliesBothPercentsBeforeRoundingOnce(int base, int percent, int another, int whole) {
        assertThat(Whole.percentOf(base, percent, another)).isEqualTo(whole);
    }

    /** Easy's 75 never takes a roster line of one creature away. */
    @Test
    void neverRoundsSomethingDownToNothingUnderTwoPercents() {
        assertThat(Whole.percentOf(1, 75, 10)).isEqualTo(1);
    }

    @Test
    void leavesNothingAsNothingUnderTwoPercents() {
        assertThat(Whole.percentOf(0, 125, 130)).isZero();
    }

    @Test
    void holdsTwoPercentsFarAboveAnyInTheGameWithoutOverflowing() {
        assertThat(Whole.percentOf(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE))
                .isEqualTo(Integer.MAX_VALUE);
    }

    /** One percent is the other at a hundred: one rule, not two that happen to agree. */
    @ParameterizedTest
    @CsvSource({"150, 125", "7, 115", "1, 10", "333, 50"})
    void roundsOnePercentAsItRoundsThatPercentAndAHundred(int base, int percent) {
        assertThat(Whole.percentOf(base, percent)).isEqualTo(Whole.percentOf(base, percent, 100));
    }
}
