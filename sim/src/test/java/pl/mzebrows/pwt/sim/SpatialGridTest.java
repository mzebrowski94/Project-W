package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;

/**
 * The grid is a broad phase: it reports pairs close enough to be <em>worth</em> testing, not pairs
 * that actually overlap. Asserting anything narrower here would duplicate the collision system and
 * make the grid harder to change.
 *
 * <p>Entities are labelled through Ashley's {@code flags} field so a reported pair can be named in
 * an assertion.
 */
class SpatialGridTest {

    private static final float ARENA = 100f;
    private static final float CELL = 10f;

    @Test
    void reportsAPairSharingACell() {
        SpatialGrid grid = grid();
        insert(grid, 1, 5f, 5f);
        insert(grid, 2, 6f, 6f);

        assertThat(pairs(grid)).containsExactly("1:2");
    }

    @Test
    void reportsAPairSplitAcrossACellBoundary() {
        SpatialGrid grid = grid();
        insert(grid, 1, 9.5f, 5f);
        insert(grid, 2, 10.5f, 5f);

        assertThat(pairs(grid)).containsExactly("1:2");
    }

    @Test
    void reportsAPairSplitDiagonallyAcrossACorner() {
        SpatialGrid grid = grid();
        insert(grid, 1, 9.5f, 9.5f);
        insert(grid, 2, 10.5f, 10.5f);

        assertThat(pairs(grid)).containsExactly("1:2");
    }

    @Test
    void reportsEachPairExactlyOnce() {
        SpatialGrid grid = grid();
        insert(grid, 1, 9.5f, 9.5f);
        insert(grid, 2, 10.5f, 10.5f);
        insert(grid, 3, 10.5f, 9.5f);

        assertThat(pairs(grid)).containsExactlyInAnyOrder("1:2", "1:3", "2:3");
    }

    @Test
    void ignoresEntitiesMoreThanACellApart() {
        SpatialGrid grid = grid();
        insert(grid, 1, 5f, 5f);
        insert(grid, 2, 85f, 85f);

        assertThat(pairs(grid)).isEmpty();
    }

    @Test
    void forgetsEverythingWhenCleared() {
        SpatialGrid grid = grid();
        insert(grid, 1, 5f, 5f);
        insert(grid, 2, 6f, 6f);

        grid.clear();

        assertThat(pairs(grid)).isEmpty();
    }

    @Test
    void keepsEntitiesOutsideTheArenaInTheEdgeCells() {
        SpatialGrid grid = grid();
        insert(grid, 1, -40f, 5f);
        insert(grid, 2, 2f, 5f);

        assertThat(pairs(grid)).containsExactly("1:2");
    }

    @Test
    void reportsNothingForASingleEntity() {
        SpatialGrid grid = grid();
        insert(grid, 1, 5f, 5f);

        assertThat(pairs(grid)).isEmpty();
    }

    private static SpatialGrid grid() {
        return new SpatialGrid(ARENA, ARENA, CELL);
    }

    private static void insert(SpatialGrid grid, int label, float x, float y) {
        Entity entity = new Entity();
        entity.flags = label;
        grid.insert(entity, x, y);
    }

    private static List<String> pairs(SpatialGrid grid) {
        List<String> found = new ArrayList<>();
        grid.forEachPair((a, b) -> found.add(Math.min(a.flags, b.flags) + ":" + Math.max(a.flags, b.flags)));
        return found;
    }
}
