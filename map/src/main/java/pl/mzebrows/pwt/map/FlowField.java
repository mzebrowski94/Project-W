package pl.mzebrows.pwt.map;

/**
 * Which way to walk, from anywhere on the map, to reach one goal - for a body of one particular size.
 *
 * <p><strong>A flow field rather than a path each.</strong> Many units converging on one target is
 * exactly what this is for: one pass over the grid costs the same whether one unit reads it or two
 * hundred, and every unit reads a direction out of its own tile. A search per unit costs a search per
 * unit and produces a queue walking the same line in single file. The {@code game-patterns} skill
 * names this as the standard answer here.
 *
 * <p><strong>Clearance is baked in, not checked afterwards.</strong> A field is built for a body
 * radius and a tile only counts as ground if a circle that size centred on it fits. That is what
 * makes a one-tile crack a route for a grunt and a wall for a brute - the tactical rule falls out of
 * the geometry instead of being a rule somebody has to remember to write - and it is why there is a
 * field per body size rather than one shared field with a filter over it.
 *
 * <p>The goal tile is seeded whether or not a body that size fits in it. A brute therefore walks to
 * the mouth of a crack the commander has backed into rather than losing him entirely, which is the
 * behaviour that makes the crack read as cover rather than as a bug.
 *
 * <p><strong>It is pure geometry and lives here rather than in a system.</strong> Nav data is a
 * function of terrain and a goal; who follows it, and how fast, is gameplay. It reads no clock, no
 * entity and no randomness, so it is testable on a hand-built map with no simulation at all.
 *
 * <p>Directions are the gradient of the distance field and are sampled with a bilinear blend between
 * the four nearest tile centres, so following one is a smooth curve rather than a staircase - nothing
 * in this game is quantised to the grid, and a unit stepping tile-by-tile would be.
 */
public final class FlowField {

    /** Not reached by the last rebuild - either unreachable, or too tight for this body. */
    private static final int UNREACHED = -1;

    private static final float EPSILON = 1e-4f;

    private final TileMap map;
    private final float bodyRadius;
    private final int cols;
    private final int rows;

    /** Whether a body of this size fits on each tile. A property of the map, so computed once. */
    private final boolean[] fits;

    private final int[] distance;
    private final float[] directionX;
    private final float[] directionY;
    private final int[] queue;

    private boolean built;
    private int goalCol;
    private int goalRow;

