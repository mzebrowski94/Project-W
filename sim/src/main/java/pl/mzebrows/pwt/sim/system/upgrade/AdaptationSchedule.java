package pl.mzebrows.pwt.sim.system.upgrade;

import pl.mzebrows.pwt.sim.def.Whole;

/**
 * When the swarm adapts (G0118), on the drafts' schedule: how many a level plans, the most it may hold of the pool,
 * what a source is worth with so many players seated, and how many a count of sources passed is owed. The count is
 * kept in hundredths of a source, so a source made worth more by the players seated is never rounded. Its first
 * waits a gap, where a slot's draft comes at once: the swarm answers a biome, it does not open one. Pure, and
 * allocation-free.
 */
final class AdaptationSchedule {

    private static final int HUNDRED = 100;

    private AdaptationSchedule() {}

    /**
     * How many adaptations a level of {@code sources} weighed sources plans: the drafts' curve on {@code base},
     * moved by {@code offset} - its spread, drawn - and held between none and {@code most}. None with a base of none,
     * whatever the offset.
     */
    static int planned(int base, int sources, int sourcesRef, int curvePercent, int offset, int most) {
        int planned = DraftSchedule.planned(base, sources, sourcesRef, curvePercent);
        if (planned <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(most, planned + offset));
    }

    /** What a source is worth, in per cent, when it is passed with this many players seated: more for each past one. */
    static int worth(int perPlayerPercent, int seated) {
        return HUNDRED + perPlayerPercent * Math.max(0, seated - 1);
    }

    /**
     * The most ranks of a pool of {@code pool} the swarm may hold: {@code mostPercent} of it, risen by {@code
     * worthPercent} - what a source is worth with the players seated - and rounded once, but never past {@code
     * ceilingPercent} of it.
     */
    static int most(int pool, int mostPercent, int ceilingPercent, int worthPercent) {
        return Math.min(Whole.percentOf(pool, ceilingPercent), Whole.percentOf(pool, mostPercent, worthPercent));
    }

    /**
     * How many adaptations a count of {@code passed} hundredths of a source has reached, on a plan of {@code planned}
     * over {@code sources}: one gap of {@code sources / planned} each, the first after its gap, and the gap going on
     * past the plan - up to {@code most}.
     */
    static int owed(int passed, int sources, int planned, int most) {
        if (planned <= 0) {
            return 0;
        }
        int owed = 0;
        while (owed < most && (long) HUNDRED * DraftSchedule.threshold(owed + 1, sources, planned, false) <= passed) {
            owed++;
        }
        return owed;
    }
}
