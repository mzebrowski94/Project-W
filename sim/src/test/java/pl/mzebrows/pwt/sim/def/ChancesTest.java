package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChancesTest {

    /** Zero is off and draws nothing, so a table nobody has filled in yet changes no roll. */
    @Test
    void startsWithEveryOccasionalBehaviourOff() {
        Chances chances = Chances.none();

        for (AiChance kind : AiChance.values()) {
            assertThat(chances.of(kind)).isZero();
        }
    }

    @Test
    void leavesEveryOtherBehaviourAloneWhenOneIsRetuned() {
        Chances base = Chances.none().with(AiChance.BITE_A_BARREL, 0.8f);

        Chances retuned = base.with(AiChance.SWING_AT_A_LEAP, 9f);

        assertThat(retuned.of(AiChance.SWING_AT_A_LEAP)).isEqualTo(9f);
        assertThat(retuned.of(AiChance.BITE_A_BARREL)).isEqualTo(0.8f);
    }

    @Test
    void comparesByValueSoAConfigCanBeComparedWithItsDefaults() {
        assertThat(Chances.none()).isEqualTo(Chances.none());
        assertThat(Chances.none().with(AiChance.AID_A_TROOPER, 0.4f)).isNotEqualTo(Chances.none());
        assertThat(Chances.none().withLongestGap(0f)).isNotEqualTo(Chances.none());
    }

    @Test
    void dealsEvenlyUnlessToldToTossACoin() {
        assertThat(Chances.none().longestGap()).isGreaterThanOrEqualTo(1f);
        assertThat(Chances.none().withLongestGap(0f).longestGap()).isZero();
    }

    @Test
    void refusesALongestGapNoDealCanKeepTo() {
        assertThatThrownBy(() -> Chances.none().withLongestGap(0.5f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Chances.none().withLongestGap(2.5f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheLongestGapWhenABehaviourIsRetuned() {
        Chances retuned = Chances.none().withLongestGap(1.5f).with(AiChance.SWING_AT_A_LEAP, 9f);

        assertThat(retuned.longestGap()).isEqualTo(1.5f);
    }
}
