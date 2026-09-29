package pl.mzebrows.pwt.sim.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Rng;

/** How cards are dealt: distinct, by weight, in deal order, and never what was just passed over if avoidable. */
class DealerTest {

    private static final int OPTIONS = 3;

    @Test
    void dealsDistinctKindsAndNoMoreThanAsked() {
        int[] out = new int[5];

        int dealt = Dealer.deal(new Rng(1L), new int[] {1, 1, 1, 1, 1}, 0L, OPTIONS, out);

        assertThat(dealt).isEqualTo(OPTIONS);
        assertThat(Arrays.stream(out, 0, dealt).distinct().count()).isEqualTo(OPTIONS);
    }

    @Test
    void neverDealsAKindWeightedZero() {
        for (long seed = 0; seed < 200; seed++) {
            int[] out = new int[4];

            int dealt = Dealer.deal(new Rng(seed), new int[] {5, 0, 5, 0}, 0L, OPTIONS, out);

            assertThat(Arrays.stream(out, 0, dealt)).containsOnly(0, 2);
        }
    }

    @Test
    void dealsFewerFromASmallPoolAndNothingFromAnEmptyOne() {
        assertThat(Dealer.deal(new Rng(1L), new int[] {3, 0, 4}, 0L, OPTIONS, new int[3]))
                .isEqualTo(2);
        assertThat(Dealer.deal(new Rng(1L), new int[] {0, 0, 0}, 0L, OPTIONS, new int[3]))
                .isZero();
    }

    /** A kind nine times the weight of another comes first about nine times in ten. */
    @Test
    void dealsTheHeavierKindFirstInProportionToItsWeight() {
        int heavierFirst = 0;
        Rng rng = new Rng(7L);
        for (int deal = 0; deal < 10_000; deal++) {
            int[] out = new int[2];
            Dealer.deal(rng, new int[] {9, 1}, 0L, 1, out);
            heavierFirst += out[0] == 0 ? 1 : 0;
        }

        assertThat(heavierFirst).isBetween(8_800, 9_200);
    }

    @Test
    void leavesOutWhatWasPassedOverWhileTheOthersLast() {
        for (long seed = 0; seed < 200; seed++) {
            int[] out = new int[5];

            int dealt = Dealer.deal(new Rng(seed), new int[] {1, 1, 1, 1, 1}, 0b00011L, OPTIONS, out);

            assertThat(Arrays.stream(out, 0, dealt)).containsExactlyInAnyOrder(2, 3, 4);
        }
    }

    /** The pool would run short without them, so they come back - after the others. */
    @Test
    void dealsWhatWasPassedOverLastWhenThePoolWouldRunShort() {
        int[] out = new int[3];

        int dealt = Dealer.deal(new Rng(3L), new int[] {1, 1, 1}, 0b011L, OPTIONS, out);

        assertThat(dealt).isEqualTo(OPTIONS);
        assertThat(out[0]).isEqualTo(2);
    }

    @Test
    void dealsTheSameCardsInTheSameOrderFromTheSameSeed() {
        int[] first = new int[5];
        int[] second = new int[5];

        Dealer.deal(new Rng(42L), new int[] {2, 3, 5, 7, 11}, 0L, OPTIONS, first);
        Dealer.deal(new Rng(42L), new int[] {2, 3, 5, 7, 11}, 0L, OPTIONS, second);

        assertThat(first).isEqualTo(second);
    }
}
