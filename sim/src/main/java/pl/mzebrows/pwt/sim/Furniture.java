package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.Randomness;

/**
 * What a biome holds besides its creatures (missions ticket 03): control points, barrels, rabbits, boulders, trees and
 * the pickups lying about. Fixed ranges, never scaled by tier or ramp; where each goes is placement's (G0068).
 *
 * @param pointsMin the fewest control points, at least one: the goal line leads east through them
 * @param pointsMax the most
 * @param barrelsMin the fewest explosive barrels, put down by holes and packs
 * @param barrelsMax the most
 * @param rabbitsMin the fewest rabbits, food that runs
 * @param rabbitsMax the most
 * @param bouldersMin the fewest boulders, round rock to fight round
 * @param bouldersMax the most
 * @param treesMin the fewest trees, a trunk to fight round under foliage that hides what is beneath it (G0072)
 * @param treesMax the most
 * @param treesByNestMin the fewest of them stood within {@code treeReach} of each nest, before the rest go anywhere
 * @param treesByNestMax the most
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
        int treesMin,
        int treesMax,
        int treesByNestMin,
        int treesByNestMax,
        List<Stock> pickups) {

    public Furniture {
        if (pointsMin < 1 || pointsMax < pointsMin) {
            throw new IllegalArgumentException(
                    "a biome has from at least 1 control point up, was " + pointsMin + ".." + pointsMax);
        }
        range("barrels", barrelsMin, barrelsMax);
        range("rabbits", rabbitsMin, rabbitsMax);
        range("boulders", bouldersMin, bouldersMax);
        range("trees", treesMin, treesMax);
        range("trees by a nest", treesByNestMin, treesByNestMax);
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

    /** How many trees, drawn. */
    public int trees(Randomness rng) {
        return between(rng, treesMin, treesMax);
    }

    /** How many trees stand by one nest, drawn. */
    public int treesByNest(Randomness rng) {
        return between(rng, treesByNestMin, treesByNestMax);
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
