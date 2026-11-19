package pl.mzebrows.pwt.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ByteBucketTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void holdsASecondsWorthAtTheEdgeAndRefusesOneByteMore() {
        ByteBucket bucket = new ByteBucket(1000, 0L);

        assertThat(bucket.take(1000, 0L)).isTrue();
        assertThat(bucket.take(1, 0L)).isFalse();
    }

    @Test
    void refillsAtItsRateAndNeverPastASecondsWorth() {
        ByteBucket bucket = new ByteBucket(1000, 0L);
        bucket.take(1000, 0L);

        assertThat(bucket.take(500, SECOND / 2)).isTrue();
        assertThat(bucket.take(1, SECOND / 2)).isFalse();
        assertThat(bucket.take(1000, 10 * SECOND)).isTrue();
        assertThat(bucket.take(1, 10 * SECOND)).isFalse();
    }
}
