package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How hot a gun gets, as numbers. Every one of them zero means a gun that never runs hot, which is
 * most of them - the same shape {@code AiChance} uses for a behaviour that is switched off.
 *
 * <p>Heat is the one thing that fills up because of something the player <em>did</em> rather than
 * something being done to him, and the hysteresis is what makes it a decision: a gun that came back
 * the moment it stopped being full would be a gun you tap rather than one you have to stop using.
 *
 * @param perShot how much of the bar one shot adds, as a fraction of the whole
 * @param coolPerSecond how much of it comes off per second, always - a gun nobody is holding cools
 *     exactly as fast as one somebody is
 * @param resumeBelow how far it must fall before a gun that stopped itself will fire again, as a
 *     fraction. Well below one, or the stop is a stutter rather than a cost
 */
@With
public record HeatDef(float perShot, float coolPerSecond, float resumeBelow) {

    public HeatDef {
        Require.notNegative("heat perShot", perShot);
        Require.notNegative("heat coolPerSecond", coolPerSecond);
        if (resumeBelow < 0f || resumeBelow > 1f) {
            throw new IllegalArgumentException("heat resumeBelow must be in [0, 1], was " + resumeBelow);
        }
    }

    /** A gun that never runs hot. What every gun a man carries declares, bar one. */
    public static HeatDef none() {
        return new HeatDef(0f, 0f, 0f);
    }

    /** True if firing this gun costs anything at all. */
    public boolean matters() {
        return perShot > 0f;
    }
}
