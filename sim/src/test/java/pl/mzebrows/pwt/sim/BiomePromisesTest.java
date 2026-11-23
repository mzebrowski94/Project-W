package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Outcrops;
import pl.mzebrows.pwt.map.Patch;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What the design says of the biomes in a number (missions ticket 03), held against the shipped blocks: each a
 * place, told apart by its ground. Connectivity and placement are the sweeps'; a biome's numbers are its block's.
 */
class BiomePromisesTest {

    private static final SimConfig SHIPPED = SimConfig.defaults();

    /** "Open, pebbles of rock" - the least of any biome with rock at all; the fen has none (its trees stand for it). */
    @Test
    void thePlainIsTheLeastRockyBiome() {
        for (BiomeKind kind : BiomeKind.values()) {
            if (kind != BiomeKind.PLAIN && rock(kind) > 0f) {
                assertThat(rock(BiomeKind.PLAIN))
                        .as("the plain against %s", kind)
                        .isLessThan(rock(kind));
            }
        }
    }

    /** "Turrets should be added to the plain" (G0082): the open ground a long gun earns its keep on, and no other. */
    @Test
    void thePlainIsTheOneBiomeThatLaysTurrets() {
        for (BiomeKind kind : BiomeKind.values()) {
            var furniture = SHIPPED.biomes().of(kind).furniture();
            if (kind == BiomeKind.PLAIN) {
                assertThat(furniture.turretsMin())
                        .as("the plain's fewest turrets")
                        .isPositive();
            } else {
                assertThat(furniture.turretsMax()).as("%s's turrets", kind).isZero();
            }
        }
    }

    /**
     * "Add lava geysers to the cut" (ticket 15, G0089): 3-5 there; "more geysers" in the caldera (ticket 16, G0091,
     * provisional, for review): 5-7, its fewest the cut's most; and none anywhere else - zero is off.
     */
    @Test
    void theCutAndTheCalderaAreTheBiomesThatLayGeysersTheCalderaMore() {
        for (BiomeKind kind : BiomeKind.values()) {
            var furniture = SHIPPED.biomes().of(kind).furniture();
            if (kind == BiomeKind.CUT) {
                assertThat(furniture.geysersMin())
                        .as("the cut's fewest geysers")
                        .isEqualTo(3);
                assertThat(furniture.geysersMax()).as("the cut's most geysers").isEqualTo(5);
            } else if (kind == BiomeKind.CALDERA) {
                assertThat(furniture.geysersMin())
                        .as("the caldera's fewest geysers")
                        .isEqualTo(5);
                assertThat(furniture.geysersMax())
                        .as("the caldera's most geysers")
                        .isEqualTo(7);
            } else {
                assertThat(furniture.geysersMax()).as("%s's geysers", kind).isZero();
            }
        }
    }

    /**
     * "A lava biome" (ticket 16, G0091, provisional, for review): the caldera is the one biome on lava - its pools laid
     * as crags of it, on ash - with no house, no tree, its barrels kept and fewer boulders than any biome that has them.
     */
    @Test
    void theCalderaIsTheOneBiomeOfLavaPoolsOnAsh() {
        for (BiomeKind kind : BiomeKind.values()) {
            assertThat(ground(kind).crags().terrain() == Terrain.LAVA)
                    .as("%s's lava", kind)
                    .isEqualTo(kind == BiomeKind.CALDERA);
        }
        BiomeGround caldera = ground(BiomeKind.CALDERA);
        var furniture = SHIPPED.biomes().of(BiomeKind.CALDERA).furniture();
        assertThat(caldera.base()).isEqualTo(Terrain.ASH);
        assertThat(caldera.crags().density()).isPositive();
        assertThat(caldera.houses().max()).isZero();
        assertThat(furniture.treesMax()).isZero();
        assertThat(furniture.barrelsMin()).isPositive();
        for (BiomeKind kind : BiomeKind.values()) {
            int boulders = SHIPPED.biomes().of(kind).furniture().bouldersMax();
            if (kind != BiomeKind.CALDERA && boulders > 0) {
                // No more than any biome that has them: the rift's maze, as short of room, lays as few (G0115).
                assertThat(furniture.bouldersMax())
                        .as("the caldera against %s", kind)
                        .isLessThanOrEqualTo(boulders);
            }
        }
    }

