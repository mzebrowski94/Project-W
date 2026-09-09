package pl.mzebrows.pwt.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class FlowFieldTest {

    private static final int COLS = 9;
    private static final int ROWS = 9;
    private static final float TILE = 5f;

    /** A grunt: narrow enough that a one-tile crack is a way through. */
    private static final float SLIM = 1.8f;

    /** A brute: 5.2 across against a 5-unit tile, so a one-tile crack is a wall. */
    private static final float BROAD = 2.6f;

    private static final int WALL_COL = 4;
    private static final int GAP_ROW = 4;
    private static final int GOAL_COL = 7;
    private static final int FAR_SIDE_COL = 1;

    @Test
    void pointsStraightAtTheGoalOnOpenGround() {
        FlowField field = FlowField.over(open(), SLIM);
        field.rebuildFor(GOAL_COL, GAP_ROW);

        float[] direction = new float[2];

        assertThat(field.directionAt(centre(FAR_SIDE_COL), centre(GAP_ROW), direction))
                .isTrue();
        assertThat(direction[0]).isGreaterThan(0.9f);
        assertThat(direction[1]).isCloseTo(0f, Offset.offset(0.1f));
    }

    @Test
    void leadsAUnitTowardsTheGapRatherThanIntoTheWall() {
        FlowField field = FlowField.over(walled(), SLIM);
        field.rebuildFor(GOAL_COL, GAP_ROW);

        float[] direction = new float[2];

        assertThat(field.directionAt(centre(3), centre(1), direction)).isTrue();
        assertThat(direction[1]).isGreaterThan(0f);
    }

    @Test
    void letsASlimBodyThroughAOneTileGap() {
        FlowField field = FlowField.over(walled(), SLIM);
        field.rebuildFor(GOAL_COL, GAP_ROW);

        assertThat(field.reaches(FAR_SIDE_COL, GAP_ROW)).isTrue();
    }

    @Test
    void refusesAGapNarrowerThanTheBodyItWasBuiltFor() {
        FlowField field = FlowField.over(walled(), BROAD);
        field.rebuildFor(GOAL_COL, GAP_ROW);

        assertThat(field.reaches(FAR_SIDE_COL, GAP_ROW)).isFalse();
    }

    @Test
    void reportsNoDirectionWhereItCannotReachTheGoal() {
        FlowField field = FlowField.over(walled(), BROAD);
        field.rebuildFor(GOAL_COL, GAP_ROW);

        float[] direction = new float[2];

        assertThat(field.directionAt(centre(FAR_SIDE_COL), centre(GAP_ROW), direction))
                .isFalse();
    }

    @Test
    void reportsNoDirectionOnceItIsThere() {
        FlowField field = FlowField.over(open(), SLIM);
        field.rebuildFor(GOAL_COL, GAP_ROW);

        float[] direction = new float[2];

        assertThat(field.directionAt(centre(GOAL_COL), centre(GAP_ROW), direction))
                .isFalse();
    }

    @Test
    void rebuildsOnlyWhenTheGoalMovesToAnotherTile() {
        FlowField field = FlowField.over(open(), SLIM);

        assertThat(field.rebuildFor(GOAL_COL, GAP_ROW)).isTrue();
        assertThat(field.rebuildFor(GOAL_COL, GAP_ROW)).isFalse();
        assertThat(field.rebuildFor(GOAL_COL - 1, GAP_ROW)).isTrue();
    }

    private static TileMap open() {
        return Maps.uniform(COLS, ROWS, TILE, Terrain.PLAIN);
    }

    /** One wall down the middle with a single tile knocked out of it. */
    private static TileMap walled() {
        return TileMap.builder(COLS, ROWS, TILE)
                .fill(Terrain.PLAIN)
                .rect(WALL_COL, 0, 1, ROWS, Terrain.ROCK)
                .set(WALL_COL, GAP_ROW, Terrain.PLAIN)
                .build();
    }

    private static float centre(int tile) {
        return tile * TILE + TILE * 0.5f;
    }
}
