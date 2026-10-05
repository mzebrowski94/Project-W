package pl.mzebrows.pwt.sim.collision;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;

/**
 * Decides where everything is allowed to be, after movement has said where it would like to be.
 *
 * <p>Three constraints, applied together over a few passes per tick: bodies may not overlap, movable
 * solids may not stand in impassable terrain, and may not leave the arena. <strong>Terrain is a clamp,
 * not a body</strong> (G0009, ADR 0007): a circle spans at most four tiles, so no broad phase.
 *
 * <p>Purely geometric: it reads {@link Collider} flags, separates pairs of solids, reports contacts,
 * and writes only movable bodies. What a contact means is the caller's.
 *
 * <p><strong>The constraints are not equal.</strong> Arena and terrain are applied after separation on
 * every pass, so they hold; separation is the one that gives under crowd pressure. More passes reduce
 * that squeeze and never remove it; the fix, if ever needed, is a different model.
 */
public final class ConstraintSolver implements SpatialGrid.PairVisitor {

    /** Notified once per overlapping pair per tick, on the first pass only. */
    @FunctionalInterface
    public interface ContactListener {
        void onContact(Entity a, Entity b);
    }

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);

    /** Two centres closer than this are treated as coincident, and pushed apart along a fixed axis. */
    private static final float EPSILON = 1e-4f;

    private final SpatialGrid grid;
    private final ContactListener listener;
    private final TileMap map;

    /** Whether a tile is solid now: terrain and shut doorways (ADR 0022). The clamp asks it, never the map. */
    private final Solid solid;

    private final float width;
    private final float height;
    private final int passes;

    /** The widest body the grid's cells were sized for; anything wider could miss a contact. */
    private final float largestRadius;

    /** Set for the duration of a pass, so contacts are announced once rather than once per pass. */
    private boolean reportContacts;

    public ConstraintSolver(SimConfig config, Solid solid, ContactListener listener) {
        this.listener = listener;
        this.solid = solid;
        this.map = solid.map();
        this.width = map.worldWidth();
        this.height = map.worldHeight();
        this.passes = config.collision().passes();
        this.largestRadius = config.largestRadius();
        this.grid = new SpatialGrid(width, height, largestRadius * 2f);
    }

    /** Resolves every constraint for this tick. The entity view must have position, body and collider. */
    public void solve(ImmutableArray<Entity> bodies) {
        grid.clear();
        for (int i = 0; i < bodies.size(); i++) {
            Entity entity = bodies.get(i);
            if (!COLLIDER.get(entity).present) {
                // Not on the field - a man inside a gun. Never inserted.
                continue;
            }
            Collider collider = COLLIDER.get(entity);
            Position position = POSITION.get(entity);
            float atX = position.x;
            float atY = position.y;
            float radius = BODY.get(entity).radius;
            if (collider.swept && PREVIOUS.has(entity)) {
                // Filed at the middle of the way it came, and as wide as half of it: whatever it can
                // have touched along the way is then within the reach the grid guarantees.
                PreviousPosition from = PREVIOUS.get(entity);
                atX = (from.x + position.x) * 0.5f;
                atY = (from.y + position.y) * 0.5f;
                float halfX = position.x - atX;
                float halfY = position.y - atY;
                radius += (float) Math.sqrt(halfX * halfX + halfY * halfY);
            }
            if (radius > largestRadius) {
                // Loud, because the alternative is silent: pairs a cell apart are never reported, and
                // units walk through the body with nothing failing. The fix is a line in largestRadius.
                throw new IllegalStateException("A body of radius " + radius
                        + " is wider than SimConfig.largestRadius() (" + largestRadius + ") sized the grid for");
            }
            grid.insert(entity, atX, atY);
        }
        for (int pass = 0; pass < passes; pass++) {
            reportContacts = pass == 0;
            grid.forEachPair(this);
            constrainMovableSolids(bodies);
        }
    }

    @Override
    public void visit(Entity a, Entity b) {
        if (a.isScheduledForRemoval() || b.isScheduledForRemoval()) {
            return;
        }
        Collider firstCollider = COLLIDER.get(a);
        Collider secondCollider = COLLIDER.get(b);
        if (firstCollider.swept || secondCollider.swept) {
            touchAlongThePath(a, b, firstCollider.swept);
            return;
        }
        Position first = POSITION.get(a);
        Position second = POSITION.get(b);
        float dx = second.x - first.x;
        float dy = second.y - first.y;
        float contact = BODY.get(a).radius + BODY.get(b).radius;
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared >= contact * contact) {
            return;
        }
        if (reportContacts) {
            listener.onContact(a, b);
        }
        if (firstCollider.solid && secondCollider.solid) {
            separate(a, b, firstCollider, secondCollider, dx, dy, (float) Math.sqrt(distanceSquared), contact);
        }
    }

    /**
     * Reports a contact if the swept one came within touching distance of the other anywhere along the
     * way it moved this tick - the closest point of that segment to the other's centre. The other is
     * taken where it stands now: it moves a fraction of what a shot does in a tick.
     *
     * <p>Contact only, and only on the first pass. Nothing swept is solid, so there is never anything
     * to separate, and the passes after the first have nothing to add.
     */
    private void touchAlongThePath(Entity a, Entity b, boolean firstIsSwept) {
        if (!reportContacts) {
            return;
        }
        Entity mover = firstIsSwept ? a : b;
        Entity other = firstIsSwept ? b : a;
        Position to = POSITION.get(mover);
        float fromX = to.x;
        float fromY = to.y;
        if (PREVIOUS.has(mover)) {
            fromX = PREVIOUS.get(mover).x;
            fromY = PREVIOUS.get(mover).y;
        }
        Position centre = POSITION.get(other);
        float pathX = to.x - fromX;
        float pathY = to.y - fromY;
        float length = pathX * pathX + pathY * pathY;
        float along = 1f;
        if (length > EPSILON) {
            along = ((centre.x - fromX) * pathX + (centre.y - fromY) * pathY) / length;
            along = along < 0f ? 0f : (along > 1f ? 1f : along);
        }
        float gapX = centre.x - (fromX + pathX * along);
        float gapY = centre.y - (fromY + pathY * along);
        float contact = BODY.get(a).radius + BODY.get(b).radius;
        if (gapX * gapX + gapY * gapY < contact * contact) {
            listener.onContact(a, b);
        }
    }

    /** Pushes two solid bodies out of each other along the line between their centres. */
    private static void separate(
            Entity a,
            Entity b,
            Collider firstCollider,
            Collider secondCollider,
            float dx,
            float dy,
            float distance,
            float contact) {
        if (!firstCollider.movable && !secondCollider.movable) {
            return;
        }
        float normalX = 1f;
        float normalY = 0f;
        float separation = contact;
        if (distance > EPSILON) {
            normalX = dx / distance;
            normalY = dy / distance;
            separation = contact - distance;
        }
        if (!firstCollider.movable) {
            push(b, normalX * separation, normalY * separation);
        } else if (!secondCollider.movable) {
            push(a, -normalX * separation, -normalY * separation);
        } else {
            float half = separation * 0.5f;
            push(a, -normalX * half, -normalY * half);
            push(b, normalX * half, normalY * half);
        }
    }

    private static void push(Entity entity, float dx, float dy) {
        Position position = POSITION.get(entity);
        position.x += dx;
        position.y += dy;
    }

    /**
     * Keeps solid movable bodies out of rock and inside the arena, in that order. Projectiles are
     * {@code ProjectileBoundsSystem}'s; immovable scenery is never moved.
     */
    private void constrainMovableSolids(ImmutableArray<Entity> bodies) {
        for (int i = 0; i < bodies.size(); i++) {
            Entity entity = bodies.get(i);
            Collider collider = COLLIDER.get(entity);
            if (!collider.present || !collider.solid || !collider.movable) {
                continue;
            }
            Position position = POSITION.get(entity);
            float radius = BODY.get(entity).radius;
            pushOutOfTerrain(position, radius);
            position.x = clamp(position.x, radius, width - radius);
            position.y = clamp(position.y, radius, height - radius);
        }
    }

    /**
     * Pushes a body out of every impassable tile it is touching - at most four of them, because a
     * circle that size cannot span more.
     *
     * <p><strong>Pushes are gathered, then resolved</strong>: applied one tile at a time, opposite pushes
     * in a gap too narrow cancel and the body slides through the wall. An axis with no room is left the
     * way it came in.
     */
    private void pushOutOfTerrain(Position position, float radius) {
        if (map.inBounds(map.colAt(position.x), map.rowAt(position.y)) && !solid.passableAt(position.x, position.y)) {
            ejectFromInside(position, radius);
            return;
        }
        int minCol = map.colAt(position.x - radius);
        int maxCol = map.colAt(position.x + radius);
        int minRow = map.rowAt(position.y - radius);
        int maxRow = map.rowAt(position.y + radius);

        float left = 0f;
        float right = 0f;
        float down = 0f;
        float up = 0f;
        float spanMinX = Float.MAX_VALUE;
        float spanMaxX = -Float.MAX_VALUE;
        float spanMinY = Float.MAX_VALUE;
        float spanMaxY = -Float.MAX_VALUE;
        float tile = map.tileSize();

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (!map.inBounds(col, row) || solid.passableAtTile(col, row)) {
                    continue;
                }
                float tileLeft = col * tile;
                float tileBottom = row * tile;
                float dx = position.x - between(position.x, tileLeft, tileLeft + tile);
                float dy = position.y - between(position.y, tileBottom, tileBottom + tile);
                float distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= radius * radius || distanceSquared <= EPSILON * EPSILON) {
                    continue;
                }
                int stepX = dx > 0f ? 1 : (dx < 0f ? -1 : 0);
                int stepY = dy > 0f ? 1 : (dy < 0f ? -1 : 0);
                if (!exposed(col, row, stepX, stepY)) {
                    continue;
                }
                float distance = (float) Math.sqrt(distanceSquared);
                float scale = (radius - distance) / distance;
                float pushX = dx * scale;
                float pushY = dy * scale;
                right = Math.max(right, pushX);
                left = Math.max(left, -pushX);
                up = Math.max(up, pushY);
                down = Math.max(down, -pushY);
                spanMinX = Math.min(spanMinX, tileLeft);
                spanMaxX = Math.max(spanMaxX, tileLeft + tile);
                spanMinY = Math.min(spanMinY, tileBottom);
                spanMaxY = Math.max(spanMaxY, tileBottom + tile);
            }
        }

        boolean pinchedX = left > 0f && right > 0f;
        boolean pinchedY = down > 0f && up > 0f;
        if (pinchedY && !pinchedX) {
            position.x = position.x - spanMinX < spanMaxX - position.x ? spanMinX - radius : spanMaxX + radius;
            return;
        }
        if (pinchedX && !pinchedY) {
            position.y = position.y - spanMinY < spanMaxY - position.y ? spanMinY - radius : spanMaxY + radius;
            return;
        }
        if (pinchedX) {
            // Squeezed on both axes at once. Nothing here can improve it; the next pass sees whatever
            // the other constraints have done and tries again.
            return;
        }
        position.x += right - left;
        position.y += up - down;
    }

    /**
     * Whether the face or corner we are about to be pushed away from is on the outside of the wall.
     *
     * <p>A face buried in the next tile of the same wall is not a surface, nor a corner beside a solid
     * tile - or a body sliding along a wall catches on every seam.
     */
    private boolean exposed(int col, int row, int stepX, int stepY) {
        if (stepX != 0 && stepY != 0) {
            return solid.passableAtTile(col + stepX, row) && solid.passableAtTile(col, row + stepY);
        }
        return solid.passableAtTile(col + stepX, row + stepY);
    }

    /**
     * The body's centre is inside a tile, so there is no direction to push along - leave by the
     * nearest face that actually opens onto somewhere it can stand. A body walled in on all four sides
     * is left where it is; a later pass frees it once whatever put it there has been resolved.
     *
     * <p>Happens when a crowd drives something into a wall hard enough.
     */
    private void ejectFromInside(Position position, float radius) {
        int col = map.colAt(position.x);
        int row = map.rowAt(position.y);
        float tile = map.tileSize();
        float tileLeft = col * tile;
        float tileBottom = row * tile;
        float tileRight = tileLeft + tile;
        float tileTop = tileBottom + tile;

        float shallowest = Float.MAX_VALUE;
        int face = -1;
        if (solid.passableAtTile(col - 1, row) && position.x - tileLeft < shallowest) {
            shallowest = position.x - tileLeft;
            face = 0;
        }
        if (solid.passableAtTile(col + 1, row) && tileRight - position.x < shallowest) {
            shallowest = tileRight - position.x;
            face = 1;
        }
        if (solid.passableAtTile(col, row - 1) && position.y - tileBottom < shallowest) {
            shallowest = position.y - tileBottom;
            face = 2;
        }
        if (solid.passableAtTile(col, row + 1) && tileTop - position.y < shallowest) {
            face = 3;
        }
        switch (face) {
            case 0 -> position.x = tileLeft - radius;
            case 1 -> position.x = tileRight + radius;
            case 2 -> position.y = tileBottom - radius;
            case 3 -> position.y = tileTop + radius;
            default -> {
                // Walled in on every side. Nothing sensible to do this pass.
            }
        }
    }

    /** Plain clamp for a tile's extent, where the bounds are always the right way round. */
    private static float between(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    /** Guards against an arena narrower than the body in it, where min would exceed max. */
    private static float clamp(float value, float min, float max) {
        if (min >= max) {
            return (min + max) * 0.5f;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
