package pl.mzebrows.pwt.sim.system.upgrade;

import java.util.List;
import pl.mzebrows.pwt.sim.def.Require;

/**
 * How drafts are earned and dealt (G0049).
 *
 * @param options how many cards a draft deals, fewer if the pool runs short
 * @param pointsPerDraft control points taken per draft: the first earns at once, then every n-th; zero
 *     is off
 * @param wavesPerDraft hold waves cleared per draft, the same way
 * @param bossesPerDraft bosses killed per draft, the same way
 * @param tierFrom how many drafts must have been earned before each tier is dealt, tier one first
 */
public record DraftDef(int options, int pointsPerDraft, int wavesPerDraft, int bossesPerDraft, List<Integer> tierFrom) {

    public DraftDef {
        Require.atLeastOne("draft options", options);
        Require.notNegative("draft pointsPerDraft", pointsPerDraft);
        Require.notNegative("draft wavesPerDraft", wavesPerDraft);
        Require.notNegative("draft bossesPerDraft", bossesPerDraft);
        if (tierFrom.isEmpty()) {
            throw new IllegalArgumentException("draft tierFrom must name at least tier one");
        }
        for (int i = 0; i < tierFrom.size(); i++) {
            if (tierFrom.get(i) < (i == 0 ? 0 : tierFrom.get(i - 1))) {
                throw new IllegalArgumentException("draft tierFrom must not come down: " + tierFrom);
            }
        }
        tierFrom = List.copyOf(tierFrom);
    }

    /** How many drafts must have been earned before this tier is dealt. */
    public int unlockedAt(int tier) {
        return tierFrom.get(tier - 1);
    }
}