    private FlowField(TileMap map, float bodyRadius) {
        this.map = map;
        this.bodyRadius = bodyRadius;
        this.cols = map.cols();
        this.rows = map.rows();
        int size = cols * rows;
        this.fits = new boolean[size];
        this.distance = new int[size];
        this.directionX = new float[size];
        this.directionY = new float[size];
        this.queue = new int[size];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                fits[row * cols + col] = map.isClear(map.tileCentreX(col), map.tileCentreY(row), bodyRadius);
            }
        }
    }

    /** A field over this map for a body of this radius. Allocates once; rebuilding allocates nothing. */
    public static FlowField over(TileMap map, float bodyRadius) {
        return new FlowField(map, bodyRadius);
    }

    /** The body radius this field was built for, in world units. */
    public float bodyRadius() {
        return bodyRadius;
    }

    /**
     * Points the whole field at a tile, and reports whether it had to do any work.
     *
     * <p>Rebuilding when the goal has not left its tile would be a grid pass a tick for a field that
     * cannot have changed. A goal that has merely moved within one tile is the same goal.
     */
    public boolean rebuildFor(int newGoalCol, int newGoalRow) {
        if (built && newGoalCol == goalCol && newGoalRow == goalRow) {
            return false;
        }
        goalCol = newGoalCol;
        goalRow = newGoalRow;
        built = true;
        flood();
        gradient();
        return true;
    }

    /** Whether a body of this size can get from this tile to the goal at all. */
    public boolean reaches(int col, int row) {
        return map.inBounds(col, row) && distance[row * cols + col] != UNREACHED;
    }

    /**
     * Writes the unit direction to walk from this world point into {@code out}, and reports whether
     * there was one.
     *
     * <p>False means there is nothing useful to say: the goal cannot be reached from here, or the
     * point is close enough that the field has flattened out. The caller decides what to do about it -
     * a unit already at the goal wants the last of the approach steered straight at it, not by a grid.
     *
     * <p>{@code out} is the caller's two-element scratch array. A field cannot own one without
     * becoming something two systems cannot call in the same tick, and returning a vector would
     * allocate once per unit per tick.
     */
    public boolean directionAt(float worldX, float worldY, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        if (!built) {
            return false;
        }
        float inTilesX = worldX / map.tileSize() - 0.5f;
        float inTilesY = worldY / map.tileSize() - 0.5f;
        int col = (int) Math.floor(inTilesX);
        int row = (int) Math.floor(inTilesY);
        float acrossX = inTilesX - col;
        float acrossY = inTilesY - row;
        float sumX = 0f;
        float sumY = 0f;
        float weightSum = 0f;
        for (int rowStep = 0; rowStep <= 1; rowStep++) {
            for (int colStep = 0; colStep <= 1; colStep++) {
                float weight = (colStep == 0 ? 1f - acrossX : acrossX) * (rowStep == 0 ? 1f - acrossY : acrossY);
                if (weight <= 0f || !reaches(col + colStep, row + rowStep)) {
                    continue;
                }
                int at = (row + rowStep) * cols + col + colStep;
                sumX += directionX[at] * weight;
                sumY += directionY[at] * weight;
                weightSum += weight;
            }
        }
        if (weightSum <= 0f) {
            return false;
        }
        float length = (float) Math.sqrt(sumX * sumX + sumY * sumY);
        if (length <= EPSILON) {
            return false;
        }
        out[0] = sumX / length;
        out[1] = sumY / length;
        return true;
    }

    /**
     * Breadth-first from the goal outwards, four-connected.
     *
     * <p>Four rather than eight for the same reason {@code Maps.reachableFrom} is: a unit has width,
     * so a diagonal pinch between two rock corners is not a way through, and counting it as one hands
     * every unit a route it cannot walk.
     */
    private void flood() {
        for (int i = 0; i < distance.length; i++) {
            distance[i] = UNREACHED;
        }
        if (!map.inBounds(goalCol, goalRow)) {
            return;
        }
        int head = 0;
        int tail = 0;
        int start = goalRow * cols + goalCol;
        distance[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int at = queue[head++];
            int atCol = at % cols;
            int atRow = at / cols;
            int next = distance[at] + 1;
            tail = visit(atCol - 1, atRow, next, tail);
            tail = visit(atCol + 1, atRow, next, tail);
            tail = visit(atCol, atRow - 1, next, tail);
            tail = visit(atCol, atRow + 1, next, tail);
        }
    }

    private int visit(int col, int row, int next, int tail) {
        if (!map.inBounds(col, row)) {
            return tail;
        }
        int at = row * cols + col;
        if (!fits[at] || distance[at] != UNREACHED) {
            return tail;
        }
        distance[at] = next;
        queue[tail] = at;
        return tail + 1;
    }

    /**
     * Turns distances into directions: downhill, as a unit vector per tile.
     *
     * <p>A neighbour that cannot be walked to counts as one step further away than the tile itself, so
     * it pushes rather than pulls and a unit hugging a wall is eased off it instead of sliding along
     * it. The goal tile keeps a zero direction, which is what makes {@code directionAt} report having
     * nothing to say once a unit is on top of what it was chasing.
     */
    private void gradient() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int at = row * cols + col;
                directionX[at] = 0f;
                directionY[at] = 0f;
                int here = distance[at];
                if (here <= 0) {
                    continue;
                }
                float gradientX = uphill(col - 1, row, here) - uphill(col + 1, row, here);
                float gradientY = uphill(col, row - 1, here) - uphill(col, row + 1, here);
                float length = (float) Math.sqrt(gradientX * gradientX + gradientY * gradientY);
                if (length <= EPSILON) {
                    continue;
                }
                directionX[at] = gradientX / length;
                directionY[at] = gradientY / length;
            }
        }
    }

    /** A neighbour's distance, with anything unwalkable reading as one step worse than where we are. */
    private int uphill(int col, int row, int here) {
        if (!map.inBounds(col, row)) {
            return here + 1;
        }
        int neighbour = distance[row * cols + col];
        return neighbour == UNREACHED ? here + 1 : neighbour;
    }
}
