package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScatterTest {

    private static final float TILE = 5f;
    private static final int COLS = 20;
    private static final int ROWS = 20;
    private static final float RADIUS = 1f;
    private static final float CLEARANCE = 1f;

    /** Wider than anything that must get past, so a gap left beside rock is a way through. */
    private static final float ROCK_MARGIN = 4f;

    @Test
    void placesEverythingItWasAskedForWhenThereIsRoom() {
        List<float[]> placed = new ArrayList<>();

        int count = scatterer(open()).scatter(10, RADIUS, (x, y) -> placed.add(new float[] {x, y}));

        assertThat(count).isEqualTo(10);
        assertThat(placed).hasSize(10);
    }

    /** A full arena and a broken generator look identical to a caller that is handed no answer. */
    @Test
    void reportsWhatItCouldNotPlace() {
        TileMap tiny = TileMap.builder(2, 2, TILE).fill(Terrain.PLAIN).build();

        int count = new Scatter(tiny, seeded(), CLEARANCE, 0f).scatter(50, 2f, (x, y) -> {});

        assertThat(count).isLessThan(50);
    }

    @Test
    void keepsEverythingOffGroundItWasNotOfferedTo() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(4, 4, 6, 6, Terrain.ORGANIC)
                .build();
        List<float[]> placed = new ArrayList<>();

        scatterer(map).scatter(8, RADIUS, EnumSet.of(Terrain.ORGANIC), (x, y) -> placed.add(new float[] {x, y}));

        assertThat(placed).isNotEmpty();
        for (float[] spot : placed) {
            assertThat(map.terrainAt(spot[0], spot[1])).isEqualTo(Terrain.ORGANIC);
        }
    }

    /**
     * Something that never moves, laid nearly flush against rock, leaves a slot narrower than anything
     * that must get past it - and nothing routes round an entity, so a man walks into the slot and stays
     * there. Found by a probe in the Adventure, 2026-09-24.
     */
    @Test
    void keepsSomethingThatNeverMovesOffRockByTheMarginItIsGiven() {
        TileMap map = TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(0, 9, COLS, 2, Terrain.ROCK)
                .build();
        List<float[]> placed = new ArrayList<>();

        scatterer(map).keepingOffRock(ROCK_MARGIN).scatter(40, RADIUS, (x, y) -> placed.add(new float[] {x, y}));

        assertThat(placed)
                .isNotEmpty()
                .allSatisfy(at -> assertThat(map.isClear(at[0], at[1], RADIUS + ROCK_MARGIN))
                        .isTrue());
    }

    @Test
    void neverPutsTwoThingsOnTopOfEachOther() {
        List<float[]> placed = new ArrayList<>();

        scatterer(open()).scatter(30, RADIUS, (x, y) -> placed.add(new float[] {x, y}));

        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertThat(distance(placed.get(i), placed.get(j)))
                        .as("two things placed on top of each other")
                        .isGreaterThanOrEqualTo(RADIUS * 2f + CLEARANCE);
            }
        }
    }

    @Test
    void staysAwayFromWhatWasReserved() {
        List<float[]> placed = new ArrayList<>();
        Scatter scatter = scatterer(open());
        scatter.reserve(50f, 50f, 20f);

        scatter.scatter(20, RADIUS, (x, y) -> placed.add(new float[] {x, y}));

        for (float[] spot : placed) {
            assertThat(distance(spot, new float[] {50f, 50f})).isGreaterThanOrEqualTo(20f + RADIUS);
        }
    }

    @Test
    void keepsEverythingClearOfTheEdgeOfTheMap() {
        List<float[]> placed = new ArrayList<>();

        new Scatter(open(), seeded(), CLEARANCE, 4f).scatter(30, RADIUS, (x, y) -> placed.add(new float[] {x, y}));

        for (float[] spot : placed) {
            assertThat(spot[0]).isBetween(RADIUS + 4f, COLS * TILE - RADIUS - 4f);
            assertThat(spot[1]).isBetween(RADIUS + 4f, ROWS * TILE - RADIUS - 4f);
        }
    }

    /** Objects standing at tile centres are the tell that a level was generated rather than laid out. */
    @Test
    void standsThingsInsideTheirTilesRatherThanAtTheCentre() {
        List<float[]> placed = new ArrayList<>();

        scatterer(open()).scatter(20, RADIUS, (x, y) -> placed.add(new float[] {x, y}));

        assertThat(placed)
                .allSatisfy(
                        spot -> assertThat(offsetInTile(spot[0]) == TILE * 0.5f && offsetInTile(spot[1]) == TILE * 0.5f)
                                .isFalse());
    }

    @Test
    void scattersTheSameLayoutTwiceFromTheSameStream() {
        List<float[]> first = new ArrayList<>();
        List<float[]> second = new ArrayList<>();

        scatterer(open()).scatter(15, RADIUS, (x, y) -> first.add(new float[] {x, y}));
        scatterer(open()).scatter(15, RADIUS, (x, y) -> second.add(new float[] {x, y}));

        assertThat(first).hasSameSizeAs(second);
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i)).containsExactly(second.get(i));
        }
    }

    private static Scatter scatterer(TileMap map) {
        return new Scatter(map, seeded(), CLEARANCE, 0f);
    }

    private static TileMap open() {
        return Maps.uniform(COLS, ROWS, TILE, Terrain.PLAIN);
    }

    private static float offsetInTile(float value) {
        return value - (float) Math.floor(value / TILE) * TILE;
    }

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Randomness seeded() {
        long[] state = {0L};
        return () -> state[0] += 0x9E3779B97F4A7C15L;
    }

    @Test
    void keepsASpawnAreaScatterInsideItsOwnTiles() {
        TileMap map = TileMap.builder(20, 20, 5f).fill(Terrain.PLAIN).build();
        TileRect area = new TileRect(1, 8, 2, 4);
        Scatter scatter = new Scatter(map, seeded(), 0f, 0f);

        List<float[]> placed = new ArrayList<>();
        int count = scatter.scatter(area, 6, 0.5f, (x, y) -> placed.add(new float[] {x, y}));

        assertThat(count).isEqualTo(6);
        assertThat(placed)
                .allSatisfy(at -> assertThat(area.contains(map.colAt(at[0]), map.rowAt(at[1])))
                        .isTrue());
    }

    @Test
    void reportsWhatASpawnAreaWasTooSmallToHold() {
        TileMap map = TileMap.builder(20, 20, 5f).fill(Terrain.PLAIN).build();
        Scatter scatter = new Scatter(map, seeded(), 0f, 0f);

        int count = scatter.scatter(TileRect.ofTile(3, 3), 40, 2f, (x, y) -> {});

        assertThat(count).isLessThan(40);
    }
}
