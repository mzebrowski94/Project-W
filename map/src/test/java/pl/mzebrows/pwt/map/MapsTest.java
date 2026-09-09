package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MapsTest {

    private static final float TILE = 5f;
    private static final int COLS = 32;
    private static final int ROWS = 18;
    private static final int REGIONS = 6;

    @Test
    void fillsAUniformMapWithOneTerrainAndOneRegion() {
        TileMap map = Maps.uniform(COLS, ROWS, TILE, Terrain.PLAIN);

        assertThat(map.regionCount()).isEqualTo(1);
        assertThat(everyTerrainIn(map)).containsExactly(Terrain.PLAIN);
    }

    @Test
    void splitsAMapIntoAsManyRegionsAsAsked() {
        TileMap map = Maps.regions(COLS, ROWS, TILE, REGIONS, seeded());

        assertThat(map.regionCount()).isEqualTo(REGIONS);
    }

    @Test
    void givesEveryTileARegionAndLeavesThemAllPassable() {
        TileMap map = Maps.regions(COLS, ROWS, TILE, REGIONS, seeded());

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                assertThat(map.regionAtTile(col, row)).isBetween(0, REGIONS - 1);
                assertThat(map.passableAtTile(col, row)).isTrue();
            }
        }
    }

    @Test
    void buildsTheSameMapTwiceFromTheSameStream() {
        TileMap first = Maps.regions(COLS, ROWS, TILE, REGIONS, seeded());
        TileMap second = Maps.regions(COLS, ROWS, TILE, REGIONS, seeded());

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                assertThat(second.regionAtTile(col, row)).isEqualTo(first.regionAtTile(col, row));
            }
        }
    }

    @Test
    void neverAsksForMoreRegionsThanThereAreTiles() {
        TileMap map = Maps.regions(2, 2, TILE, 99, seeded());

        assertThat(map.regionCount()).isEqualTo(4);
    }

    @Test
    void refusesAMapWithNoRegionsAtAll() {
        assertThatThrownBy(() -> Maps.regions(COLS, ROWS, TILE, 0, seeded()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsTilesBehindAnUnbrokenWallAsUnreachable() {
        TileMap map = TileMap.builder(9, 9, TILE)
                .fill(Terrain.PLAIN)
                .rect(4, 0, 1, 9, Terrain.ROCK)
                .build();

        assertThat(Maps.connects(map, 0, 0, 8, 8)).isFalse();
    }

    @Test
    void walksAroundAWallThatHasAGapInIt() {
        TileMap map = TileMap.builder(9, 9, TILE)
                .fill(Terrain.PLAIN)
                .rect(4, 0, 1, 9, Terrain.ROCK)
                .set(4, 4, Terrain.PLAIN)
                .build();

        assertThat(Maps.connects(map, 0, 0, 8, 8)).isTrue();
    }

    /** A unit has width, so squeezing between two rock corners is not a way through. */
    @Test
    void refusesToCountADiagonalPinchAsAWayThrough() {
        TileMap map = TileMap.builder(3, 3, TILE)
                .fill(Terrain.PLAIN)
                .set(1, 0, Terrain.ROCK)
                .set(0, 1, Terrain.ROCK)
                .set(1, 2, Terrain.ROCK)
                .set(2, 1, Terrain.ROCK)
                .build();

        assertThat(Maps.connects(map, 0, 0, 2, 2)).isFalse();
    }

    @Test
    void reachesNothingAtAllFromInsideRock() {
        TileMap map = TileMap.builder(4, 4, TILE)
                .fill(Terrain.PLAIN)
                .set(1, 1, Terrain.ROCK)
                .build();

        assertThat(Maps.reachableFrom(map, 1, 1)).containsOnly(false);
    }

    @Test
    void fillsInAPocketNothingCanWalkTo() {
        TileMap.Builder builder =
                TileMap.builder(9, 9, TILE).fill(Terrain.PLAIN).rect(4, 0, 1, 9, Terrain.ROCK);

        TileMap map = Maps.sealUnreachable(builder, 0, 0, Terrain.ROCK).build();

        assertThat(map.terrainAtTile(8, 8)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(0, 0)).isEqualTo(Terrain.PLAIN);
    }

    /**
     * A Weyl sequence: deterministic, well spread in its high bits, and deliberately not the
     * simulation's generator - these tests are about the partition, not about SplitMix64.
     */
    private static Randomness seeded() {
        long[] state = {0L};
        return () -> state[0] += 0x9E3779B97F4A7C15L;
    }

    private static java.util.Set<Terrain> everyTerrainIn(TileMap map) {
        java.util.EnumSet<Terrain> found = java.util.EnumSet.noneOf(Terrain.class);
        for (int row = 0; row < map.rows(); row++) {
            for (int col = 0; col < map.cols(); col++) {
                found.add(map.terrainAtTile(col, row));
            }
        }
        return found;
    }
}
