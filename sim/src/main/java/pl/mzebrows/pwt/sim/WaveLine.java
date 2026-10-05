package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * One wave of a finale's schedule (G0064), written {@code afterSeconds strain countMin countMax mouth}: when it comes,
 * what, how many and from where. The count is drawn and scaled like a roster's, through {@link Roster#count} with the
 * tier's pressure and no ramp; the timing, the strain and the mouth are never tiered.
 *
 * @param afterSeconds how long after the wave before it - or after the hold is taken, for the first
 * @param strain what comes
 * @param countMin the fewest, before the pressure
 * @param countMax the most, before the pressure
 * @param mouth the name of the mouth it comes from, one its finale has
 */
public record WaveLine(float afterSeconds, UnitKind strain, int countMin, int countMax, String mouth) {

    public WaveLine {
        if (afterSeconds < 0f || countMin < 0 || countMax < countMin) {
            throw new IllegalArgumentException("a wave comes at least 0 s after the last, at least 0 of them up, was "
                    + afterSeconds + " s and " + countMin + ".." + countMax + " " + strain);
        }
    }

    /** How many come: drawn between the fewest and the most, then scaled by the pressure, rounded once. */
    public int count(Randomness rng, MissionConfig.Pressure pressure) {
        return Roster.count(countMin, countMax, rng, pressure);
    }
}
