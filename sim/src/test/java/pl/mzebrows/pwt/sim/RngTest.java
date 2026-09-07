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
}
