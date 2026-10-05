package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * One biome's coats laid into one rect, as a biome in the middle of a strip sees it: rock above and below,
 * a ridge face either side with a pass through each, and an apron in front of each pass.
 */
class CoatsTest {

    private static final int SEEDS = 64;
    private static final int CLEARANCE = 2;

    /** How far a coat's median share may sit off its density: thicket's big blobs land 1.3 points over. */
    private static final float MEDIAN_OFF = 0.015f;

    /**
     * The least share of its density any seed may land at. Rock laid over patches is what comes nearest:
     * the badlands' neighbour patches under 15% rock go under it on 4 seeds in 2000, none of these 64.
     */
    private static final float FLOOR = 0.5f;

    /**
     * Lay-until-reached overshoots by half a piece on average and one piece at worst, and rock laid later
     * eats into the patches under it - so the median over the seeds is held close, and every seed only off
     * the floor that running out of room would break through.
     */
    @Test
    void laysEachCoatAtItsDensity() {
        for (Biome biome : Biome.FIVE) {
            BiomeGround ground = biome.ground();
            assertLandsAt(
                    biome.name() + " rock",
                    biome.rock(),
                    seed -> Rig.laid(ground, seed).share(Terrain.ROCK));
            for (Patch layer : ground.patches()) {
                Terrain terrain = layer.ground().resolve(null, ground.base(), ground.fallback());
                assertLandsAt(
                        biome.name() + " " + terrain,
                        layer.density(),
                        seed -> Rig.laid(ground, seed).share(terrain));
            }
        }
    }

