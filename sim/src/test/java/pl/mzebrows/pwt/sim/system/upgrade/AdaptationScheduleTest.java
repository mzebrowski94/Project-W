package pl.mzebrows.pwt.sim.system.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The swarm adapts on the drafts' schedule (G0118): a count planned from the level's sources, moved by its spread and
 * never past its share of the pool; each source worth more for every player seated when it is passed, and the share
 * risen by as much, but never past the ceiling. Its first waits a gap - the swarm answers a biome, it does not open
 * one.
 */
class AdaptationScheduleTest {

    private static final int ADAPTATIONS = 6;
    private static final int SOURCES_REF = 22;
    private static final int CURVE = 60;
    private static final int POOL = 18;
    private static final int MOST = 50;
    private static final int CEILING = 75;
    private static final int PER_PLAYER = 20;

    /** Half of eighteen ranks: nine. */
    private static final int SOLO_MOST = 9;

    @Test
    void plansTheConfiguredAdaptationsForTheReferenceLength() {
        assertThat(AdaptationSchedule.planned(ADAPTATIONS, SOURCES_REF, SOURCES_REF, CURVE, 0, SOLO_MOST))
                .isEqualTo(ADAPTATIONS);
    }

    @Test
    void movesThePlanByItsSpreadEitherWay() {
        assertThat(AdaptationSchedule.planned(ADAPTATIONS, SOURCES_REF, SOURCES_REF, CURVE, -1, SOLO_MOST))
                .isEqualTo(ADAPTATIONS - 1);
        assertThat(AdaptationSchedule.planned(ADAPTATIONS, SOURCES_REF, SOURCES_REF, CURVE, 1, SOLO_MOST))
                .isEqualTo(ADAPTATIONS + 1);
    }

    /** No run plans the whole pool: a long one stops at the share, and a spread never takes a plan below none. */
    @Test
    void plansNeverPastItsShareOfThePoolNorBelowNone() {
        assertThat(AdaptationSchedule.planned(ADAPTATIONS, 10 * SOURCES_REF, SOURCES_REF, CURVE, 1, SOLO_MOST))
                .isEqualTo(SOLO_MOST);
        assertThat(AdaptationSchedule.planned(ADAPTATIONS, 1, SOURCES_REF, CURVE, -1, SOLO_MOST))
                .isZero();
    }

    @Test
    void plansNothingWithAdaptationsOff() {
        assertThat(AdaptationSchedule.planned(0, SOURCES_REF, SOURCES_REF, CURVE, 1, SOLO_MOST))
                .isZero();
    }

    @Test
    void holdsAtMostItsShareOfThePoolAlone() {
        assertThat(AdaptationSchedule.most(POOL, MOST, CEILING, AdaptationSchedule.worth(PER_PLAYER, 1)))
                .isEqualTo(SOLO_MOST);
    }

    /** Two players lift half the pool by a fifth, 10.8 ranks, rounded once; eight would pass the ceiling's 13.5. */
    @ParameterizedTest
    @CsvSource({"2, 11", "4, 14", "8, 14"})
    void raisesTheShareWithThePlayersSeatedButNeverPastTheCeiling(int seated, int most) {
        assertThat(AdaptationSchedule.most(POOL, MOST, CEILING, AdaptationSchedule.worth(PER_PLAYER, seated)))
                .isEqualTo(most);
    }

    @ParameterizedTest
    @CsvSource({"0, 100", "1, 100", "2, 120", "4, 160"})
    void makesASourceWorthMoreForEveryPlayerSeatedPastTheFirst(int seated, int percent) {
        assertThat(AdaptationSchedule.worth(PER_PLAYER, seated)).isEqualTo(percent);
    }

    /**
     * Six over twenty-two, the first after its gap: 3, 7, 11, 14, 18, 22, counted in hundredths of a source. Past the
     * plan the gap goes on - 25, 29, 33 - for a count more players made worth more, up to the share.
     */
    @ParameterizedTest
    @CsvSource({"0, 0", "299, 0", "300, 1", "1100, 3", "2200, 6", "3300, 9", "9900, 9"})
    void owesEveryThresholdACountHasPassedUpToItsShare(int passed, int owed) {
        assertThat(AdaptationSchedule.owed(passed, SOURCES_REF, ADAPTATIONS, SOLO_MOST))
                .isEqualTo(owed);
    }

    @Test
    void owesNothingOnAPlanOfNone() {
        assertThat(AdaptationSchedule.owed(100 * SOURCES_REF, SOURCES_REF, 0, SOLO_MOST))
                .isZero();
    }
}
