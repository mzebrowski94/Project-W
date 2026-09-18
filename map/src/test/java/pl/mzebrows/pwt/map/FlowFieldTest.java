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

    /**
     * The reason this is public at all: a squad choosing between two objectives must compare routes,
     * not straight lines. The near point here is behind a wall and the far one is not.
     */
    @Test
    void countsTheWalkRatherThanTheStraightLine() {
        TileMap map = TileMap.builder(20, 20, 5f)
                .fill(Terrain.PLAIN)
                .rect(5, 0, 1, 18, Terrain.ROCK)
                .build();
        FlowField field = FlowField.over(map, 1f);
        field.rebuildFor(2, 2);

        int throughTheWall = field.stepsTo(8, 2);
        int roundTheOpenSide = field.stepsTo(2, 12);

        assertThat(throughTheWall).isGreaterThan(roundTheOpenSide);
    }

    @Test
    void reportsNoRouteToSomewhereItCannotReach() {
        TileMap map = TileMap.builder(20, 20, 5f)
                .fill(Terrain.PLAIN)
                .rect(5, 0, 1, 20, Terrain.ROCK)
                .build();
        FlowField field = FlowField.over(map, 1f);
        field.rebuildFor(2, 2);

        assertThat(field.stepsTo(8, 2)).isNegative();
    }

    @Test
    void countsNoStepsFromTheGoalItself() {
        TileMap map = TileMap.builder(20, 20, 5f).fill(Terrain.PLAIN).build();
        FlowField field = FlowField.over(map, 1f);
        field.rebuildFor(4, 4);

        assertThat(field.stepsTo(4, 4)).isZero();
    }

    /**
     * Several goals at once, which is what a side with more than one man on it needs. A multi-source
     * flood costs what a single-source one costs, so this is the cheap answer rather than the clever
     * one - the alternative is a field per man, per tick.
     */
    @Test
    void leadsToWhicheverGoalIsNearestByRoute() {
        TileMap map = Maps.uniform(21, 5, 1f, Terrain.PLAIN);
        FlowField field = FlowField.over(map, 0.2f);

        field.rebuildFor(new int[] {0, 20}, new int[] {2, 2}, 2);

        assertThat(field.stepsTo(1, 2)).isEqualTo(1);
        assertThat(field.stepsTo(19, 2)).isEqualTo(1);
        assertThat(field.stepsTo(10, 2)).isEqualTo(10);
    }

    /** And a man standing on one of them is standing on the goal, whichever of them it is. */
    @Test
    void countsEveryGoalAsArrived() {
        TileMap map = Maps.uniform(21, 5, 1f, Terrain.PLAIN);
        FlowField field = FlowField.over(map, 0.2f);

        field.rebuildFor(new int[] {0, 20}, new int[] {2, 2}, 2);

        assertThat(field.stepsTo(0, 2)).isZero();
        assertThat(field.stepsTo(20, 2)).isZero();
    }

    /** No goals at all is a field that reaches nothing, rather than one pointing at a corner. */
    @Test
    void reachesNothingWhenThereIsNobodyToWalkTo() {
        TileMap map = Maps.uniform(9, 5, 1f, Terrain.PLAIN);
        FlowField field = FlowField.over(map, 0.2f);

        field.rebuildFor(new int[0], new int[0], 0);

        assertThat(field.reaches(4, 2)).isFalse();
    }

    /** Handing over the same goals again is a field that was already right, and no second grid pass. */
    @Test
    void doesNoWorkWhenTheGoalsHaveNotMoved() {
        TileMap map = Maps.uniform(9, 5, 1f, Terrain.PLAIN);
        FlowField field = FlowField.over(map, 0.2f);
        field.rebuildFor(new int[] {1, 7}, new int[] {2, 2}, 2);

        assertThat(field.rebuildFor(new int[] {1, 7}, new int[] {2, 2}, 2)).isFalse();
        assertThat(field.rebuildFor(new int[] {1, 6}, new int[] {2, 2}, 2)).isTrue();
    }
}
