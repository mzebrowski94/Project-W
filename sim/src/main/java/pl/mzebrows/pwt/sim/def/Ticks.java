package pl.mzebrows.pwt.sim.def;

/**
 * Seconds, as the config writes them, into the whole ticks the simulation counts.
 *
 * <p><strong>The one conversion.</strong> A timer is a count of ticks set once from a duration and
 * taken down by one each tick, so it lasts exactly what it says. Counting seconds down by a float tick
 * does not: 1/60 is not exact in a float, the residue left a sliver above zero on 71 of the shipped
 * durations, and every one of them ran a tick long - the flamer fired on every sixth tick instead of
 * every fifth, and nobody tuning it could have seen why.
 *
 * <p>Rounded to the nearest tick. Zero stays zero, because zero means off; anything above zero lasts at
 * least one tick, so a duration somebody asked for is never rounded away to nothing.
 */
public final class Ticks {

    private Ticks() {}

    /** How many whole ticks of {@code tickSeconds} a duration of {@code seconds} lasts. */
    public static int of(float seconds, float tickSeconds) {
        if (seconds <= 0f) {
            return 0;
        }
        return Math.max(1, Math.round(seconds / tickSeconds));
    }
}
