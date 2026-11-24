package pl.mzebrows.pwt.sim.system.upgrade;

import pl.mzebrows.pwt.sim.def.Whole;

/**
 * When a slot's drafts fall (G0118), fixed when the level is built: how many a level of so many weighed sources
 * plans, the count of sources passed each one waits for, and when a tier opens. The same thresholds for every
 * slot; what differs is each slot's own count. Pure, and allocation-free.
 */
final class DraftSchedule {

    private static final int HUNDRED = 100;

    private DraftSchedule() {}

    /**
     * How many drafts a level of {@code sources} weighed sources plans: {@code base x (sources / sourcesRef) ^
     * (curvePercent / 100)}, the power taken once with {@code StrictMath} and rounded once by {@link Whole#nearest}.
     * None for no sources, or a base of none.
     */
    static int planned(int base, int sources, int sourcesRef, int curvePercent) {
        if (base <= 0 || sources <= 0) {
            return 0;
        }
        double curve = StrictMath.pow((double) sources / sourcesRef, curvePercent / (double) HUNDRED);
        return Whole.nearest(base * curve);
    }

    /**
     * The count of sources passed the {@code draft}-th of {@code planned} waits for, counting from one: one gap of
     * {@code sources / planned} each, the remainder carried in whole numbers - the first at once if {@code
     * firstAtOnce} and the rest a gap after it, otherwise the first after its gap and the last on the last source.
     * Never below one, so nothing is earned before anything is passed.
     */
    static int threshold(int draft, int sources, int planned, boolean firstAtOnce) {
        if (firstAtOnce) {
            return 1 + (int) ((long) (draft - 1) * sources / planned);
        }
        return Math.max(1, (int) ((long) draft * sources / planned));
    }

    /** How many of {@code planned} drafts a count of {@code passed} sources has reached. */
    static int owed(int passed, int sources, int planned, boolean firstAtOnce) {
        int owed = 0;
        while (owed < planned && threshold(owed + 1, sources, planned, firstAtOnce) <= passed) {
            owed++;
        }
        return owed;
    }

    /** Whether a tier from {@code fromPercent} of the plan is open to a chooser with {@code earned} drafts behind him. */
    static boolean opens(int fromPercent, int earned, int planned) {
        return (long) HUNDRED * earned >= (long) fromPercent * planned;
    }
}
