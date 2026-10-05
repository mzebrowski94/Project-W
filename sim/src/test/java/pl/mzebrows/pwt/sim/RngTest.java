package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RngTest {

    @Test
    void sameSeedProducesTheSameSequence() {
        Rng first = new Rng(12345L);
        Rng second = new Rng(12345L);

        for (int i = 0; i < 1000; i++) {
            assertThat(first.nextLong()).isEqualTo(second.nextLong());
        }
    }

    @Test
    void differentSeedsDiverge() {
        Rng first = new Rng(1L);
        Rng second = new Rng(2L);

        assertThat(first.nextLong()).isNotEqualTo(second.nextLong());
    }

    @Test
    void stateSnapshotReplaysTheSameSequence() {
        Rng rng = new Rng(7L);
        rng.nextLong();
        long snapshot = rng.state();
        long expected = rng.nextLong();

        rng.restore(snapshot);

        assertThat(rng.nextLong()).isEqualTo(expected);
    }

    @Test
    void nextFloatStaysInUnitRange() {
        Rng rng = new Rng(99L);

        for (int i = 0; i < 10_000; i++) {
            assertThat(rng.nextFloat()).isGreaterThanOrEqualTo(0f).isLessThan(1f);
        }
    }

    @Test
    void nextIntStaysInBounds() {
        Rng rng = new Rng(3L);

        for (int i = 0; i < 10_000; i++) {
            assertThat(rng.nextInt(6)).isBetween(0, 5);
        }
    }

    @Test
    void nextIntRejectsNonPositiveBounds() {
        Rng rng = new Rng(3L);

        assertThatThrownBy(() -> rng.nextInt(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStreamIsTheSameEveryRunForTheSameSeed() {
        Rng first = Rng.stream(42L, RngStream.WANDER);
        Rng second = Rng.stream(42L, RngStream.WANDER);

        for (int i = 0; i < 1000; i++) {
            assertThat(first.nextLong()).isEqualTo(second.nextLong());
        }
    }

    @Test
    void streamsForDifferentConcernsDiverge() {
        Rng leap = Rng.stream(42L, RngStream.LEAP);
        Rng wander = Rng.stream(42L, RngStream.WANDER);

        for (int i = 0; i < 100; i++) {
            assertThat(wander.nextLong()).isNotEqualTo(leap.nextLong());
        }
    }

    @Test
    void oneStreamIsNotAnotherAFewDrawsAhead() {
        Rng leap = Rng.stream(7L, RngStream.LEAP);
        Rng wander = Rng.stream(7L, RngStream.WANDER);
        long first = wander.nextLong();

        for (int i = 0; i < 64; i++) {
            assertThat(leap.nextLong()).isNotEqualTo(first);
        }
    }

    @Test
    void aStreamDoesNotFollowTheGeneratorItWasSeededFrom() {
        Rng level = new Rng(7L);
        Rng derived = Rng.stream(7L, RngStream.MAP);

        assertThat(derived.nextLong()).isNotEqualTo(level.nextLong());
    }
    /** Pinned before {@code stream} was rewritten on {@code derive}: every existing scenario lays out as it did. */
    @Test
    void aStreamGivesTheValuesItAlwaysHas() {
        assertThat(firstThree(Rng.stream(42L, RngStream.MAP)))
                .containsExactly(0x84B6_9AA1_3DCE_F2E7L, 0xCF06_BF71_8D28_C157L, 0x8668_E2E8_BF45_148EL);
        assertThat(firstThree(Rng.stream(7L, RngStream.WANDER)))
                .containsExactly(0xDB07_C196_736A_FB7AL, 0x7561_F53C_0319_6383L, 0x91FA_AB87_D6D8_3A4AL);
        assertThat(firstThree(Rng.stream(-3L, RngStream.ADAPTATION)))
                .containsExactly(0x0460_CC9D_18B6_13F5L, 0xBEB3_ED23_8462_A3C7L, 0x4C50_F186_0863_4B30L);
    }

    @Test
    void derivingDoesNotAdvanceTheParent() {
        Rng parent = new Rng(11L);
        Rng twin = new Rng(11L);

        parent.derive(5L);

        assertThat(parent.nextLong()).isEqualTo(twin.nextLong());
    }

    @Test
    void aChildIsAFunctionOfTheParentsStateAndTheSalt() {
        Rng parent = new Rng(11L);

        assertThat(parent.derive(5L).nextLong())
                .isEqualTo(new Rng(11L).derive(5L).nextLong());
        assertThat(parent.derive(5L).nextLong()).isNotEqualTo(parent.derive(6L).nextLong());
    }

    @Test
    void aStreamIsTheLevelSeedDerivedUnderItsConcern() {
        assertThat(Rng.stream(42L, RngStream.MAP).nextLong())
                .isEqualTo(new Rng(42L).derive(RngStream.MAP.salt()).nextLong());
    }

    private static long[] firstThree(Rng rng) {
        return new long[] {rng.nextLong(), rng.nextLong(), rng.nextLong()};
    }
}
