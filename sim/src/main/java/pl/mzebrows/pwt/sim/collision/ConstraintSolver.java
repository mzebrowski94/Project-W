package pl.mzebrows.pwt.sim.collision;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Decides where everything is allowed to be, after movement has said where it would like to be.
 *
 * <p>Three constraints, applied together: bodies may not overlap, movable solids may not stand in
 * impassable terrain, and movable solids may not leave the arena. Applying them in sequence once is
 * not enough - pushing a unit out of an obstacle can shove it through a wall, and clamping it back
 * puts it inside the obstacle again - so the set is applied repeatedly, a few passes per tick, and
 * the result satisfies all three.
 *
 * <p><strong>Terrain is a clamp, not a body.</strong> Impassable ground never enters the pairwise
 * path and is never an entity (G0009); it constrains a position exactly as the arena boundary already
 * does, which is why circles-only collision (ADR 0007) is untouched by it. The grid makes the lookup
 * free: a circle spans at most four tiles, so there is no broad phase and nothing to iterate.
 *
 * <p>Purely geometric. It reads {@link Collider} flags and never asks what an entity <em>is</em>: a
 * pair of solids is separated, a pair where either is non-solid only reports a contact, and only
 * movable bodies are ever written to. What a contact <em>means</em> - damage, a pickup, a trigger -
 * is the caller's business.
 *
 * <p><strong>The three constraints are not equal.</strong> Arena containment is applied last on every
 * pass and is therefore absolute: nothing ever ends a tick outside the arena. Terrain sits in the same
 * tier, applied on every pass after separation, so a wall holds under crowd pressure - you would far
 * rather watch two units overlap than watch one squeeze through a rock face. Staying out of a solid
 * body is the one that gives, because a crowd driven into a dead end is an unsatisfiable set of
 * constraints and something has to. Positional separation of this kind always resolves that by letting
 * bodies overlap, and raising the pass count reduces the squeeze without ever removing it. If deep
 * penetration under crowd pressure ever becomes a real problem, the fix is a different model -
 * impulses, or a mass hierarchy where an immovable body cannot be penetrated - not more passes.
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

    /** Two centres closer than this are treated as coincident, and pushed apart along a fixed axis. */
    private static final float EPSILON = 1e-4f;

    private final SpatialGrid grid;
    private final ContactListener listener;
    private final TileMap map;
    private final float width;
    private final float height;
    private final int passes;

    /** Set for the duration of a pass, so contacts are announced once rather than once per pass. */
    private boolean reportContacts;

    public ConstraintSolver(SimConfig config, TileMap map, ContactListener listener) {
        this.listener = listener;
        this.map = map;
        this.width = map.worldWidth();
        this.height = map.worldHeight();
        this.passes = config.collision().passes();
        this.grid = new SpatialGrid(width, height, config.largestRadius() * 2f);
    }

    /** Resolves every constraint for this tick. The entity view must have position, body and collider. */
    public void solve(ImmutableArray<Entity> bodies) {
        grid.clear();
        for (int i = 0; i < bodies.size(); i++) {
            Entity entity = bodies.get(i);
            Position position = POSITION.get(entity);
            grid.insert(entity, position.x, position.y);
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
        Collider firstCollider = COLLIDER.get(a);
        Collider secondCollider = COLLIDER.get(b);
        if (firstCollider.solid && secondCollider.solid) {
            separate(a, b, firstCollider, secondCollider, dx, dy, (float) Math.sqrt(distanceSquared), contact);
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
     * Keeps solid movable bodies out of rock and inside the arena, in that order, so the boundary has
     * the last word. Non-solid ones - projectiles - are deliberately left alone: a projectile leaves
     * the arena and flies into a wall rather than stopping at either, and {@code ProjectileBoundsSystem}
     * is what despawns it. An immovable body is left alone too, so terrain never rearranges the
     * scenery a level put down.
     */
    private void constrainMovableSolids(ImmutableArray<Entity> bodies) {
        for (int i = 0; i < bodies.size(); i++) {
            Entity entity = bodies.get(i);
            Collider collider = COLLIDER.get(entity);
            if (!collider.solid || !collider.movable) {
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
     * <p><strong>Pushes are gathered and then resolved, never applied one tile at a time.</strong>
     * Applying each independently looks correct and is not: a body squeezed into a gap narrower than
     * itself gets an equal push from the rock above and the rock below, the two cancel exactly, and it
     * slides through a wall it does not fit through while overlapping both sides. Gathering them makes
     * that case visible as what it is - an axis with no room on it - and the answer is to leave the way
     * it came in, which is the nearer end of the tiles doing the squeezing.
     */
    private void pushOutOfTerrain(Position position, float radius) {
        if (map.inBounds(map.colAt(position.x), map.rowAt(position.y)) && !map.passableAt(position.x, position.y)) {
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
                if (!map.inBounds(col, row) || map.passableAtTile(col, row)) {
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
     * <p>Without this, a wall made of tiles behaves like a row of separate blocks: a body sliding
     * along it is resolved against each tile independently, and the tile it has half left pushes it
     * sideways off its own corner - which the player feels as catching on every seam. The fix is that
     * a face buried in the next tile of the same wall is not a surface, and neither is a corner with a
     * solid tile beside it. Anything outside the map reads as impassable, so the arena edge is
     * correctly never treated as an exposed face either.
     */
    private boolean exposed(int col, int row, int stepX, int stepY) {
        if (stepX != 0 && stepY != 0) {
            return map.passableAtTile(col + stepX, row) && map.passableAtTile(col, row + stepY);
        }
        return map.passableAtTile(col + stepX, row + stepY);
    }

    /**
     * The body's centre is inside a tile, so there is no direction to push along - leave by the
     * nearest face that actually opens onto somewhere it can stand. A body walled in on all four sides
     * is left where it is; a later pass frees it once whatever put it there has been resolved.
     *
     * <p>This should not happen in play. It happens when a crowd drives something into a wall hard
     * enough, and it is the difference between recovering and staying stuck inside the rock.
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
        if (map.passableAtTile(col - 1, row) && position.x - tileLeft < shallowest) {
            shallowest = position.x - tileLeft;
            face = 0;
        }
        if (map.passableAtTile(col + 1, row) && tileRight - position.x < shallowest) {
            shallowest = tileRight - position.x;
            face = 1;
        }
        if (map.passableAtTile(col, row - 1) && position.y - tileBottom < shallowest) {
            shallowest = position.y - tileBottom;
            face = 2;
        }
        if (map.passableAtTile(col, row + 1) && tileTop - position.y < shallowest) {
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
