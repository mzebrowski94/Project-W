package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;

/**
 * What a mission is built with that no other level has: its tiers and its ramp (G0063). <strong>The one
 * reader of a tier</strong> - {@link #apply} folds one into the config a mission's world is built from, and
 * {@link #pressure} scales the counts its biomes are dealt - so no system learns what a tier is.
 *
 * @param rampPercent how much a biome's creature counts rise for each position it stands at in the march, as
 *     a whole percentage: the first biome is at none, the fourth at three times this. Zero is no ramp. The
 *     finale is not ramped
 * @param tiers every tier, by kind
 */
public record MissionConfig(
        int rampPercent, @ConfigKey("tier") Defs<TierKind, TierDef> tiers) {

    private static final int WHOLE = 100;

    public MissionConfig {
        Require.notNegative("mission rampPercent", rampPercent);
    }

    /**
     * How hard the swarm presses on this tier in the biome at this position of the march - the first is zero.
     * Every count a biome's roster deals goes through it, and nothing else scales one.
     */
    public Pressure pressure(TierKind tier, int position) {
        Require.notNegative("mission position", position);
        return new Pressure(tiers.of(tier).enemyPercent(), WHOLE + rampPercent * position);
    }

    /**
     * {@code config} as a mission on this tier plays it: the tier's health on top of the swarm's, its nest
     * period and its adaptation pace. Nothing else changes - the squad, the reserve and every blow are the same
     * on every tier. Applied once, to a config no tier has been applied to.
     */
    public SimConfig apply(TierKind tier, SimConfig config) {
        TierDef def = tiers.of(tier);
        return config.withSwarm(config.swarm().withTierPercent(def.healthPercent()))
                .withNest(config.nest()
                        .withPeriodSeconds(config.nest().periodSeconds() * def.nestPeriodPercent() / WHOLE))
                .withAdaptation(new AdaptationDef(
                        def.adaptationEverySeconds(),
                        def.adaptationPerPlayerUpgrades(),
                        config.adaptation().answerWeight()));
    }

    /**
     * A tier's count and a position's ramp, both as whole percentages, meeting a count once in {@link Whole}
     * (G0054): never rounded in turn, and a count above zero never scaled to none.
     */
    public record Pressure(int enemyPercent, int rampedPercent) {

        /** This many creatures, as the pressure deals them. */
        public int scale(int count) {
            return Whole.percentOf(count, enemyPercent, rampedPercent);
        }
    }
}
