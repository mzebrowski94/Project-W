package pl.mzebrows.pwt.sim;

import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * One line of what a biome has lying about (missions ticket 03), written {@code pickup <kind> min max}. Never scaled
 * by tier or ramp. Sealed, with one record, so stock of another sort is a record rather than a flag.
 */
public sealed interface Stock {

    /** How many: drawn between the fewest and the most. */
    int count(Randomness rng);

    /** Pickups of one kind - crates, medkits, mushrooms, flowers. Never a gun's cache, which is a finale's. */
    record Pickup(PickupKind kind, int min, int max) implements Stock {
        public Pickup {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException(
                        "a stock line runs from at least 0 up, pickup " + kind + " was " + min + ".." + max);
            }
        }

        @Override
        public int count(Randomness rng) {
            return min + rng.nextInt(max - min + 1);
        }
    }
}
