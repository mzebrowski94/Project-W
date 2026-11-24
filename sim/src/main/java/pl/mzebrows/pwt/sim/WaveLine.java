package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WaveRole;

/**
 * One wave of a finale's schedule (G0064), written {@code afterSeconds role mouths}: when it comes, what part it plays
 * and from where. Who comes is a {@link Member} of the role, dealt from the finale's stream (G0120); how many is the
 * member's, drawn and scaled like a roster's, through {@link Roster#count} with the tier's pressure and no ramp. The
 * timing, the role and the mouths are never tiered.
 *
 * <p><strong>From one mouth or several at once</strong> (G0087): {@code nw+se} sends from both on the same tick, each
 * mouth its own count drawn from the member's range, so a range is what one mouth sends.
 *
 * @param afterSeconds how long after the wave before it - or after the hold is taken, for the first
 * @param role what part it plays: which of the finale's roles its strain is dealt from
 * @param mouths the name of the mouth it comes from, or several joined by {@code +}, each one its finale has
 */
public record WaveLine(float afterSeconds, WaveRole role, String mouths) {

    /** What joins two mouths a wave comes from at once. */
    public static final String AND = "+";

    public WaveLine {
        if (afterSeconds < 0f) {
            throw new IllegalArgumentException("a wave comes at least 0 s after the last, was " + afterSeconds + " s");
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

    /**
     * One strain a role may be dealt, written {@code strain countMin countMax}: what comes, and how many from each mouth
     * before the pressure.
     *
     * @param strain what comes
     * @param countMin the fewest from each mouth, before the pressure
     * @param countMax the most from each mouth, before the pressure
     */
    public record Member(UnitKind strain, int countMin, int countMax) {

        public Member {
            if (countMin < 0 || countMax < countMin) {
                throw new IllegalArgumentException(
                        "a role's member comes at least 0 up, was " + countMin + ".." + countMax + " " + strain);
            }
        }

        /** How many come from one mouth: drawn between the fewest and the most, then scaled as a loose line is. */
        public int count(Randomness rng, MissionConfig.Pressure pressure) {
            return Roster.count(countMin, countMax, rng, pressure);
        }
    }
}
