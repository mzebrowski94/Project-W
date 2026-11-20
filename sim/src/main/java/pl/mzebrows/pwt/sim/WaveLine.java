package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * One wave of a finale's schedule (G0064), written {@code afterSeconds strain countMin countMax mouths}: when it comes,
 * what, how many and from where. The count is drawn and scaled like a roster's, through {@link Roster#count} with the
 * tier's pressure and no ramp; the timing, the strain and the mouths are never tiered.
 *
 * <p><strong>From one mouth or several at once</strong> (G0087): {@code nw+se} sends from both on the same tick, each
 * mouth its own count drawn from the range, so a range is what one mouth sends.
 *
 * @param afterSeconds how long after the wave before it - or after the hold is taken, for the first
 * @param strain what comes
 * @param countMin the fewest from each mouth, before the pressure
 * @param countMax the most from each mouth, before the pressure
 * @param mouths the name of the mouth it comes from, or several joined by {@code +}, each one its finale has
 */
public record WaveLine(float afterSeconds, UnitKind strain, int countMin, int countMax, String mouths) {

    /** What joins two mouths a wave comes from at once. */
    public static final String AND = "+";

    public WaveLine {
        if (afterSeconds < 0f || countMin < 0 || countMax < countMin) {
            throw new IllegalArgumentException("a wave comes at least 0 s after the last, at least 0 of them up, was "
                    + afterSeconds + " s and " + countMin + ".." + countMax + " " + strain);
        }
        Set<String> named = new TreeSet<>();
        for (String mouth : mouths.split("\\" + AND, -1)) {
            if (mouth.isBlank() || !named.add(mouth)) {
                throw new IllegalArgumentException(
                        "a wave names each of its mouths once, and none blank, was '" + mouths + "'");
            }
        }
    }

    /** The mouths it comes from, in the order written. */
    public List<String> mouthNames() {
        return List.of(mouths.split("\\" + AND, -1));
    }

    /** How many come from one mouth: drawn between the fewest and the most, then scaled as a loose line is. */
    public int count(Randomness rng, MissionConfig.Pressure pressure) {
        return Roster.count(countMin, countMax, rng, pressure);
    }
}
