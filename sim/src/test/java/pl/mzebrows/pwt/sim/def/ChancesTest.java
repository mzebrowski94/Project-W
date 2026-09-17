package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChancesTest {

    @Test
    void givesEveryOccasionalBehaviourANumber() {
        Chances chances = Chances.standard();

        for (AiChance kind : AiChance.values()) {
            assertThat(chances.of(kind)).isEqualTo(kind.standard());
        }
    }

    @Test
    void leavesEveryOtherBehaviourAloneWhenOneIsRetuned() {
        Chances retuned = Chances.standard().with(AiChance.SWING_AT_A_LEAP, 9f);

        assertThat(retuned.of(AiChance.SWING_AT_A_LEAP)).isEqualTo(9f);
        assertThat(retuned.of(AiChance.BITE_A_BARREL)).isEqualTo(AiChance.BITE_A_BARREL.standard());
    }

    @Test
    void comparesByValueSoAConfigCanBeComparedWithItsDefaults() {
        assertThat(Chances.standard()).isEqualTo(Chances.standard());
        assertThat(Chances.standard().with(AiChance.AID_A_TROOPER, 0f)).isNotEqualTo(Chances.standard());
    }
}
