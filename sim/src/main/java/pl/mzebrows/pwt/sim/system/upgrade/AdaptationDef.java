package pl.mzebrows.pwt.sim.system.upgrade;

import lombok.With;
import pl.mzebrows.pwt.sim.def.Require;

/**
 * When the swarm adapts (ADR 0017, G0118): on the drafts' schedule, at random from the aliens' pool, with nobody
 * choosing. Its sources are the drafts' - every site and every ridge, at their weights - and its count of them passed is
 * the side's: the sites taken and the furthest biome any commander has entered.
 *
 * @param adaptations how many a level of the drafts' {@code sourcesRef} weighed sources plans; zero is off
 * @param curvePercent how fast the plan grows with the sources, as the drafts' {@code curvePercent} does
 * @param spread how far the plan may be moved either way, drawn once from the swarm's stream when it is planned
 * @param mostPercent the most of the pool's ranks a level plans, in per cent, so no run ends holding all of it
 * @param perPlayerPercent how much more a source is worth when it is passed, in per cent, for every player seated past
 *     the first - and how much the most the swarm may hold rises by
 * @param ceilingPercent the share of the pool's ranks the swarm never holds more of, however many are seated
 * @param answerWeight how much likelier an adaptation that answers an upgrade the players hold is dealt: its weight
 *     times one plus this, per rank they hold; zero is off - the reactive hook, not yet used
 */
@With
public record AdaptationDef(
        int adaptations,
        int curvePercent,
        int spread,
        int mostPercent,
        int perPlayerPercent,
        int ceilingPercent,
        int answerWeight) {

    public AdaptationDef {
        Require.notNegative("adaptation adaptations", adaptations);
        Require.notNegative("adaptation curvePercent", curvePercent);
        Require.notNegative("adaptation spread", spread);
        Require.notNegative("adaptation mostPercent", mostPercent);
        Require.notNegative("adaptation perPlayerPercent", perPlayerPercent);
        Require.percent("adaptation ceilingPercent", ceilingPercent);
        Require.atLeast(
                "adaptation ceilingPercent",
                ceilingPercent,
                mostPercent,
                "the ceiling is where a share risen by the players stops");
        Require.notNegative("adaptation answerWeight", answerWeight);
    }

    /** How many adaptations a level of {@code sources} weighed sources plans, its spread {@code offset} drawn. */
    public int planned(int sources, int sourcesRef, int pool, int offset) {
        return AdaptationSchedule.planned(adaptations, sources, sourcesRef, curvePercent, offset, most(pool, 1));
    }

    /** What a source is worth, in per cent, passed with this many players seated. */
    public int worth(int seated) {
        return AdaptationSchedule.worth(perPlayerPercent, seated);
    }

    /** The most ranks of a pool of {@code pool} the swarm may hold with this many players seated. */
    public int most(int pool, int seated) {
        return AdaptationSchedule.most(pool, mostPercent, ceilingPercent, worth(seated));
    }
}
