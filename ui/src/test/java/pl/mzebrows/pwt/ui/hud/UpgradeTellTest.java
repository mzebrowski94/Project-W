package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UpgradeTellTest {

    private static final UpgradeTell TELL = new UpgradeTell("UPGRADE {0}", "UPGRADE x{1} {0}", "+{1} {0}");

    @Test
    void saysNothingWhileHeHoldsNoDraft() {
        assertThat(TELL.text(0, "C", 19)).isEmpty();
    }

    @Test
    void namesTheKeyAndTheCountWhenThereIsRoom() {
        assertThat(TELL.text(1, "C", 16)).isEqualTo("UPGRADE C");
        assertThat(TELL.text(3, "Y", 16)).isEqualTo("UPGRADE x3 Y");
    }

    @Test
    void shortensBesideAStatus() {
        assertThat(TELL.text(2, "Triangle", 11)).isEqualTo("+2 Triangle");
        assertThat(TELL.text(2, "C", 6)).isEqualTo("+2 C");
        assertThat(TELL.text(2, "Triangle", 8)).isEqualTo("+2");
    }

    /** A remote player's slot has no key on this machine: the count alone. */
    @Test
    void dropsAKeyThisMachineDoesNotHave() {
        assertThat(TELL.text(2, "", 16)).isEqualTo("UPGRADE x2");
    }

    @Test
    void neverTakesMoreColumnsThanItIsGiven() {
        assertThat(TELL.text(12, "Triangle", 5).length()).isLessThanOrEqualTo(5);
    }

    @Test
    void writesRanksInRomanNumerals() {
        assertThat(RankText.roman(1)).isEqualTo("I");
        assertThat(RankText.roman(3)).isEqualTo("III");
        assertThat(RankText.roman(11)).isEqualTo("11");
        assertThat(RankText.roman(0)).isEmpty();
    }
}
