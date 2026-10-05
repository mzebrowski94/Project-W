package pl.mzebrows.pwt.client.play;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.Log;
import pl.mzebrows.pwt.sim.def.TierKind;

/**
 * The mission record (G0067): what this machine remembers of the dailies it has won - per UTC day and tier, a
 * ✓ and the best time in ticks. One key per won (day, tier), {@code 20261005.H = 41237}: the day as {@code
 * yyyymmdd}, the tier's letter as in the code. A key there is the ✓. Kept forever; nothing prunes.
 *
 * <p>Read once from its {@link Store} and written through it on a win. <b>A garbled key is logged and
 * skipped</b>, the rest still read: records degrade, play never stops.
 */
public final class MissionRecords {

    /** The best of a daily not won: no ✓. */
    public static final long NONE = -1L;

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /** {@code yyyymmdd}, the dot, the tier's letter. */
    private static final int KEY_LENGTH = 10;

    private final Store store;
    private final Map<String, Long> bests = new HashMap<>();

    private MissionRecords(Store store) {
        this.store = store;
    }

    /** Where the record is kept: libGDX's preferences in the game, a map in a test. */
    public interface Store {

        /** Every key kept, as read when the game started; its values as text or as numbers. */
        Map<String, ?> kept();

        /** Keeps {@code ticks} under {@code key}, at once - a failure logged, never thrown. */
        void keep(String key, long ticks);
    }

    /** The record as {@code store} keeps it, each garbled key logged and skipped. */
    public static MissionRecords read(Store store) {
        MissionRecords records = new MissionRecords(store);
        for (Map.Entry<String, ?> entry : store.kept().entrySet()) {
            long ticks = ticks(entry.getValue());
            if (!valid(entry.getKey()) || ticks < 0) {
                Log.error(
                        "Mission record skipped, not a day and a best: " + entry.getKey() + "=" + entry.getValue(),
                        null);
                continue;
            }
            records.bests.put(entry.getKey(), ticks);
        }
        return records;
    }

    /** The best time of {@code day}'s daily on {@code tier}, in ticks - or {@link #NONE} if it was not won. */
    public long best(LocalDate day, TierKind tier) {
        Long best = bests.get(key(day, tier));
        return best == null ? NONE : best;
    }

    /**
     * A daily won in {@code ticks}: kept if it is the first win of that day and tier or strictly faster than its
     * best. {@code day} is null for a mission that is no daily, which is never remembered.
     *
     * @return whether it is a NEW BEST - and so was written
     */
    public boolean offer(@Nullable LocalDate day, TierKind tier, long ticks) {
        if (day == null) {
            return false;
        }
        long best = best(day, tier);
        if (best != NONE && ticks >= best) {
            return false;
        }
        String key = key(day, tier);
        bests.put(key, ticks);
        store.keep(key, ticks);
        return true;
    }

    private static String key(LocalDate day, TierKind tier) {
        return day.format(DAY) + "." + MissionCode.letter(tier);
    }

    private static boolean valid(String key) {
        if (key.length() != KEY_LENGTH || key.charAt(KEY_LENGTH - 2) != '.') {
            return false;
        }
        TierKind tier = MissionCode.tierOf(key.charAt(KEY_LENGTH - 1));
        if (tier == null) {
            return false;
        }
        try {
            return key(LocalDate.parse(key.substring(0, KEY_LENGTH - 2), DAY), tier)
                    .equals(key);
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** A kept value as ticks, or a negative number if it is none. */
    private static long ticks(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return NONE;
            }
        }
        return NONE;
    }
}
