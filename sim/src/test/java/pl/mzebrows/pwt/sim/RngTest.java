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
}
