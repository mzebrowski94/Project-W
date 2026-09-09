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

    private static TileMap plain() {
        return TileMap.builder(COLS, ROWS, TILE).fill(Terrain.PLAIN).build();
    }
}
