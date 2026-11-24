package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class TileMapTest {

    private static final float TILE = 5f;
    private static final int COLS = 32;
    private static final int ROWS = 18;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-5f);

    /** A grunt: a one-tile crack is a way through. */
    private static final float SLIM = 1.8f;

    /** A brute: 5.2 across against a 5-unit tile, so a one-tile crack is a wall. */
    private static final float BROAD = 2.6f;

    /** Columns to a region in {@link #banded}: four regions across the map. */
    private static final int BAND = 8;

    private static final int REGIONS = COLS / BAND;

    @Test
    void reportsTheWorldSizeItsGridAddsUpTo() {
        TileMap map = plain();

        assertThat(map.worldWidth()).isCloseTo(160f, TOLERANCE);
        assertThat(map.worldHeight()).isCloseTo(90f, TOLERANCE);
    }

    @Test
    void turnsAWorldPositionIntoTheTileHoldingIt() {
        TileMap map = plain();

        assertThat(map.colAt(0f)).isZero();
        assertThat(map.colAt(4.999f)).isZero();
        assertThat(map.colAt(5f)).isEqualTo(1);
        assertThat(map.rowAt(89.999f)).isEqualTo(ROWS - 1);
    }

    @Test
    void readsAnythingOutsideItselfAsImpassable() {
        TileMap map = plain();

        assertThat(map.passableAt(-0.001f, 10f)).isFalse();
        assertThat(map.passableAt(10f, -0.001f)).isFalse();
        assertThat(map.passableAt(160f, 10f)).isFalse();
        assertThat(map.passableAt(10f, 90f)).isFalse();
    }

    @Test
    void namesTheBlockingTerrainForTilesItDoesNotHave() {
        TileMap map = plain();

        assertThat(map.terrainAtTile(-1, 0)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(COLS, 0)).isEqualTo(Terrain.ROCK);
        assertThat(map.regionAtTile(-1, 0)).isEqualTo(TileMap.NO_REGION);
        assertThat(map.biomeAtTile(-1, 0)).isEqualTo(TileMap.NO_BIOME);
        assertThat(map.biomeAt(-1f, 0f)).isEqualTo(TileMap.NO_BIOME);
    }

    @Test
    void remembersTheTerrainAndRegionEachTileWasBuiltWith() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .set(3, 4, Terrain.ROCK)
                .region(3, 4, 7)
                .build();

        assertThat(map.terrainAtTile(3, 4)).isEqualTo(Terrain.ROCK);
        assertThat(map.regionAtTile(3, 4)).isEqualTo(7);
        assertThat(map.terrainAtTile(3, 5)).isEqualTo(Terrain.PLAIN);
        assertThat(map.passableAt(17.5f, 22.5f)).isFalse();
    }

    @Test
    void countsOneMoreRegionThanTheHighestItWasGiven() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .region(0, 0, 4)
                .build();

        assertThat(map.regionCount()).isEqualTo(5);
    }

    /** A map that names no biomes is one biome, so a level without them reads exactly as it did before. */
    @Test
    void putsEveryTileInBiomeZeroUntilToldOtherwise() {
        TileMap map = banded(TileMap.builder(COLS, ROWS, TILE)).build();

        assertThat(map.biomeCount()).isEqualTo(1);
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                assertThat(map.biomeAtTile(col, row)).isZero();
            }
        }
    }

    /** A tile is in its region's biome: one partition, so a region can never straddle two. */
    @Test
    void answersARegionsBiomeForEveryTileInIt() {
        TileMap map = banded(TileMap.builder(COLS, ROWS, TILE))
                .regionInBiome(1, 0)
                .regionInBiome(2, 1)
                .regionInBiome(3, 1)
                .build();

        assertThat(map.biomeCount()).isEqualTo(2);
        for (int col = 0; col < COLS; col++) {
            int expected = col < BAND * 2 ? 0 : 1;
            for (int row = 0; row < ROWS; row++) {
                assertThat(map.biomeAtTile(col, row)).as("tile %d,%d", col, row).isEqualTo(expected);
            }
        }
        assertThat(map.biomeAt(BAND * 2 * TILE - 0.1f, 1f)).isZero();
        assertThat(map.biomeAt(BAND * 2 * TILE, 1f)).isEqualTo(1);
    }

    @Test
    void marksNoRidgeOnAMapThatNamesNone() {
        TileMap map = banded(TileMap.builder(COLS, ROWS, TILE)).build();

        assertThat(map.ridgeCount()).isZero();
        assertThat(map.ridgesReached(COLS - 1)).isZero();
    }

    /** Counted by each ridge's west edge, in whatever order they were marked (G0118). */
    @Test
    void countsTheRidgesAtOrWestOfAColumn() {
        TileMap map =
                TileMap.builder(COLS, ROWS, TILE).ridge(BAND * 2).ridge(BAND).build();

        assertThat(map.ridgeCount()).isEqualTo(2);
        assertThat(map.ridgesReached(BAND - 1)).isZero();
        assertThat(map.ridgesReached(BAND)).isEqualTo(1);
        assertThat(map.ridgesReached(BAND * 2 - 1)).isEqualTo(1);
        assertThat(map.ridgesReached(COLS - 1)).isEqualTo(2);
    }

    @Test
    void refusesARidgeOffTheMap() {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);

        assertThatThrownBy(() -> builder.ridge(COLS)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A biome nothing is in would make the count a lie, and every caller sizes from the count. */
    @Test
    void refusesBiomesThatLeaveOneOut() {
        TileMap.Builder builder = banded(TileMap.builder(COLS, ROWS, TILE)).regionInBiome(3, 2);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesABiomeForARegionTheMapDoesNotHave() {
        TileMap.Builder builder = banded(TileMap.builder(COLS, ROWS, TILE)).regionInBiome(REGIONS, 1);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void putsATileCentreInTheMiddleOfIt() {
        TileMap map = plain();

        assertThat(map.tileCentreX(0)).isCloseTo(2.5f, TOLERANCE);
        assertThat(map.tileCentreY(2)).isCloseTo(12.5f, TOLERANCE);
    }

    @Test
    void refusesAGridThatCouldNotHoldALevel() {
        assertThatThrownBy(() -> TileMap.builder(0, ROWS, TILE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TileMap.builder(COLS, 0, TILE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TileMap.builder(COLS, ROWS, 0f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToWriteATileItDoesNotHave() {
        TileMap.Builder builder = TileMap.builder(COLS, ROWS, TILE);

        assertThatThrownBy(() -> builder.set(COLS, 0, Terrain.ROCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callsACircleClearOnlyWhenNothingUnderItBlocks() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .set(4, 4, Terrain.ROCK)
                .build();

        assertThat(map.isClear(10f, 10f, 2f)).isTrue();
        assertThat(map.isClear(22.5f, 22.5f, 2f)).isFalse();
    }

    @Test
    void callsACircleOverhangingTheEdgeOfTheMapUnclear() {
        TileMap map = plain();

        assertThat(map.isClear(1f, 10f, 2f)).isFalse();
        assertThat(map.isClear(10f, 10f, 2f)).isTrue();
    }

    @Test
    void linesTheOutsideOfTheMapWithABorder() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .border(1, Terrain.ROCK)
                .build();

        assertThat(map.terrainAtTile(0, 0)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(COLS - 1, ROWS - 1)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(5, 0)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(1, 1)).isEqualTo(Terrain.PLAIN);
    }

    /** A layout written against one grid must still build on a smaller one rather than throwing. */
    @Test
    void clipsARectangleToTheMapInsteadOfRefusingIt() {
        TileMap map = TileMap.builder(4, 4, TILE)
                .fill(Terrain.PLAIN)
                .rect(2, 2, 99, 99, Terrain.ROCK)
                .build();

        assertThat(map.terrainAtTile(3, 3)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(1, 1)).isEqualTo(Terrain.PLAIN);
    }

    @Test
    void seesAClearLineAcrossOpenGround() {
        assertThat(plain().isClearPath(10f, 10f, 90f, 60f, SLIM)).isTrue();
    }

    @Test
    void seesNoLineThroughAWall() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(10, 0, 1, ROWS, Terrain.ROCK)
                .build();

        assertThat(map.isClearPath(10f, 40f, 90f, 40f, SLIM)).isFalse();
    }

    @Test
    void seesNoLineThroughAGapNarrowerThanTheBody() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(10, 0, 1, ROWS, Terrain.ROCK)
                .set(10, 8, Terrain.PLAIN)
                .build();

        assertThat(map.isClearPath(30f, 42.5f, 80f, 42.5f, SLIM)).isTrue();
        assertThat(map.isClearPath(30f, 42.5f, 80f, 42.5f, BROAD)).isFalse();
    }

    @Test
    void seesStraightAcrossOpenGround() {
        assertThat(plain().clearLine(10f, 42.5f, 140f, 42.5f)).isTrue();
    }

    @Test
    void seesNothingThroughRock() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(10, 0, 1, ROWS, Terrain.ROCK)
                .build();

        assertThat(map.clearLine(10f, 42.5f, 140f, 42.5f)).isFalse();
    }

    /**
     * The case a marched sampler gets wrong. A ray clipping the corner where two walls meet passes
     * through solid ground, but half-tile samples on either side of the corner can both land on open
     * tiles - which is a unit noticing through a wall, once in a while, unreproducibly.
     */
    @Test
    void seesNothingThroughTheCornerWhereTwoWallsMeet() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(5, 5, 1, 1, Terrain.ROCK)
                .build();

        assertThat(map.clearLine(27.4f, 27.4f, 32.6f, 32.6f)).isFalse();
    }

    /** Where a line runs into rock, so a shot that flew into a wall can end at its face rather than inside it. */
    @Test
    void measuresHowFarALineRunsBeforeItMeetsRock() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(10, 0, 1, ROWS, Terrain.ROCK)
                .build();
        float face = 10 * TILE;

        float fraction = map.passableFraction(face - 20f, 42.5f, face + 20f, 42.5f);

        assertThat(fraction).isCloseTo(0.5f, Offset.offset(1e-4f));
    }

    @Test
    void measuresAllOfALineThatMeetsNoRock() {
        assertThat(plain().passableFraction(10f, 42.5f, 140f, 42.5f)).isEqualTo(1f);
    }

    @Test
    void measuresNothingOfALineThatStartsInRock() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(10, 0, 1, ROWS, Terrain.ROCK)
                .build();

        assertThat(map.passableFraction(10 * TILE + 1f, 42.5f, 140f, 42.5f)).isZero();
    }

    @Test
    void seesSomethingStandingInTheSameTile() {
        assertThat(plain().clearLine(12f, 12f, 13f, 13f)).isTrue();
    }

    @Test
    void seesNothingPastTheEdgeOfTheMap() {
        assertThat(plain().clearLine(10f, 42.5f, -30f, 42.5f)).isFalse();
    }

    @Test
    void seesNothingFromInsideRock() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(2, 8, 1, 1, Terrain.ROCK)
                .build();

        assertThat(map.clearLine(12.5f, 42.5f, 140f, 42.5f)).isFalse();
    }

    private static TileMap plain() {
        return TileMap.builder(COLS, ROWS, TILE).fill(Terrain.PLAIN).build();
    }

    /** {@link #REGIONS} regions in column bands of {@link #BAND}, west to east. */
    private static TileMap.Builder banded(TileMap.Builder builder) {
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                builder.region(col, row, col / BAND);
            }
        }
        return builder;
    }
}
