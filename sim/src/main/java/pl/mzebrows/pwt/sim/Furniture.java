package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.Randomness;

/**
 * What a biome holds besides its creatures (missions ticket 03): sites, barrels, rabbits, boulders, trees,
 * turrets, geysers and the pickups lying about. Fixed ranges, never scaled by tier or ramp; where each goes is placement's (G0068).
 *
 * @param sitesMin the fewest sites (G0110) - control points, or another kind of site in a point's place - at least one:
 *     the goal line leads east through them
 * @param sitesMax the most
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
 * @param stonesMin the fewest stones, rock from a man's size to five times it (the desert's): zero in every other biome
 * @param stonesMax the most
 * @param turretsMin the fewest turrets, each by a point (G0082): zero in every biome but the plain
 * @param turretsMax the most
 * @param geysersMin the fewest geysers, ground that erupts in fire (G0089): zero in every biome but the cut
 * @param geysersMax the most
 * @param spires how many spires, the fen's emplacements (G0113): {@code 0 0} in every biome but the fen
 * @param relics how many of its sites are relics (G0114), each in a point's place: {@code 0 0} in every biome but the
 *     desert; never more than it has sites
 * @param sitesInClearings whether a control point stands at the centre of each of its ground's clearings, first of its
 *     sites, each in the band its column falls in (G0115); false in every biome but the rift
 * @param clearingPackShare the per cent of its loose packs put down in its clearings, before any elsewhere (G0115);
 *     zero in every biome but the rift
 * @param pickups what lies about, a line per kind; a house is stocked with its crates and medkits first, and a crate is
 *     laid nowhere else (G0110)
 */
public record Furniture(
        int sitesMin,
        int sitesMax,
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
        int stonesMin,
        int stonesMax,
        int turretsMin,
        int turretsMax,
        int geysersMin,
        int geysersMax,
        Count spires,
        Count relics,
        boolean sitesInClearings,
        int clearingPackShare,
        List<Stock> pickups) {

    /** A fewest and a most, on one line: {@code spires=3 5}, {@code relics=1 2}. */
    public record Count(int min, int max) {

        public Count {
            range("count", min, max);
        }

        /** How many, drawn. */
        public int draw(Randomness rng) {
            return between(rng, min, max);
        }
    }

    public Furniture {
        if (sitesMin < 1 || sitesMax < sitesMin) {
            throw new IllegalArgumentException(
                    "a biome has from at least 1 site up, was " + sitesMin + ".." + sitesMax);
        }
        range("barrels", barrelsMin, barrelsMax);
        range("rabbits", rabbitsMin, rabbitsMax);
        range("boulders", bouldersMin, bouldersMax);
        range("trees", treesMin, treesMax);
        range("trees by a nest", treesByNestMin, treesByNestMax);
        range("stones", stonesMin, stonesMax);
        range("turrets", turretsMin, turretsMax);
        range("geysers", geysersMin, geysersMax);
        if (relics.max() > sitesMin) {
            throw new IllegalArgumentException(
                    "a biome's relics are among its sites, so at most " + sitesMin + ", were up to " + relics.max());
        }
        if (clearingPackShare < 0 || clearingPackShare > 100) {
            throw new IllegalArgumentException("furniture clearingPackShare is a per cent, was " + clearingPackShare);
        }
        pickups = List.copyOf(pickups);
    }

    /** How many sites, drawn. */
    public int sites(Randomness rng) {
        return between(rng, sitesMin, sitesMax);
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

    /** How many stones, drawn. */
    public int stones(Randomness rng) {
        return between(rng, stonesMin, stonesMax);
    }

    /** How many turrets, drawn. */
    public int turrets(Randomness rng) {
        return between(rng, turretsMin, turretsMax);
    }

    /** How many geysers, drawn. */
    public int geysers(Randomness rng) {
        return between(rng, geysersMin, geysersMax);
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
