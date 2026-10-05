package pl.mzebrows.pwt.ui.hud;

import java.util.Locale;

/**
 * A level's time - the ticks to its outcome (G0066) - as the player reads it, {@code m:ss.t}. The tenths are
 * cut, never rounded up, so a time shown is never one the level did not reach. Compared in ticks, never in
 * this text.
 */
public final class LevelTime {

    private static final long MILLIS_PER_TENTH = 100L;
    private static final long TENTHS_PER_SECOND = 10L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private LevelTime() {}

    /** {@code ticks} of {@code tickSeconds} each, as {@code m:ss.t}; the minutes go on past an hour. */
    public static String text(long ticks, float tickSeconds) {
        // Whole milliseconds first, so a tick length a float cannot hold exactly does not cut a tenth short.
        long tenths = Math.round(ticks * (double) tickSeconds * 1000.0) / MILLIS_PER_TENTH;
        long seconds = tenths / TENTHS_PER_SECOND;
        return String.format(
                Locale.ROOT,
                "%d:%02d.%d",
                seconds / SECONDS_PER_MINUTE,
                seconds % SECONDS_PER_MINUTE,
                tenths % TENTHS_PER_SECOND);
    }
}
