package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Rng;

/** An emplacement's cycle is dealt once from the stream it is put down from, and a volley is not dealt at all (G0113). */
class CycleDefTest {

    private static final CycleDef.Dealt FOUR_TO_SIX = new CycleDef.Dealt(4, 6);

    private static final int SEEDS = 64;

    @Test
    void dealsTheSameCycleFromOneSeedAndOthersFromOthers() {
        Set<Cycle> dealt = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            Cycle once = FOUR_TO_SIX.deal(new Rng(seed));

            assertThat(FOUR_TO_SIX.deal(new Rng(seed))).isEqualTo(once);
            dealt.add(once);
        }

        assertThat(dealt).hasSizeGreaterThan(SEEDS / 2);
    }

    @Test
    void dealsBetweenItsFewestAndMostDifferentCompassPointsOneAStep() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Cycle cycle = FOUR_TO_SIX.deal(new Rng(seed));

            assertThat(cycle.length()).isBetween(4, 6);
            int seen = 0;
            for (int step = 0; step < cycle.length(); step++) {
                int points = cycle.points(step);
                assertThat(Integer.bitCount(points))
                        .as("seed %d step %d", seed, step)
                        .isOne();
                assertThat(seen & points).as("seed %d step %d", seed, step).isZero();
                seen |= points;
            }
        }
    }

    @Test
    void firesAVolleysBearingsTogetherInOneStep() {
        Cycle volley = new CycleDef.Volley(List.of(0, 90, 180, 270)).deal(new Rng(1L));

        assertThat(volley.length()).isOne();
        assertThat(volley.points(0)).isEqualTo(0b0101_0101);
    }

    @Test
    void refusesAVolleyBearingThatIsNoCompassPoint() {
        assertThatThrownBy(() -> new CycleDef.Volley(List.of(0, 30))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesADealtCycleLongerThanTheCompass() {
        assertThatThrownBy(() -> new CycleDef.Dealt(4, 9)).isInstanceOf(IllegalArgumentException.class);
    }
}
