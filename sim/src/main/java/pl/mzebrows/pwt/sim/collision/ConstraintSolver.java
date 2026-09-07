package pl.mzebrows.pwt.sim.collision;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Decides where everything is allowed to be, after movement has said where it would like to be.
 *
 * <p>Two constraints, applied together: bodies may not overlap, and movable solids may not leave the
 * arena. Applying them in sequence once is not enough - pushing a unit out of an obstacle can shove
 * it through a wall, and clamping it back puts it inside the obstacle again. So the pair is applied
 * repeatedly, a few passes per tick, and the result satisfies both. This is why a unit can no longer
 * be wedged between an obstacle and the arena edge.
 *
 * <p>Purely geometric. It reads {@link Collider} flags and never asks what an entity <em>is</em>: a
 * pair of solids is separated, a pair where either is non-solid only reports a contact, and only
 * movable bodies are ever written to. What a contact <em>means</em> - damage, a pickup, a trigger -
 * is the caller's business.
 *
 * <p><strong>The two constraints are not equal.</strong> Arena containment is applied last on every
 * pass and is therefore absolute: nothing ever ends a tick outside the arena. Staying out of a solid
 * body is best-effort, because a crowd driven into a dead end is an unsatisfiable set of constraints
 * and something has to give. Positional separation of this kind always resolves that by letting
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
    private final float width;
    private final float height;
    private final int passes;

    /** Set for the duration of a pass, so contacts are announced once rather than once per pass. */
    private boolean reportContacts;

    public ConstraintSolver(SimConfig config, ContactListener listener) {
        this.listener = listener;
        this.width = config.arena().width();
        this.height = config.arena().height();
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
            containMovableSolids(bodies);
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
     * Keeps solid movable bodies inside the arena. Non-solid ones - projectiles - are deliberately
     * left alone so they can leave and be despawned.
     */
    private void containMovableSolids(ImmutableArray<Entity> bodies) {
        for (int i = 0; i < bodies.size(); i++) {
            Entity entity = bodies.get(i);
            Collider collider = COLLIDER.get(entity);
            if (!collider.solid || !collider.movable) {
                continue;
            }
            Position position = POSITION.get(entity);
            float radius = BODY.get(entity).radius;
            position.x = clamp(position.x, radius, width - radius);
            position.y = clamp(position.y, radius, height - radius);
        }
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
