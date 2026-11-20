package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * One line of a biome's roster (missions ticket 03): how many of one strain it is dealt, and how. Written {@code
 * loose|nest <strain> min max}; the range is the biome at the first position on medium. <strong>Every count a
 * roster deals is drawn and scaled in {@link #count}</strong>, and nothing else scales one (G0063).
 */
public sealed interface Roster {

    /** What it deals. */
    UnitKind strain();

    /** The fewest, before the pressure. */
    int min();

    /** The most, before the pressure. */
    int max();

    /** How many: drawn between the fewest and the most, then scaled by the pressure - a nest's way or a loose one's. */
    int count(Randomness rng, MissionConfig.Pressure pressure);

    /**
     * How many of a range of creatures with no nest the swarm deals: drawn between the fewest and the most, then
     * scaled by {@link MissionConfig.Pressure#scaleLoose}. The one place such a count is drawn - a loose line's, and a
     * finale's wave's (G0064).
     */
    static int count(int min, int max, Randomness rng, MissionConfig.Pressure pressure) {
        return pressure.scaleLoose(drawn(min, max, rng));
    }

    private static int drawn(int min, int max, Randomness rng) {
        return min + rng.nextInt(max - min + 1);
    }

    /** Creatures put down unaware, in packs round anchors (G0068). */
    record Loose(UnitKind strain, int min, int max) implements Roster {
        public Loose {
            range("loose " + strain, min, max);
        }

        @Override
        public int count(Randomness rng, MissionConfig.Pressure pressure) {
            return Roster.count(min, max, rng, pressure);
        }
    }

    /** Holes in the ground that keep sending that strain out once woken (G0062). */
    record Nest(UnitKind strain, int min, int max) implements Roster {
        public Nest {
            range("nest " + strain, min, max);
        }

        @Override
        public int count(Randomness rng, MissionConfig.Pressure pressure) {
            return pressure.scale(drawn(min, max, rng));
        }
    }

    private static void range(String line, int min, int max) {
        if (min < 0 || max < min) {
            throw new IllegalArgumentException(
                    "a roster line runs from at least 0 up, " + line + " was " + min + ".." + max);
        }
    }
}
