package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.Randomness;

/**
 * What a biome holds besides its creatures (missions ticket 03): control points, barrels, rabbits, boulders and the
 * pickups lying about. Fixed ranges, never scaled by tier or ramp; where each goes is placement's (G0068).
 *
 * @param pointsMin the fewest control points, at least one: the goal line leads east through them
 * @param pointsMax the most
 * @param barrelsMin the fewest explosive barrels, put down by holes and packs
 * @param barrelsMax the most
 * @param rabbitsMin the fewest rabbits, food that runs
 * @param rabbitsMax the most
 * @param bouldersMin the fewest boulders, round rock to fight round
 * @param bouldersMax the most
 * @param pickups what lies about, a line per kind; a house is stocked with its crates and medkits first
 */
public record Furniture(
        int pointsMin,
        int pointsMax,
        int barrelsMin,
        int barrelsMax,
        int rabbitsMin,
        int rabbitsMax,
        int bouldersMin,
        int bouldersMax,
        List<Stock> pickups) {

    public Furniture {
        if (pointsMin < 1 || pointsMax < pointsMin) {
            throw new IllegalArgumentException(
                    "a biome has from at least 1 control point up, was " + pointsMin + ".." + pointsMax);
        }
        range("barrels", barrelsMin, barrelsMax);
        range("rabbits", rabbitsMin, rabbitsMax);
        range("boulders", bouldersMin, bouldersMax);
        pickups = List.copyOf(pickups);
    }

    /** How many control points, drawn. */
    public int points(Randomness rng) {
        return between(rng, pointsMin, pointsMax);
    }

    /** How many barrels, drawn. */
    public int barrels(Randomness rng) {
        return between(rng, barrelsMin, barrelsMax);
    }

    /** How many rabbits, drawn. */
    public int rabbits(Randomness rng) {
        return between(rng, rabbitsMin, rabbitsMax);
    }

    /** How many boulders, drawn. */
    public int boulders(Randomness rng) {
        return between(rng, bouldersMin, bouldersMax);
    }

    private static int between(Randomness rng, int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }

    private static void range(String what, int min, int max) {
        if (min < 0 || max < min) {
            throw new IllegalArgumentException(
                    "furniture " + what + " run from at least 0 up, were " + min + ".." + max);
        }
    }
}