    /**
     * "A cold biome" (ticket 10, G0115, provisional, for review): the rift is the one biome of fissures - corridors carved
     * out of them, on frost - and the one with clearings, two of radius 5 (amended 2026-11-23), its sites in them and
     * 60 % of its packs; no house, no tree, no geyser.
     */
    @Test
    void theRiftIsTheOneBiomeOfFissuresOnFrostWithItsSitesInTwoClearings() {
        for (BiomeKind kind : BiomeKind.values()) {
            boolean rift = kind == BiomeKind.RIFT;
            var furniture = SHIPPED.biomes().of(kind).furniture();
            assertThat(ground(kind).corridors().on() && ground(kind).corridors().terrain() == Terrain.FISSURE)
                    .as("%s's fissures", kind)
                    .isEqualTo(rift);
            assertThat(ground(kind).crags().terrain() == Terrain.FISSURE)
                    .as("%s's fissure crags", kind)
                    .isFalse();
            assertThat(ground(kind).clearings().count() > 0)
                    .as("%s's clearings", kind)
                    .isEqualTo(rift);
            assertThat(furniture.sitesInClearings())
                    .as("%s's sites in clearings", kind)
                    .isEqualTo(rift);
            assertThat(furniture.clearingPackShare() > 0)
                    .as("%s's packs in clearings", kind)
                    .isEqualTo(rift);
        }
        BiomeGround rift = ground(BiomeKind.RIFT);
        var furniture = SHIPPED.biomes().of(BiomeKind.RIFT).furniture();
        assertThat(rift.base()).isEqualTo(Terrain.FROST);
        assertThat(rift.clearings().count()).isEqualTo(2);
        assertThat(rift.clearings().radius()).isEqualTo(5);
        assertThat(rift.houses().max()).isZero();
        assertThat(furniture.clearingPackShare()).isEqualTo(60);
        assertThat(furniture.treesMax()).isZero();
        assertThat(furniture.geysersMax()).isZero();
    }

    /** "Upgraded spitters in it" (ticket 16): the magma spitter stands in the caldera and nowhere else. */
    @Test
    void theCalderaIsTheOneBiomeWithMagmaSpitters() {
        for (BiomeKind kind : BiomeKind.values()) {
            boolean magma = SHIPPED.biomes().of(kind).roster().stream()
                    .anyMatch(line -> line.strain() == UnitKind.MAGMA_SPITTER);
            assertThat(magma).as("magma spitters in %s", kind).isEqualTo(kind == BiomeKind.CALDERA);
        }
    }

    /**
     * "Crags at both ends of their range": mounds round the anthill's holes, masses in the badlands - crags of rock; the
     * caldera's are of lava (G0091).
     */
    @Test
    void cragsRiseFromMoundsInTheAnthillToMassesInTheBadlands() {
        for (BiomeKind kind : BiomeKind.values()) {
            boolean crags = rockCrags(kind) > 0f;
            assertThat(crags).as("%s's crags", kind).isEqualTo(kind == BiomeKind.ANTHILL || kind == BiomeKind.BADLANDS);
        }
        assertThat(ground(BiomeKind.ANTHILL).crags().density())
                .isLessThan(ground(BiomeKind.BADLANDS).crags().density());
        for (BiomeKind kind : BiomeKind.values()) {
            assertThat(rock(kind)).as("%s against the badlands", kind).isLessThanOrEqualTo(rock(BiomeKind.BADLANDS));
        }
    }