    @Test
    void leavesNoPassageUnderTheClearanceAndNothingSealedOff() {
        for (Biome biome : Biome.FIVE) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Rig rig = Rig.laid(biome.ground(), seed);

                assertThat(rig.narrowest())
                        .as("%s, seed %d", biome.name(), seed)
                        .isGreaterThanOrEqualTo(CLEARANCE);
                assertThat(rig.pockets()).as("%s, seed %d", biome.name(), seed).isZero();
                assertThat(rig.passesConnect())
                        .as("%s, seed %d", biome.name(), seed)
                        .isTrue();
            }
        }
    }

    @Test
    void keepsTheApronToBaseGround() {
        for (Biome biome : Biome.FIVE) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Rig rig = Rig.laid(biome.ground(), seed);

                assertThat(rig.apronGround())
                        .as("%s, seed %d", biome.name(), seed)
                        .containsOnly(biome.ground().base());
            }
        }
    }

    @Test
    void retuningOneCoatMovesNoOther() {
        BiomeGround badlands = Biome.BADLANDS.ground();
        BiomeGround fewerPatches = withPatches(badlands, List.of(new Patch.Rect(PatchGround.neighbour(), 0.08f, 4, 6)));
        BiomeGround fewerOutcrops = withOutcrops(badlands, new Outcrops(0.05f, 2, 4, 1, 2, 0.3f));
        BiomeGround moreCrags = withCrags(badlands, new Crags(0.08f, 4));
        for (long seed = 0; seed < SEEDS; seed++) {
            Rig original = Rig.laid(badlands, seed);

            assertThat(Rig.laid(fewerPatches, seed).rockTiles()).isEqualTo(original.rockTiles());
            assertThat(Rig.laid(fewerOutcrops, seed).groundWhereBothAreGround(original))
                    .isEqualTo(original.groundWhereBothAreGround(Rig.laid(fewerOutcrops, seed)));
            assertThat(Rig.laid(moreCrags, seed).groundWhereBothAreGround(original))
                    .isEqualTo(original.groundWhereBothAreGround(Rig.laid(moreCrags, seed)));
        }
    }

    @Test
    void laysTheSameGroundFromTheSameStream() {
        for (Biome biome : Biome.FIVE) {
            assertThat(Rig.laid(biome.ground(), 7L).tiles())
                    .isEqualTo(Rig.laid(biome.ground(), 7L).tiles());
        }
    }

    @Test
    void layingDrawsNothingFromTheBiomesOwnStream() {
        TestStream stream = TestStream.seeded(3L);
        TestStream twin = TestStream.seeded(3L);

        Rig.laid(Biome.QUARRY.ground(), stream);

        assertThat(stream.nextLong()).isEqualTo(twin.nextLong());
    }

    @Test
    void neighbourGroundTakesTheBaseOfTheBiomeOnTheNearerSide() {
        BiomeGround plain = Biome.PLAIN.ground();
        for (long seed = 0; seed < SEEDS; seed++) {
            Rig rig = Rig.laid(plain, Terrain.ASH, Terrain.PLAIN, TestStream.seeded(seed));

            assertThat(rig.groundIn(Rig.WEST_HALF)).doesNotContain(Terrain.DUST, Terrain.ORGANIC);
            assertThat(rig.groundIn(Rig.EAST_HALF)).doesNotContain(Terrain.ASH);
        }
    }

    @Test
    void neverClearsRockItDidNotLay() {
        TileRect spill = new TileRect(1, 16, 2, 10);
        for (Biome biome : List.of(Biome.ANTHILL, Biome.BADLANDS)) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Rig rig = Rig.laid(biome.ground(), null, null, spill, TestStream.seeded(seed));

                assertThat(rig.groundIn(spill))
                        .as("%s, seed %d", biome.name(), seed)
                        .containsOnly(Terrain.ROCK);
                assertThat(rig.narrowest())
                        .as("%s, seed %d", biome.name(), seed)
                        .isGreaterThanOrEqualTo(CLEARANCE);
                assertThat(rig.pockets()).as("%s, seed %d", biome.name(), seed).isZero();
            }
        }
    }

    @Test
    void aCoatAskedForMoreThanFitsStillLeavesEverythingReachable() {
        BiomeGround quarry = Biome.QUARRY.ground();
        List<BiomeGround> greedy = List.of(
                withOutcrops(quarry, new Outcrops(0.9f, 4, 9, 2, 3, 0.7f)), withCrags(quarry, new Crags(0.6f, 4)));
        for (BiomeGround ground : greedy) {
            for (long seed = 0; seed < 8; seed++) {
                Rig rig = Rig.laid(ground, seed);

                assertThat(rig.passesConnect()).isTrue();
                assertThat(rig.pockets()).isZero();
                assertThat(rig.narrowest()).isGreaterThanOrEqualTo(CLEARANCE);
            }
        }
    }

    private static BiomeGround withPatches(BiomeGround g, List<Patch> patches) {
        return new BiomeGround(
                g.widthMin(), g.widthMax(), g.regions(), g.base(), g.fallback(), patches, g.crags(), g.outcrops());
    }

    private static BiomeGround withCrags(BiomeGround g, Crags crags) {
        return new BiomeGround(
                g.widthMin(), g.widthMax(), g.regions(), g.base(), g.fallback(), g.patches(), crags, g.outcrops());
    }

    private static BiomeGround withOutcrops(BiomeGround g, Outcrops outcrops) {
        return new BiomeGround(
                g.widthMin(), g.widthMax(), g.regions(), g.base(), g.fallback(), g.patches(), g.crags(), outcrops);
    }

    /** The median within {@link #MEDIAN_OFF} of the density, and no seed under {@link #FLOOR} of it. */
    private static void assertLandsAt(String coat, float density, java.util.function.LongFunction<Float> share) {
        float[] shares = new float[SEEDS];
        for (int seed = 0; seed < SEEDS; seed++) {
            shares[seed] = share.apply(seed);
        }
        java.util.Arrays.sort(shares);

        assertThat(shares[SEEDS / 2]).as("%s median", coat).isCloseTo(density, within(MEDIAN_OFF));
        assertThat(shares[0]).as("%s lowest", coat).isGreaterThanOrEqualTo(density * FLOOR);
    }

    /**
     * A biome 54 columns by 28 rows inside a ring of rock, a four-row pass through each side - west low, east
     * high - and an apron three tiles deep in front of each.
     */
    private static final class Rig {

        static final int WIDTH = 54;
        static final int HEIGHT = 28;
        static final int COLS = WIDTH + 2;
        static final int ROWS = HEIGHT + 2;
        static final int PASS = 4;
        static final int APRON = 3;
        static final int WEST_PASS = 8;
        static final int EAST_PASS = 18;
        static final TileRect AREA = new TileRect(1, 1, WIDTH, HEIGHT);
        static final TileRect WEST_HALF = new TileRect(1, 1, WIDTH / 2 - 3, HEIGHT);
        static final TileRect EAST_HALF = new TileRect(WIDTH / 2 + 4, 1, WIDTH / 2 - 3, HEIGHT);

        private final TileMap map;
        private final boolean[] apron;
        private final int[] ends;

        private Rig(TileMap map, boolean[] apron, int[] ends) {
            this.map = map;
            this.apron = apron;
            this.ends = ends;
        }

        static Rig laid(BiomeGround ground, long seed) {
            return laid(ground, TestStream.seeded(seed));
        }

        static Rig laid(BiomeGround ground, Randomness stream) {
            return laid(ground, null, null, stream);
        }

        static Rig laid(BiomeGround ground, @Nullable Terrain west, @Nullable Terrain east, Randomness stream) {
            return laid(ground, west, east, null, stream);
        }

        /** With {@code spill} rock before the coats are laid, as a ridge face wandering into the biome. */
        static Rig laid(
                BiomeGround ground,
                @Nullable Terrain west,
                @Nullable Terrain east,
                @Nullable TileRect spill,
                Randomness stream) {
            TileMap.Builder builder =
                    TileMap.builder(COLS, ROWS, 5f).fill(ground.base()).border(1, Terrain.ROCK);
            if (spill != null) {
                builder.rect(spill.col(), spill.row(), spill.width(), spill.height(), Terrain.ROCK);
            }
            boolean[] apron = new boolean[COLS * ROWS];
            int[] ends = new int[PASS * 2];
            for (int i = 0; i < PASS; i++) {
                builder.set(0, WEST_PASS + i, ground.base()).set(COLS - 1, EAST_PASS + i, ground.base());
                ends[i] = (WEST_PASS + i) * COLS;
                ends[PASS + i] = (EAST_PASS + i) * COLS + COLS - 1;
            }
            for (int d = 1; d <= APRON; d++) {
                for (int r = -1; r <= PASS; r++) {
                    apron[(WEST_PASS + r) * COLS + d] = true;
                    apron[(EAST_PASS + r) * COLS + COLS - 1 - d] = true;
                }
            }
            Coats.lay(builder, AREA, ground, west, east, apron, ends, CLEARANCE, stream);
            return new Rig(builder.build(), apron, ends);
        }

        float share(Terrain terrain) {
            int found = 0;
            for (int r = AREA.row(); r < AREA.endRow(); r++) {
                for (int c = AREA.col(); c < AREA.endCol(); c++) {
                    if (map.terrainAtTile(c, r) == terrain) {
                        found++;
                    }
                }
            }
            return found / (float) AREA.tileCount();
        }

        Terrain[] tiles() {
            Terrain[] tiles = new Terrain[COLS * ROWS];
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    tiles[r * COLS + c] = map.terrainAtTile(c, r);
                }
            }
            return tiles;
        }

        boolean[] rockTiles() {
            boolean[] rock = new boolean[COLS * ROWS];
            for (int i = 0; i < rock.length; i++) {
                rock[i] = !map.passableAtTile(i % COLS, i / COLS);
            }
            return rock;
        }

        /** This rig's ground on every tile both rigs left open, and null elsewhere. */
        @Nullable
        Terrain[] groundWhereBothAreGround(Rig other) {
            @Nullable Terrain[] ground = new Terrain[COLS * ROWS];
            for (int i = 0; i < ground.length; i++) {
                int c = i % COLS;
                int r = i / COLS;
                if (map.passableAtTile(c, r) && other.map.passableAtTile(c, r)) {
                    ground[i] = map.terrainAtTile(c, r);
                }
            }
            return ground;
        }

        List<Terrain> apronGround() {
            List<Terrain> found = new java.util.ArrayList<>();
            for (int i = 0; i < apron.length; i++) {
                if (apron[i]) {
                    found.add(map.terrainAtTile(i % COLS, i / COLS));
                }
            }
            return found;
        }

        List<Terrain> groundIn(TileRect rect) {
            List<Terrain> found = new java.util.ArrayList<>();
            for (int r = rect.row(); r < rect.endRow(); r++) {
                for (int c = rect.col(); c < rect.endCol(); c++) {
                    found.add(map.terrainAtTile(c, r));
                }
            }
            return found;
        }

        boolean passesConnect() {
            boolean[] reached = Maps.reachableFrom(map, ends[0] % COLS, ends[0] / COLS);
            for (int end : ends) {
                if (!reached[end]) {
                    return false;
                }
            }
            return true;
        }

        int pockets() {
            boolean[] reached = Maps.reachableFrom(map, ends[0] % COLS, ends[0] / COLS);
            int pockets = 0;
            for (int r = AREA.row(); r < AREA.endRow(); r++) {
                for (int c = AREA.col(); c < AREA.endCol(); c++) {
                    if (map.passableAtTile(c, r) && !reached[r * COLS + c]) {
                        pockets++;
                    }
                }
            }
            return pockets;
        }

        /** The shortest run of open ground between two rocks along any row or column through the biome. */
        int narrowest() {
            int narrowest = Integer.MAX_VALUE;
            for (int r = AREA.row(); r < AREA.endRow(); r++) {
                narrowest = Math.min(narrowest, narrowestRun(0, r, 1, 0, COLS));
            }
            for (int c = AREA.col(); c < AREA.endCol(); c++) {
                narrowest = Math.min(narrowest, narrowestRun(c, 0, 0, 1, ROWS));
            }
            return narrowest;
        }

        private int narrowestRun(int col, int row, int dc, int dr, int length) {
            int narrowest = Integer.MAX_VALUE;
            int run = -1;
            for (int step = 0; step < length; step++) {
                boolean open = map.passableAtTile(col + dc * step, row + dr * step);
                if (open && run >= 0) {
                    run++;
                } else if (!open) {
                    if (run > 0) {
                        narrowest = Math.min(narrowest, run);
                    }
                    run = 0;
                }
            }
            return narrowest;
        }
    }

    /** The five first biomes, written out as {@code biomes.properties} will hold them. */
    private record Biome(String name, BiomeGround ground) {

        static final Biome PLAIN = new Biome(
                "plain",
                new BiomeGround(
                        50,
                        58,
                        3,
                        Terrain.PLAIN,
                        Terrain.DUST,
                        List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                        Crags.off(),
                        new Outcrops(0.006f, 1, 2, 1, 1, 0f)));

        static final Biome QUARRY = new Biome(
                "quarry",
                new BiomeGround(
                        50,
                        58,
                        3,
                        Terrain.DUST,
                        Terrain.PLAIN,
                        List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                        Crags.off(),
                        new Outcrops(0.10f, 4, 9, 2, 3, 0.7f)));

        static final Biome BRAKE = new Biome(
                "brake",
                new BiomeGround(
                        50,
                        58,
                        3,
                        Terrain.PLAIN,
                        Terrain.ORGANIC,
                        List.of(
                                new Patch.Rect(PatchGround.of(Terrain.ORGANIC), 0.035f, 4, 6),
                                new Patch.Blob(PatchGround.of(Terrain.THICKET), 0.18f, 6, 11)),
                        Crags.off(),
                        new Outcrops(0.015f, 2, 4, 1, 2, 0.3f)));

        static final Biome ANTHILL = new Biome(
                "anthill",
                new BiomeGround(
                        50,
                        58,
                        3,
                        Terrain.ORGANIC,
                        Terrain.DUST,
                        List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                        new Crags(0.05f, 4),
                        Outcrops.off()));

        static final Biome BADLANDS = new Biome(
                "badlands",
                new BiomeGround(
                        50,
                        58,
                        3,
                        Terrain.ASH,
                        Terrain.DUST,
                        List.of(new Patch.Rect(PatchGround.neighbour(), 0.04f, 4, 6)),
                        new Crags(0.13f, 4),
                        new Outcrops(0.02f, 2, 4, 1, 2, 0.3f)));

        static final List<Biome> FIVE = List.of(PLAIN, QUARRY, BRAKE, ANTHILL, BADLANDS);

        float rock() {
            return ground.crags().density() + ground.outcrops().density();
        }
    }
}
