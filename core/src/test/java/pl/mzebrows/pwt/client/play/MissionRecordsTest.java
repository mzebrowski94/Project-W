package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.TierKind;

/** The mission record (G0067): a ✓ and a best time per won daily, one key per UTC day and tier. */
class MissionRecordsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private final Map<String, Object> kept = new LinkedHashMap<>();
    private final MissionRecords.Store store = new MissionRecords.Store() {
        @Override
        public Map<String, ?> kept() {
            return kept;
        }

        @Override
        public void keep(String key, long ticks) {
            kept.put(key, Long.toString(ticks));
        }
    };

    @Test
    void remembersAWonDailysTimeAsItsBestUnderTheDayAndTheTiersLetter() {
        MissionRecords records = MissionRecords.read(store);

        boolean newBest = records.offer(DAY, TierKind.HARD, 41_237L);

        assertThat(newBest).isTrue();
        assertThat(records.best(DAY, TierKind.HARD)).isEqualTo(41_237L);
        assertThat(kept).containsExactly(Map.entry("20261005.H", "41237"));
        assertThat(MissionRecords.read(store).best(DAY, TierKind.HARD)).isEqualTo(41_237L);
    }

    @Test
    void remembersNothingOfADailyNotWon() {
        MissionRecords records = MissionRecords.read(store);

        assertThat(records.best(DAY, TierKind.EASY)).isEqualTo(MissionRecords.NONE);
    }

    /** NEW BEST only for a time strictly lower; an equal one is not written again. */
    @Test
    void aBetterTimeReplacesAWorseAndAnEqualOrWorseOneDoesNot() {
        MissionRecords records = MissionRecords.read(store);
        records.offer(DAY, TierKind.MEDIUM, 30_000L);

        assertThat(records.offer(DAY, TierKind.MEDIUM, 30_000L)).isFalse();
        assertThat(records.offer(DAY, TierKind.MEDIUM, 31_000L)).isFalse();
        assertThat(records.best(DAY, TierKind.MEDIUM)).isEqualTo(30_000L);

        assertThat(records.offer(DAY, TierKind.MEDIUM, 29_999L)).isTrue();
        assertThat(kept).containsEntry("20261005.M", "29999");
    }

    @Test
    void keepsEachDayAndTierApart() {
        MissionRecords records = MissionRecords.read(store);

        records.offer(DAY, TierKind.HARD, 50_000L);
        records.offer(DAY.plusDays(1), TierKind.HARD, 40_000L);
        records.offer(DAY, TierKind.EASY, 20_000L);

        assertThat(records.best(DAY, TierKind.HARD)).isEqualTo(50_000L);
        assertThat(records.best(DAY.plusDays(1), TierKind.HARD)).isEqualTo(40_000L);
        assertThat(records.best(DAY, TierKind.EASY)).isEqualTo(20_000L);
        assertThat(records.best(DAY, TierKind.MEDIUM)).isEqualTo(MissionRecords.NONE);
    }

    /** Codes are not remembered: a mission that is no daily has no day, and writes nothing. */
    @Test
    void writesNothingForAMissionThatIsNoDaily() {
        MissionRecords records = MissionRecords.read(store);

        assertThat(records.offer(null, TierKind.HARD, 41_237L)).isFalse();
        assertThat(kept).isEmpty();
    }

    @Test
    void skipsAGarbledKeyAndReadsTheRest() {
        kept.put("20261005.H", "41237");
        kept.put("20261005.X", "100");
        kept.put("2026105.M", "100");
        kept.put("20261305.M", "100");
        kept.put("20261006.E", "soon");
        kept.put("20261006.M", "-5");
        kept.put("20261007.E", 12_000L);

        MissionRecords records = MissionRecords.read(store);

        assertThat(records.best(DAY, TierKind.HARD)).isEqualTo(41_237L);
        assertThat(records.best(DAY.plusDays(1), TierKind.EASY)).isEqualTo(MissionRecords.NONE);
        assertThat(records.best(DAY.plusDays(1), TierKind.MEDIUM)).isEqualTo(MissionRecords.NONE);
        assertThat(records.best(DAY.plusDays(2), TierKind.EASY)).isEqualTo(12_000L);
    }

    /** A garbled best is no best: a win that day writes over it. */
    @Test
    void aWinWritesOverAGarbledBest() {
        kept.put("20261005.E", "soon");
        MissionRecords records = MissionRecords.read(store);

        assertThat(records.offer(DAY, TierKind.EASY, 60_000L)).isTrue();
        assertThat(kept).containsEntry("20261005.E", "60000");
    }

    /**
     * A joiner whose day is already the next files the host's daily under the day it is the daily of, as
     * the host does - so both remember the same key.
     */
    @Test
    void aJoinerFilesADailyStartedTheDayBeforeItsOwnUnderThatDay() {
        MissionCode hosts = MissionCode.daily(DAY.toEpochDay(), TierKind.HARD, 6);
        MissionRecords records = MissionRecords.read(store);

        records.offer(hosts.dailyNear(DAY.plusDays(1), 6), hosts.tier(), 41_237L);

        assertThat(kept).containsOnlyKeys("20261005.H");
    }
}
