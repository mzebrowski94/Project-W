package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.sim.def.Require;

/**
 * One tier of a mission (G0063): four numbers on the swarm, and nothing about the player's side. Read by
 * {@code MissionConfig} alone, which folds it into the config a mission's world is built from.
 *
 * @param enemyPercent every creature count a biome's roster deals, loose and nest, as a whole percentage
 * @param healthPercent every strain's and nest's health, on top of {@code sim.swarm.healthPercent}, as a whole
 *     percentage
 * @param nestPeriodPercent the time between a nest's broods, as a whole percentage of {@code
 *     sim.nest.periodSeconds}
 * @param adaptations how many adaptations a level of the drafts' {@code sourcesRef} weighed sources plans the swarm
 *     (G0118), in place of the level's; zero is off
 */
public record TierDef(int enemyPercent, int healthPercent, int nestPeriodPercent, int adaptations) {

    public TierDef {
        Require.atLeastOne("tier enemyPercent", enemyPercent);
        Require.atLeastOne("tier healthPercent", healthPercent);
        Require.atLeastOne("tier nestPeriodPercent", nestPeriodPercent);
        Require.notNegative("tier adaptations", adaptations);
    }
}
