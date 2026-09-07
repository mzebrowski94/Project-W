package pl.mzebrows.pwt.sim.collision;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

/**
 * Broad phase for collision: a uniform grid over the arena that reports pairs close enough to be
 * worth testing properly. It does not decide whether anything overlaps - that is the collision
 * system's job, and keeping the two apart is what lets either change without the other.
 *
 * <p>A dense grid rather than a hash map, because the arena is bounded and small: cells are a flat
 * array indexed by row and column, so there is no hashing and therefore nothing whose iteration
 * order could vary between runs or JVMs. Determinism here is structural rather than something to
 * remember.
 *
 * <p>Entities are inserted by their centre only. That is sound as long as the cell is at least as
 * wide as the largest possible overlap distance - two bodies can only touch if their centres are
 * within the sum of their radii, so a cell of twice the largest radius guarantees any touching pair
 * shares a cell or sits in adjacent ones. {@link Simulation} sizes the cells from the config for
 * exactly this reason; a caller that picks its own cell size owns that constraint.
 *
 * <p>Allocates once, in the constructor. {@link #clear()} empties the cells without releasing their
 * backing arrays, so a tick costs no allocation at all.
 */
public final class SpatialGrid {

    /** Receives each candidate pair. Implemented by the collision system itself, so nothing is captured. */
    @FunctionalInterface
    public interface PairVisitor {
        void visit(Entity a, Entity b);
    }

    /**
     * Column and row offsets of the four neighbours checked from each cell: east, north-west, north
     * and north-east. Half the ring, so every neighbouring pair of cells is visited exactly once
     * rather than twice from opposite ends.
     */
    private static final int[] FORWARD_NEIGHBOURS = {1, 0, -1, 1, 0, 1, 1, 1};

    private final int columns;
    private final int rows;
    private final float cellSize;
    private final Array<Array<Entity>> cells;

    /** Builds a grid covering {@code width} by {@code height} world units in square cells. */
    public SpatialGrid(float width, float height, float cellSize) {
        if (cellSize <= 0f) {
            throw new IllegalArgumentException("cellSize must be positive, was " + cellSize);
        }
        if (width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("grid size must be positive, was " + width + "x" + height);
        }
        this.cellSize = cellSize;
        this.columns = Math.max(1, (int) Math.ceil(width / cellSize));
        this.rows = Math.max(1, (int) Math.ceil(height / cellSize));
        this.cells = new Array<>(true, columns * rows);
        for (int i = 0; i < columns * rows; i++) {
            cells.add(new Array<>());
        }
    }

    /** Empties every cell, keeping the memory. Call once per tick before re-inserting. */
    public void clear() {
        for (int i = 0; i < cells.size; i++) {
            cells.get(i).clear();
        }
    }

    /** Files an entity under the cell containing its centre. Positions outside land in edge cells. */
    public void insert(Entity entity, float x, float y) {
        cells.get(indexOf(x, y)).add(entity);
    }

    /** Visits every pair of entities whose cells are the same or adjacent. */
    public void forEachPair(PairVisitor visitor) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                Array<Entity> cell = cells.get(row * columns + column);
                if (cell.size == 0) {
                    continue;
                }
                visitWithin(cell, visitor);
                visitNeighbours(cell, column, row, visitor);
            }
        }
    }

    private static void visitWithin(Array<Entity> cell, PairVisitor visitor) {
        for (int i = 0; i < cell.size; i++) {
            for (int j = i + 1; j < cell.size; j++) {
                visitor.visit(cell.get(i), cell.get(j));
            }
        }
    }

    private void visitNeighbours(Array<Entity> cell, int column, int row, PairVisitor visitor) {
        for (int offset = 0; offset < FORWARD_NEIGHBOURS.length; offset += 2) {
            int neighbourColumn = column + FORWARD_NEIGHBOURS[offset];
            int neighbourRow = row + FORWARD_NEIGHBOURS[offset + 1];
            if (neighbourColumn < 0 || neighbourColumn >= columns || neighbourRow < 0 || neighbourRow >= rows) {
                continue;
            }
            Array<Entity> neighbour = cells.get(neighbourRow * columns + neighbourColumn);
            for (int i = 0; i < cell.size; i++) {
                for (int j = 0; j < neighbour.size; j++) {
                    visitor.visit(cell.get(i), neighbour.get(j));
                }
            }
        }
    }

    private int indexOf(float x, float y) {
        int column = clamp((int) Math.floor(x / cellSize), columns);
        int row = clamp((int) Math.floor(y / cellSize), rows);
        return row * columns + column;
    }

    private static int clamp(int value, int bound) {
        if (value < 0) {
            return 0;
        }
        return value >= bound ? bound - 1 : value;
    }
}
