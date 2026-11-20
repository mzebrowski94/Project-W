package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.sim.def.Require;

/**
 * How strong a host has made a mission's swarm, beside its tier (G0106): two whole percentages that meet the tier's
 * and the ramp's once, in {@code Whole}. Not part of the mission's code - the code names the map; this is what the
 * session was started with.
 *
 * @param healthPercent every strain's and nest's health, on top of the tier's; a hundred is as the tier has it
 * @param countPercent every creature count a mission deals, finale waves included, on top of the tier's and the ramp's
 */
public record Strength(int healthPercent, int countPercent) {

    /** The swarm as its tier has it. */
    public static final Strength NORMAL = new Strength(100, 100);

    public Strength {
        Require.atLeastOne("strength healthPercent", healthPercent);
        Require.atLeastOne("strength countPercent", countPercent);
    }

    /** Whether this is {@link #NORMAL}: what a daily's record is kept for. */
    public boolean normal() {
        return equals(NORMAL);
    }
}
