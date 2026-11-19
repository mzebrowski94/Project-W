package pl.mzebrows.pwt.sim;

import java.util.ArrayList;
import java.util.List;
import lombok.With;
import pl.mzebrows.pwt.map.StripRules;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Require;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;

/**
 * What a mission is built with that no other level has: its tiers and its ramp (G0063), how many biomes it deals,
 * the strip they are laid in and where what they count is put down (G0068). <strong>The one reader of a
 * tier</strong> - {@link #apply} folds one into the config a mission's world is built from, and {@link #pressure}
 * scales the counts its biomes are dealt - so no system learns what a tier is.
 *
 * @param rampTopPercent what a biome's creature counts are scaled to at the last biome before the finale, as a
 *     whole percentage: the first biome stands at 100 and those between are evenly spaced, whatever the march's
 *     length (G0102). 100 is no ramp. The finale is not ramped
 * @param tiers every tier, by kind
 * @param biomes how many biomes a mission deals before its finale, the same on every tier: the default length - a
 *     daily's - and, in a mission's config, the length it was asked for (G0102)
 * @param biomesMin the shortest length a mission may be chosen at
 * @param biomesMax the longest; the pool may be smaller, and a biome comes again (G0102)
 * @param ground the strip every mission is laid in: its height, ridges, passes and houses (missions tickets 03, 12,
 *     13)
 * @param placement where what a biome counts is put down (G0068)
 * @param approachColumns how many columns at the east end of the last biome hold its last control point, the one
 *     before the finale (G0064)
 */
@With
public record MissionConfig(
        int rampTopPercent,
        @ConfigKey("tier") Defs<TierKind, TierDef> tiers,
        int biomes,
        int biomesMin,
        int biomesMax,
        StripRules ground,
        PlacementRules placement,
        @ConfigKey("finale.approachColumns") int approachColumns) {

    private static final int WHOLE = 100;

    public MissionConfig {
        Require.atLeast("mission rampTopPercent", rampTopPercent, WHOLE, "the ramp never eases below its first biome");
        Require.atLeastOne("mission biomesMin", biomesMin);
        Require.atLeast("mission biomesMax", biomesMax, biomesMin, "a length range holds at least one length");
        if (biomes < biomesMin || biomes > biomesMax) {
            throw new IllegalArgumentException(
                    "mission biomes is " + biomes + ", outside the lengths " + biomesMin + " to " + biomesMax);
        }
        Require.atLeastOne("mission finale approachColumns", approachColumns);
    }

    /** Whether a mission may be this many biomes long: the one reading of {@code biomesMin} and {@code biomesMax}. */
    public boolean hasLength(int length) {
        return length >= biomesMin && length <= biomesMax;
    }

    /**
     * How hard the swarm presses on this tier in the biome at this position of the march - the first is zero. The
     * ramp spans the march: x1.0 at the first biome and {@code rampTopPercent} at the last, whatever {@code
     * biomes} is (G0102). Every count a biome's roster deals goes through it, and nothing else scales one.
     */
    public Pressure pressure(TierKind tier, int position) {
        Require.notNegative("mission position", position);
        int last = biomes - 1;
        int ramped = last == 0 ? WHOLE : WHOLE + (rampTopPercent - WHOLE) * Math.min(position, last) / last;
        return new Pressure(tiers.of(tier).enemyPercent(), ramped);
    }

    /**
     * How hard the swarm presses on this tier in the finale's waves: the tier's count and no ramp (G0064). A finale
     * is the same fight however long the march to it was.
     */
    public Pressure finale(TierKind tier) {
        return new Pressure(tiers.of(tier).enemyPercent(), WHOLE);
    }

    /**
     * How every tier presses on the biome at this position: what a biome's placement must find room for, so one seed
     * stands its points in the same places on every tier (G0068).
     */
    public List<Pressure> pressures(int position) {
        List<Pressure> every = new ArrayList<>();
        for (TierKind tier : TierKind.values()) {
            every.add(pressure(tier, position));
        }
        return every;
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
