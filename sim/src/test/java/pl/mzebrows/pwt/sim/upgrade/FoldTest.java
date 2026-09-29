package pl.mzebrows.pwt.sim.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * How a number is worked out from its base. The first test is the one the rest of the game leans on:
 * with nothing held, every number is its base exactly, so a world with no upgrades is the world before
 * they existed, byte for byte.
 */
class FoldTest {

    private static final int FLOOR = -50;
    private static final int CEILING = 100;
    private static final Offset<Float> CLOSE = Offset.offset(1e-5f);

    @Test
    void handsBackTheBaseExactlyWhenNothingMovesIt() {
        assertThat(Fold.scale(0.1f, 0, FLOOR, CEILING)).isEqualTo(0.1f);
        assertThat(Fold.scaleTicks(7, 0, FLOOR, CEILING)).isEqualTo(7);
        assertThat(Fold.count(3, 0, 0)).isEqualTo(3);
        assertThat(Fold.chance(0.4f, 0f)).isEqualTo(0.4f);
    }

    @Test
    void scalesByAWholePercentage() {
        assertThat(Fold.scale(10f, 25, FLOOR, CEILING)).isCloseTo(12.5f, CLOSE);
        assertThat(Fold.scale(10f, -12, FLOOR, CEILING)).isCloseTo(8.8f, CLOSE);
    }

    @Test
    void holdsTheSumInsideTheStatsRange() {
        assertThat(Fold.scale(10f, -80, FLOOR, CEILING)).isCloseTo(5f, CLOSE);
        assertThat(Fold.scale(10f, 300, FLOOR, CEILING)).isCloseTo(20f, CLOSE);
    }

    @Test
    void roundsADurationInTicksToTheNearestTick() {
        assertThat(Fold.scaleTicks(10, -12, FLOOR, CEILING)).isEqualTo(9);
        assertThat(Fold.scaleTicks(10, -36, FLOOR, CEILING)).isEqualTo(6);
        assertThat(Fold.scaleTicks(10, 25, FLOOR, CEILING)).isEqualTo(13);
    }

    @Test
    void neverShortensADurationBelowOneTick() {
        assertThat(Fold.scaleTicks(2, -90, -95, CEILING)).isEqualTo(1);
        assertThat(Fold.scaleTicks(1, -50, FLOOR, CEILING)).isEqualTo(1);
    }

    /** A gun that fires every tick is left firing every tick, not slowed to every other one. */
    @Test
    void leavesADurationOfNoTicksAtNone() {
        assertThat(Fold.scaleTicks(0, -50, FLOOR, CEILING)).isZero();
    }

    @Test
    void addsToACountAndHoldsItAtItsFloor() {
        assertThat(Fold.count(3, 2, 0)).isEqualTo(5);
        assertThat(Fold.count(3, -5, 1)).isEqualTo(1);
    }

    @Test
    void raisesAChanceAndNeverTakesItBelowNothing() {
        assertThat(Fold.chance(0f, 0.3f)).isCloseTo(0.3f, CLOSE);
        assertThat(Fold.chance(0.4f, -1f)).isZero();
    }
}
