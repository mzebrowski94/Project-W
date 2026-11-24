package pl.mzebrows.pwt.sim.system.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A slot's drafts are planned when the level is built (G0118): a count that grows slower than the level's sources,
 * the first at once and the rest one even gap apart, and a tier that opens by share of the plan.
 */
class DraftScheduleTest {

    private static final int DRAFTS = 6;
    private static final int SOURCES_REF = 22;
    private static final int CURVE = 60;

    @Test
    void plansTheConfiguredDraftsForTheReferenceLength() {
        assertThat(DraftSchedule.planned(DRAFTS, SOURCES_REF, SOURCES_REF, CURVE))
                .isEqualTo(DRAFTS);
    }

    /** A count that grows one for one with the length is ruled out (G0118): twice the sources is not twice the drafts. */
    @Test
    void plansFewerThanTwiceAsManyForTwiceTheSources() {
        int twice = DraftSchedule.planned(DRAFTS, 2 * SOURCES_REF, SOURCES_REF, CURVE);

        assertThat(twice).isGreaterThan(DRAFTS).isLessThan(2 * DRAFTS);
    }

    @Test
    void plansOneForOneAtAFullCurve() {
        for (int sources = 1; sources <= 40; sources++) {
            assertThat(DraftSchedule.planned(1, sources, 1, 100))
                    .as("%d sources", sources)
                    .isEqualTo(sources);
        }
    }

    @Test
    void plansNothingForALevelWithoutSourcesOrWithDraftsOff() {
        assertThat(DraftSchedule.planned(DRAFTS, 0, SOURCES_REF, CURVE)).isZero();
        assertThat(DraftSchedule.planned(0, SOURCES_REF, SOURCES_REF, CURVE)).isZero();
    }

    /** A source above none never plans none: one site is still one draft. */
    @Test
    void plansAtLeastOneForALevelWithASource() {
        assertThat(DraftSchedule.planned(DRAFTS, 1, SOURCES_REF, CURVE)).isEqualTo(1);
    }

    /** The first at once, the rest one gap of sources-over-drafts apart, carried in whole numbers. */
    @Test
    void earnsTheFirstAtOnceAndTheRestOneEvenGapApart() {
        assertThat(thresholds(SOURCES_REF, DRAFTS, true)).containsExactly(1, 4, 8, 12, 15, 19);
    }

    @Test
    void earnsTheLastOnTheLastSourceWhenTheFirstWaitsItsGap() {
        assertThat(thresholds(SOURCES_REF, DRAFTS, false)).containsExactly(3, 7, 11, 14, 18, 22);
    }

    @Test
    void earnsSeveralAtOnceWhenThePlanOutnumbersTheSources() {
        assertThat(thresholds(2, 4, true)).containsExactly(1, 1, 2, 2);
    }

    /** Six over twenty-two: thresholds 1, 4, 8, 12, 15, 19, and never more than the six past the last. */
    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "7, 2", "8, 3", "27, 6"})
    void owesEveryThresholdACountHasPassedAndNoMoreThanThePlan(int passed, int owed) {
        assertThat(DraftSchedule.owed(passed, SOURCES_REF, DRAFTS, true)).isEqualTo(owed);
    }

    @Test
    void owesNothingOnAPlanOfNone() {
        assertThat(DraftSchedule.owed(SOURCES_REF, SOURCES_REF, 0, true)).isZero();
    }

    /** By share of a slot's plan: 40 % of six opens on the fourth draft, once three are behind him. */
    @ParameterizedTest
    @CsvSource({"0, 0, true", "40, 2, false", "40, 3, true", "80, 4, false", "80, 5, true"})
    void opensATierOnceItsShareOfThePlanHasBeenEarned(int fromPercent, int earned, boolean open) {
        assertThat(DraftSchedule.opens(fromPercent, earned, DRAFTS)).isEqualTo(open);
    }

    private static List<Integer> thresholds(int sources, int planned, boolean firstAtOnce) {
        List<Integer> thresholds = new ArrayList<>();
        for (int draft = 1; draft <= planned; draft++) {
            thresholds.add(DraftSchedule.threshold(draft, sources, planned, firstAtOnce));
        }
        return thresholds;
    }
}
