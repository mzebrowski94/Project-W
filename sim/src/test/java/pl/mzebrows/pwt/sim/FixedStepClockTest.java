package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class FixedStepClockTest {

    private static final float TICK = 1f / 60f;

    private static FixedStepClock clock() {
        return new FixedStepClock(new SimConfig(1L, TICK, 5, 160f, 90f));
    }

    @Test
    void reportsNoStepsBeforeAFullTickHasAccumulated() {
        assertThat(clock().stepsFor(TICK / 2f)).isZero();
    }

    @Test
    void reportsOneStepPerAccumulatedTick() {
        FixedStepClock clock = clock();

        assertThat(clock.stepsFor(TICK)).isEqualTo(1);
        assertThat(clock.stepsFor(TICK * 3f)).isEqualTo(3);
    }

    @Test
    void carriesTheRemainderIntoTheNextFrame() {
        FixedStepClock clock = clock();

        assertThat(clock.stepsFor(TICK * 1.5f)).isEqualTo(1);
        assertThat(clock.stepsFor(TICK * 0.6f)).isEqualTo(1);
    }

    @Test
    void clampsLongStallsInsteadOfQueueingCatchUpSteps() {
        FixedStepClock clock = clock();

        assertThat(clock.stepsFor(10f)).isEqualTo(5);
        assertThat(clock.stepsFor(0f)).isZero();
    }

    @Test
    void exposesInterpolationAlphaBetweenSteps() {
        FixedStepClock clock = clock();
        clock.stepsFor(TICK * 1.5f);

        assertThat(clock.alpha()).isCloseTo(0.5f, Offset.offset(1e-4f));
    }
}
