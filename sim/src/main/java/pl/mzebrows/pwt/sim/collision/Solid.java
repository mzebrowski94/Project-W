package pl.mzebrows.pwt.sim.collision;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.Arrays;
import pl.mzebrows.pwt.map.Passability;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;

/**
 * Whether a tile is solid now (ADR 0022): the map's terrain, and the doorway of every shut door. <strong>The one
 * place in {@code sim} that answers it</strong> for a running world - the solver's clamp, a shot flying into rock, a
 * blast's line, perception's clear line and a shooter's choice of target all ask here - so a shut door is a wall to
 * everything that moves, shoots, explodes or looks. Routes do not ask: a flow field is the map's (ADR 0009), and a
 * route runs through a shut door so a hunter walks up to it (G0069).
 *
 * <p>Derived from the doors, never carried: {@link #derive} rebuilds it from every door standing shut, before every
 * tick and whenever a door opens or breaks, so a restored world answers exactly as the one it was taken from. Read
 * inside per-entity loops, so it is one array read on top of the terrain's.
 */
public final class Solid implements Passability {

    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    /** How far past the face a probe for the door behind it reaches, in world units: well inside one tile. */
    private static final float DOOR_PROBE = 1e-2f;

    private final TileMap map;

    /** The handle of the shut door standing in each tile, or nobody - {@code row * cols + col}. */
    private final long[] shut;

    /** The one door {@link #clearLineTo} lets a line into, for the length of one walk. */
    private long excepted = Handle.NOBODY;

    /** This, but for the tiles of {@link #excepted}: built once, so a walk to a door allocates nothing. */
    private final Passability toExcepted;

    public Solid(TileMap map) {
        this.map = map;
        this.shut = new long[map.cols() * map.rows()];
        this.toExcepted = (col, row) -> map.passableAtTile(col, row)
                && (shut[row * map.cols() + col] == Handle.NOBODY || shut[row * map.cols() + col] == excepted);
    }

    /** The ground under it, which never changes: its extent, its tiles and its terrain. */
    public TileMap map() {
        return map;
    }

    /** Rebuilds which tiles a shut door stands in, from every door still standing and not yet opened. */
    public void derive(ImmutableArray<Entity> doors) {
        Arrays.fill(shut, Handle.NOBODY);
        for (int i = 0; i < doors.size(); i++) {
            Entity entity = doors.get(i);
            Door door = DOOR.get(entity);
            if (door.open || DEAD.has(entity)) {
                continue;
            }
            long handle = HANDLE.get(entity).id;
            for (int row = door.row; row < door.row + door.height; row++) {
                for (int col = door.col; col < door.col + door.width; col++) {
                    if (map.inBounds(col, row)) {
                        shut[row * map.cols() + col] = handle;
                    }
                }
            }
        }
    }

    /** Whether something can stand on this tile, and a line pass through it, now. False off the map. */
    @Override
    public boolean passableAtTile(int col, int row) {
        return map.passableAtTile(col, row) && shut[row * map.cols() + col] == Handle.NOBODY;
    }

    /** Whether something can stand at this world position now. */
    public boolean passableAt(float worldX, float worldY) {
        return passableAtTile(map.colAt(worldX), map.rowAt(worldY));
    }

    /** The shut door standing in this tile, by handle, or nobody. */
    public long doorAt(int col, int row) {
        return map.inBounds(col, row) ? shut[row * map.cols() + col] : Handle.NOBODY;
    }

    /** The shut door standing at this world position, by handle, or nobody. */
    public long doorAt(float worldX, float worldY) {
        return doorAt(map.colAt(worldX), map.rowAt(worldY));
    }

    /** Whether an unobstructed straight line runs between two points now: the map's exact walk, over this. */
    public boolean clearLine(float fromX, float fromY, float toX, float toY) {
        return map.clearLine(fromX, fromY, toX, toY, this);
    }

    /** Whether something this big could travel the straight line between two points now, the map's walk over this. */
    public boolean isClearPath(float fromX, float fromY, float toX, float toY, float radius) {
        return map.isClearPath(fromX, fromY, toX, toY, radius, this);
    }

    /**
     * Whether a straight line reaches this shut door now: nothing solid on it but the door's own tiles. What a
     * blast asks of a door it might catch - its line to the door ends at the door (ADR 0022).
     */
    public boolean clearLineTo(long door, float fromX, float fromY, float toX, float toY) {
        excepted = door;
        boolean clear = map.clearLine(fromX, fromY, toX, toY, toExcepted);
        excepted = Handle.NOBODY;
        return clear;
    }

    /**
     * The shut door that is the first solid thing on the way from a point along a unit direction, within this
     * distance, or nobody - nobody too when rock comes first. <strong>The one test of a way shut by a
     * door</strong> (G0069): a trooper whose way it is opens it, and a hunter whose way it is leaps at it.
     */
    public long doorAhead(float fromX, float fromY, float directionX, float directionY, float distance) {
        float toX = fromX + directionX * distance;
        float toY = fromY + directionY * distance;
        float open = map.passableFraction(fromX, fromY, toX, toY, this);
        if (open >= 1f) {
            return Handle.NOBODY;
        }
        // A hair past where the line met it, so the point is in the tile that stopped it.
        float into = distance * open + DOOR_PROBE;
        return doorAt(fromX + directionX * into, fromY + directionY * into);
    }

    /** How much of the line runs over open ground before it first meets something solid now, as a fraction. */
    public float passableFraction(float fromX, float fromY, float toX, float toY) {
        return map.passableFraction(fromX, fromY, toX, toY, this);
    }
}