    /** "Undergrowth over a fifth of it": the brake is the most overgrown, and only it and the fen's reeds grow at all. */
    @Test
    void theBrakeIsTheMostOvergrownWithThicket() {
        for (BiomeKind kind : BiomeKind.values()) {
            if (kind == BiomeKind.BRAKE) {
                assertThat(thicket(kind)).as("the brake's thicket").isGreaterThanOrEqualTo(0.18f);
            } else if (kind == BiomeKind.FEN) {
                assertThat(thicket(kind)).as("the fen's reeds").isPositive().isLessThan(thicket(BiomeKind.BRAKE));
            } else {
                assertThat(thicket(kind)).as("%s's thicket", kind).isZero();
            }
        }
    }

    /** "No square anywhere" in the fen: every patch a blob, no rock and no house. */
    @Test
    void laysNoSquareInTheFen() {
        assertThat(ground(BiomeKind.FEN).patches()).allMatch(patch -> patch instanceof Patch.Blob);
        assertThat(rock(BiomeKind.FEN)).isZero();
        assertThat(ground(BiomeKind.FEN).houses().max()).isZero();
    }

    /** "Islands of reeds": fewer, bigger clumps than the brake's undergrowth, so ground to go round, not to wade. */
    @Test
    void theFensReedsGrowInBiggerClumpsThanTheBrakesUndergrowth() {
        assertThat(thicketBlob(BiomeKind.FEN).sizeMin())
                .isGreaterThan(thicketBlob(BiomeKind.BRAKE).sizeMin());
        assertThat(thicketBlob(BiomeKind.FEN).sizeMax())
                .isGreaterThan(thicketBlob(BiomeKind.BRAKE).sizeMax());
    }

    /** "The only biome with frogs in it." */
    @Test
    void theFenIsTheOneBiomeWithFrogs() {
        for (BiomeKind kind : BiomeKind.values()) {
            boolean frogs =
                    SHIPPED.biomes().of(kind).roster().stream().anyMatch(line -> line.strain() == UnitKind.FROG);
            assertThat(frogs).as("frogs in %s", kind).isEqualTo(kind == BiomeKind.FEN);
        }
    }

    /**
     * "Slabs with no corner bent off them": straight bars, longer at their shortest than anybody else's rock, and the
     * one rock with a broken outline.
     */
    @Test
    void theCutsRockIsStraightSlabsLongerThanAnyOthers() {
        Outcrops cut = ground(BiomeKind.CUT).outcrops();
        assertThat(cut.arms()).isZero();
        assertThat(cut.ragged()).isPositive();
        for (BiomeKind kind : BiomeKind.values()) {
            if (kind != BiomeKind.CUT && ground(kind).outcrops().density() > 0f) {
                assertThat(cut.lengthMin())
                        .as("the cut against %s", kind)
                        .isGreaterThan(ground(kind).outcrops().lengthMin());
            }
        }
    }

    /** "A quarry with a base in it" (ticket 12): the one biome certain of a house. */
    @Test
    void theQuarryIsTheOneBiomeCertainOfAHouse() {
        for (BiomeKind kind : BiomeKind.values()) {
            assertThat(ground(kind).houses().min() > 0).as("%s's houses", kind).isEqualTo(kind == BiomeKind.QUARRY);
        }
    }

    private static BiomeGround ground(BiomeKind kind) {
        return SHIPPED.biomes().of(kind).ground();
    }

    private static float thicket(BiomeKind kind) {
        float thicket = 0f;
        for (Patch patch : ground(kind).patches()) {
            if (patch.ground().terrain() == Terrain.THICKET) {
                thicket += patch.density();
            }
        }
        return thicket;
    }

    private static Patch thicketBlob(BiomeKind kind) {
        return ground(kind).patches().stream()
                .filter(patch -> patch instanceof Patch.Blob && patch.ground().terrain() == Terrain.THICKET)
                .findFirst()
                .orElseThrow();
    }

    private static float rock(BiomeKind kind) {
        return rockCrags(kind) + ground(kind).outcrops().density();
    }

    /** The share of a biome its crags take when they are of rock; a crag of lava is no rock. */
    private static float rockCrags(BiomeKind kind) {
        return ground(kind).crags().terrain() == Terrain.ROCK
                ? ground(kind).crags().density()
                : 0f;
    }
}
