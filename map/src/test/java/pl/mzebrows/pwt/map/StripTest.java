package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripTest {

    static final HouseRules HOUSES = new HouseRules(8, 12, 7, 10, 2, 3, 0.25f, 8, 9);
    static final StripRules RULES = new StripRules(30, 3, 2, 3, 3, 4, 1, 2, 6, 4, 8, HOUSES);
    static final float TILE_SIZE = 5f;

    /** Where the strip is entered: against the first biome's west border, on the middle rows. */
    static final TileRect START = new TileRect(1, 12, 2, 5);

    private static final int SEEDS = 8;

    private static final List<BiomeGround> ORDER = List.of(
            TestBiome.QUARRY.ground(), TestBiome.BRAKE.ground(), TestBiome.BADLANDS.ground(), TestBiome.PLAIN.ground());

    private static final BiomeGround FINALE = TestBiome.REDOUBT.ground();

    @Test
    void laysTheBiomesWestToEastWithARidgeBetweenEveryTwoAndBeforeTheFinale() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);

            assertThat(StripFaults.layout(laid, grounds(ORDER), RULES))
                    .as("seed %d", seed)
                    .isEmpty();
            assertThat(StripFaults.passes(laid, RULES)).as("seed %d", seed).isEmpty();
        }
    }

    @Test
    void entersTheFirstBiomeByTheStartAndEveryOtherByThePassesIntoIt() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);

            assertThat(StripFaults.entries(laid, grounds(ORDER), RULES))
                    .as("seed %d", seed)
                    .isEmpty();
            assertThat(laid.entries().getFirst()).containsExactly(START);
        }
    }

    @Test
    void keepsTheStartTheApronsAndEveryHousesGroundTheBiomesBase() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);

            assertThat(StripFaults.kept(laid, grounds(ORDER), RULES))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void keepsTheStartClearOfHousesAndRockWhereverItIs() {
        TileRect north = new TileRect(1, 2, 3, 4);
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = Strip.lay(RULES, ORDER, FINALE, north, TestStream.seeded(seed), TILE_SIZE);

            assertThat(StripFaults.all(laid, grounds(ORDER), RULES))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void refusesAStartOutsideTheFirstBiome() {
        assertThatThrownBy(() ->
                        Strip.lay(RULES, ORDER, FINALE, new TileRect(0, 12, 2, 5), TestStream.seeded(1L), TILE_SIZE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inside the first biome");
    }

    @Test
    void putsEveryRidgeTileInTheBiomeWhoseGroundItShows() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);

            assertThat(StripFaults.membership(laid, grounds(ORDER)))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void putsAOneColumnRidgeInTheBiomeToItsEast() {
        StripRules wall = new StripRules(30, 3, 2, 1, 3, 4, 1, 2, 6, 4, 8, gatehouses(0f));

        Strip.Laid laid = Strip.lay(wall, ORDER, FINALE, START, TestStream.seeded(5L), TILE_SIZE);

        for (int b = 0; b < StripFaults.ridges(laid); b++) {
            int col = StripFaults.westEdge(laid, b);
            for (int r = 0; r < wall.rows(); r++) {
                assertThat(laid.map().biomeAtTile(col, r))
                        .as("ridge %d, row %d", b, r)
                        .isEqualTo(b + 1);
            }
        }
        assertThat(StripFaults.all(laid, grounds(ORDER), wall)).isEmpty();
    }

    @Test
    void refusesRulesThatCannotEnterAPassFarEnoughFromTheLastOne() {
        assertThatThrownBy(() -> new StripRules(16, 3, 2, 3, 3, 4, 1, 2, 6, 4, 8, HOUSES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 rows from the last");
    }

    @Test
    void refusesADoorwayNarrowerThanTheClearanceOrWiderThanAPass() {
        assertThatThrownBy(() -> rules(new HouseRules(8, 12, 7, 10, 2, 1, 0.25f, 8, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least the clearance");
        assertThatThrownBy(() -> rules(new HouseRules(8, 12, 7, 10, 2, 5, 0.25f, 8, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most a pass");
    }

    @Test
    void refusesAHouseTooSmallToHoldADoorwayOffItsCorners() {
        assertThatThrownBy(() -> new HouseRules(4, 12, 7, 10, 2, 3, 0.25f, 8, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("off its corners");
    }

    @Test
    void refusesAGatehouseTooHighToStandInsideTheBorder() {
        assertThatThrownBy(() -> rules(new HouseRules(8, 12, 7, 10, 2, 3, 0.25f, 8, 27)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gatehouse 27 high");
        assertThat(rules(new HouseRules(8, 12, 7, 10, 2, 3, 0f, 8, 27))).isNotNull();
    }

    @Test
    void honoursAHouseWithADoorwayInEveryWall() {
        StripRules four = rules(new HouseRules(8, 12, 7, 10, 4, 3, 0.25f, 8, 9));
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = Strip.lay(four, ORDER, FINALE, START, TestStream.seeded(seed), TILE_SIZE);

            assertThat(StripFaults.all(laid, grounds(ORDER), four))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void keepsEveryPassOpenFromOneEndOfTheStripToTheOther() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);

            assertThat(StripFaults.reach(laid)).as("seed %d", seed).isEmpty();
            assertThat(StripFaults.clearance(laid, RULES)).as("seed %d", seed).isEmpty();
        }
    }

    /**
     * A ridge's face rock a row in from the border, with the row next to the border left open beside a pass, was a
     * slot a tile high under the border (found by the mission sweep, 2026-11-15). These seeds laid one, at the
     * bottom and the top.
     */
    @Test
    void leavesNoSlotWhereARidgesFaceMeetsTheBorder() {
        for (long seed : new long[] {3204, 6759}) {
            assertThat(StripFaults.clearance(lay(ORDER, seed), RULES))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void leavesTheFinalesWestEdgeStraight() {
        for (long seed = 0; seed < SEEDS; seed++) {
            assertThat(StripFaults.finaleEdge(lay(ORDER, seed)))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void anchorsAFinaleWhereTheAdventuresHaveTheirRedoubt() {
        assertThat(Strip.anchor(new TileRect(181, 1, 34, 28))).isEqualTo(TileRect.ofTile(197, 15));
    }

    @Test
    void laysAFinalesPiecesAsRockAtTheirOffsetsFromItsAnchor() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);
            TileRect anchor = Strip.anchor(laid.biomes().getLast());

            for (TileRect piece : FINALE.pieces()) {
                for (int row = piece.row(); row < piece.endRow(); row++) {
                    for (int col = piece.col(); col < piece.endCol(); col++) {
                        assertThat(laid.map().terrainAtTile(anchor.col() + col, anchor.row() + row))
                                .as("seed %d, piece %s at %d,%d", seed, piece, col, row)
                                .isEqualTo(Terrain.ROCK);
                    }
                }
            }
            assertThat(laid.map().terrainAtTile(anchor.col(), anchor.row())).isEqualTo(FINALE.base());
        }
    }

    @Test
    void refusesAPieceThatReachesPastItsBiome() {
        BiomeGround past = new BiomeGround(
                34,
                34,
                3,
                Terrain.DUST,
                Terrain.ASH,
                Houses.off(),
                List.of(),
                Crags.off(),
                Outcrops.off(),
                List.of(new TileRect(18, 0, 1, 1)));

        assertThatThrownBy(() -> Strip.lay(RULES, ORDER, past, START, TestStream.seeded(1L), TILE_SIZE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reaches past");
    }

    @Test
    void wallsEveryHouseWithItsDoorwaysInWallsOfTheirOwnAndItsInsideOpen() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = lay(ORDER, seed);

            assertThat(StripFaults.houses(laid, grounds(ORDER), RULES))
                    .as("seed %d", seed)
                    .isEmpty();
            assertThat(StripFaults.housesIn(laid, 0))
                    .as("seed %d, the quarry", seed)
                    .isBetween(1, 2);
        }
    }

    @Test
    void makesEveryRidgeButTheOneBeforeTheFinaleAGatehouseAtAFullShare() {
        StripRules always = rules(gatehouses(1f));
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = Strip.lay(always, ORDER, FINALE, START, TestStream.seeded(seed), TILE_SIZE);

            for (int b = 0; b < StripFaults.ridges(laid); b++) {
                assertThat(StripFaults.gatehouse(laid, b))
                        .as("seed %d, ridge %d", seed, b)
                        .isEqualTo(b < StripFaults.ridges(laid) - 1);
            }
            assertThat(StripFaults.all(laid, grounds(ORDER), always))
                    .as("seed %d", seed)
                    .isEmpty();
        }
    }

    @Test
    void laysNoGatehouseAtAShareOfNothing() {
        StripRules never = rules(gatehouses(0f));
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = Strip.lay(never, ORDER, FINALE, START, TestStream.seeded(seed), TILE_SIZE);

            for (int b = 0; b < StripFaults.ridges(laid); b++) {
                assertThat(StripFaults.gatehouse(laid, b))
                        .as("seed %d, ridge %d", seed, b)
                        .isFalse();
            }
        }
    }

    @Test
    void entersAGatehouseByItsWestWallAndLeavesByItsEastOnAnotherRow() {
        StripRules always = rules(gatehouses(1f));
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid laid = Strip.lay(always, ORDER, FINALE, START, TestStream.seeded(seed), TILE_SIZE);

            House gatehouse = StripFaults.gatehouseIn(laid, 0);
            assertThat(gatehouse).as("seed %d", seed).isNotNull();
            TileRect in = gatehouse.doorways().get(0);
            TileRect out = gatehouse.doorways().get(1);
            assertThat(StripFaults.wallOf(gatehouse, in)).as("seed %d", seed).isZero();
            assertThat(StripFaults.wallOf(gatehouse, out)).as("seed %d", seed).isOne();
            assertThat(out.row()).as("seed %d", seed).isNotEqualTo(in.row());
        }
    }

    @Test
    void laysTheSameStripFromTheSameStream() {
        Strip.Laid once = lay(ORDER, 11L);
        Strip.Laid twice = lay(ORDER, 11L);

        assertThat(twice.biomes()).isEqualTo(once.biomes());
        assertThat(twice.houses()).isEqualTo(once.houses());
        assertThat(terrain(twice.map())).isEqualTo(terrain(once.map()));
        assertThat(regions(twice.map())).isEqualTo(regions(once.map()));
    }

    @Test
    void layingDrawsNothingFromTheMapStreamItself() {
        TestStream stream = TestStream.seeded(3L);
        TestStream twin = TestStream.seeded(3L);

        Strip.lay(RULES, ORDER, FINALE, START, stream, TILE_SIZE);

        assertThat(stream.nextLong()).isEqualTo(twin.nextLong());
    }

    @Test
    void retuningOneBiomesCoatsMovesNoOtherBiome() {
        BiomeGround badlands = ORDER.get(2);
        BiomeGround retuned = new BiomeGround(
                badlands.widthMin(),
                badlands.widthMax(),
                badlands.regions(),
                badlands.base(),
                badlands.fallback(),
                badlands.houses(),
                badlands.patches(),
                new Crags(0.05f, 2, Terrain.ROCK),
                Outcrops.off(),
                badlands.pieces());
        List<BiomeGround> order = new ArrayList<>(ORDER);
        order.set(2, retuned);
        for (long seed = 0; seed < SEEDS; seed++) {
            Strip.Laid original = lay(ORDER, seed);
            Strip.Laid changed = lay(order, seed);

            assertThat(changed.biomes()).isEqualTo(original.biomes());
            for (int p = 0; p < original.biomes().size(); p++) {
                if (p != 2) {
                    TileRect rect = original.biomes().get(p);
                    assertThat(terrainIn(changed.map(), rect))
                            .as("seed %d, biome %d", seed, p)
                            .isEqualTo(terrainIn(original.map(), rect));
                }
            }
        }
    }

    /** The strip's rules with houses of their own. */
    static StripRules rules(HouseRules houses) {
        return new StripRules(30, 3, 2, 3, 3, 4, 1, 2, 6, 4, 8, houses);
    }

    /** The strip's houses with a gatehouse share of their own. */
    static HouseRules gatehouses(float share) {
        return new HouseRules(
                HOUSES.widthMin(),
                HOUSES.widthMax(),
                HOUSES.heightMin(),
                HOUSES.heightMax(),
                HOUSES.doorways(),
                HOUSES.doorwayWidth(),
                share,
                HOUSES.gatehouseWidth(),
                HOUSES.gatehouseHeight());
    }

    static Strip.Laid lay(List<BiomeGround> order, long seed) {
        return Strip.lay(RULES, order, FINALE, START, TestStream.seeded(seed), TILE_SIZE);
    }

    /** The dealt biomes and the finale, as the strip lays them west to east. */
    static List<BiomeGround> grounds(List<BiomeGround> order) {
        List<BiomeGround> grounds = new ArrayList<>(order);
        grounds.add(FINALE);
        return grounds;
    }

    private static Terrain[] terrain(TileMap map) {
        return terrainIn(map, new TileRect(0, 0, map.cols(), map.rows()));
    }

    private static Terrain[] terrainIn(TileMap map, TileRect rect) {
        Terrain[] tiles = new Terrain[rect.tileCount()];
        for (int r = rect.row(); r < rect.endRow(); r++) {
            for (int c = rect.col(); c < rect.endCol(); c++) {
                tiles[(r - rect.row()) * rect.width() + c - rect.col()] = map.terrainAtTile(c, r);
            }
        }
        return tiles;
    }

    private static int[] regions(TileMap map) {
        int[] regions = new int[map.cols() * map.rows()];
        for (int i = 0; i < regions.length; i++) {
            regions[i] = map.regionAtTile(i % map.cols(), i / map.cols());
        }
        return regions;
    }
}
