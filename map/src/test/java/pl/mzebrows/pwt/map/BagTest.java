package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A bag deals every value in a range once before any of them comes up again. The first test is the
 * whole promise; the rest are the edges a caller can actually reach - a range of one, and a range as
 * large as the bag holds.
 */
class BagTest {

    private static final int SIZE = 5;

    @Test
    void dealsEveryValueOnceBeforeAnyComesUpAgain() {
        Randomness rng = seeded();
        long drawn = 0L;
        List<Integer> dealt = new ArrayList<>();

        for (int i = 0; i < SIZE * 3; i++) {
            int value = Bag.draw(rng, drawn, SIZE);
            drawn = Bag.take(drawn, value, SIZE);
            dealt.add(value);
        }

        for (int bag = 0; bag < 3; bag++) {
            assertThat(dealt.subList(bag * SIZE, (bag + 1) * SIZE)).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
        }
    }

    @Test
    void drawsNothingForARangeOfOneValue() {
        int[] calls = {0};
        Randomness counted = () -> {
            calls[0]++;
            return 0L;
        };

        int value = Bag.draw(counted, 0L, 1);

        assertThat(value).isZero();
        assertThat(calls[0]).isZero();
    }

    @Test
    void holdsARangeAsWideAsALongHasBits() {
        Randomness rng = seeded();
        long drawn = 0L;
        boolean[] seen = new boolean[Bag.MOST];

        for (int i = 0; i < Bag.MOST; i++) {
            int value = Bag.draw(rng, drawn, Bag.MOST);
            drawn = Bag.take(drawn, value, Bag.MOST);
            seen[value] = true;
        }

        assertThat(seen).containsOnly(true);
        assertThat(drawn).isZero();
    }

    @Test
    void refusesARangeItCannotHold() {
        assertThatThrownBy(() -> Bag.draw(seeded(), 0L, Bag.MOST + 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Bag.draw(seeded(), 0L, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** The same Weyl sequence {@code MapsTest} uses: deterministic, and not the simulation's generator. */
    private static Randomness seeded() {
        long[] state = {0L};
        return () -> state[0] += 0x9E3779B97F4A7C15L;
    }
}
