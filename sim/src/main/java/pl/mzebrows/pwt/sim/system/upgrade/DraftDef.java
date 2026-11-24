package pl.mzebrows.pwt.sim.system.upgrade;

import java.util.List;
import pl.mzebrows.pwt.sim.def.Require;

/**
 * How drafts are earned and dealt (G0049, G0118): every slot his own, on a schedule fixed when the level is built
 * from its sources - every site, and a crossing for every ridge.
 *
 * @param options how many cards a draft deals, fewer if the pool runs short
 * @param drafts how many drafts a slot is planned on a level of {@code sourcesRef} weighed sources; zero is off
 * @param sourcesRef the weighed sources a level plans exactly {@code drafts} for
 * @param curvePercent how fast the plan grows with the sources, as the power of their share of {@code sourcesRef}:
 *     a hundred is one for one, less grows slower
 * @param siteWeight what a site taken is worth, for every seated slot; zero leaves sites out
 * @param crossingWeight what a crossing is worth, for the slot whose commander made it; zero leaves crossings out
 * @param firstAtOnce whether the first source passed earns at once, the rest one gap apart after it; otherwise the
 *     first waits its gap and the last falls on the last source
 * @param tierFrom the share of a slot's planned drafts, in per cent, he must have earned before each tier is dealt,
 *     tier one first
 */
public record DraftDef(
        int options,
        int drafts,
        int sourcesRef,
        int curvePercent,
        int siteWeight,
        int crossingWeight,
        boolean firstAtOnce,
        List<Integer> tierFrom) {

    private static final int HUNDRED = 100;

    public DraftDef {
        Require.atLeastOne("draft options", options);
        Require.notNegative("draft drafts", drafts);
        Require.atLeastOne("draft sourcesRef", sourcesRef);
        Require.notNegative("draft curvePercent", curvePercent);
        Require.notNegative("draft siteWeight", siteWeight);
        Require.notNegative("draft crossingWeight", crossingWeight);
        if (tierFrom.isEmpty()) {
            throw new IllegalArgumentException("draft tierFrom must name at least tier one");
        }
        for (int i = 0; i < tierFrom.size(); i++) {
            if (tierFrom.get(i) < (i == 0 ? 0 : tierFrom.get(i - 1)) || tierFrom.get(i) > HUNDRED) {
                throw new IllegalArgumentException("draft tierFrom must run up from 0 to at most 100: " + tierFrom);
            }
        }
        tierFrom = List.copyOf(tierFrom);
    }

    /** The same, dealing nothing: the proving ground's (G0060). */
    public DraftDef off() {
        return new DraftDef(options, 0, sourcesRef, curvePercent, siteWeight, crossingWeight, firstAtOnce, tierFrom);
    }

    /**
     * What so many sites and ridges weigh, every site and every crossing at its weight: a level's sources when it
     * is planned, and a slot's count of them passed - the sites taken and his own crossings - as it is played.
     */
    public int sources(int sites, int ridges) {
        return siteWeight * sites + crossingWeight * ridges;
    }

    /** How many drafts each slot is planned on a level of {@code sources} weighed sources. */
    public int planned(int sources) {
        return DraftSchedule.planned(drafts, sources, sourcesRef, curvePercent);
    }

    /** Whether this tier is open to a chooser with {@code earned} of {@code planned} drafts behind him. */
    public boolean opens(int tier, int earned, int planned) {
        return DraftSchedule.opens(tierFrom.get(tier - 1), earned, planned);
    }
}
