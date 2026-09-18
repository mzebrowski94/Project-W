package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Hauling something along behind you.
 *
 * <p>It takes hold, lets go, decides the grip has been lost, and drives the load. What it does
 * <strong>not</strong> do is touch the commander's velocity: it publishes which way the load lies
 * and {@code CommanderControlSystem} - the one system that owns his velocity - does the constraining.
 * Two systems writing one velocity is how a unit ends up travelling in a direction neither asked for.
 *
 * <p>The load is moved by writing its velocity, never its position. Placing it would put it through
 * rock, and a man dragged through a wall is the kind of thing that makes every other rule look
 * negotiable. Written velocity plus the constraint solver means a load that snags is a load that
 * stops - and a grip stretched past what an arm could hold is dropped, which is what turns a corner
 * of rock into something the player has to walk around rather than through.
 *
 * <p><strong>What may be grabbed is "has nothing else driving it".</strong> A man who is down
 * qualifies because he is down; a barrel qualifies because it never had a will of its own. A trooper
 * on his feet carries the same component and cannot be taken. That is one rule rather than a list of
 * kinds, which is what keeps the next draggable thing from being a branch in here (G0008).
 *
 * <p>It runs before {@code CommanderControlSystem}, so the constraint that system applies is about
 * the grip as it is this tick rather than as it was last tick.
 */
public final class DragSystem extends EntitySystem {

    private static final ComponentMapper<Drag> DRAG = ComponentMapper.getFor(Drag.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);

    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);

    private static final float EPSILON = 1e-4f;

    private ImmutableArray<Entity> draggers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> loads = new ImmutableArray<>(new Array<>());

    /** Issued rather than derived, so an id is never reused and a stale one can never match. */
    private long nextDragId = 1L;

    @Override
    public void addedToEngine(Engine engine) {
        draggers = engine.getEntitiesFor(
                Family.all(Commander.class, Drag.class, Intent.class, Position.class, Body.class, Action.class)
                        .get());
        loads = engine.getEntitiesFor(
                Family.all(Dragged.class, Position.class, Body.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < draggers.size(); i++) {
            Entity dragger = draggers.get(i);
            Drag drag = DRAG.get(dragger);
            boolean held = INTENT.get(dragger).dragging;
            boolean pressed = held && !drag.wasHeld;
            drag.wasHeld = held;
            if (Drivers.held(dragger)) {
                letGo(drag);
                continue;
            }
            if (pressed) {
                if (drag.active()) {
                    letGo(drag);
                } else {
                    takeHold(dragger, drag);
                }
            }
            haul(dragger, drag, deltaTime);
        }
    }

    /** Grabs whatever is nearest and inert within arm's length, or nothing. */
    private void takeHold(Entity dragger, Drag drag) {
        Entity load = nearestGrabbable(dragger, ACTION.get(dragger).reach);
        if (load == null) {
            return;
        }
        drag.load = nextDragId;
        boolean movable = !COLLIDER.has(load) || COLLIDER.get(load).movable;
        DRAGGED.get(load).take(nextDragId, movable);
        if (!movable) {
            // Scenery becomes a body for as long as it is in his hands, or the solver would let him
            // pull it through rock: only movable solids are kept out of terrain and inside the arena.
            COLLIDER.get(load).set(COLLIDER.get(load).solid, true);
        }
        nextDragId++;
    }

    /**
     * Pulls the load to where it should be trailing, and drops it if it is no longer there to pull.
     *
     * <p>The grip breaks when the load falls further behind than an arm could stretch, which is what
     * happens when it snags on rock: the solver will not let it through, this keeps asking, and the
     * distance between them grows until he has plainly lost it.
     */
    private void haul(Entity dragger, Drag drag, float deltaTime) {
        if (!drag.active()) {
            return;
        }
        Entity load = loadOf(drag.load);
        if (load == null || !grabbable(load)) {
            letGo(drag);
            return;
        }
        Position at = POSITION.get(dragger);
        Position loadAt = POSITION.get(load);
        float toX = loadAt.x - at.x;
        float toY = loadAt.y - at.y;
        float apart = (float) Math.sqrt(toX * toX + toY * toY);
        float tether = BODY.get(dragger).radius + BODY.get(load).radius + drag.gap;
        if (apart > tether + ACTION.get(dragger).reach) {
            letGo(drag);
            return;
        }
        if (apart <= EPSILON) {
            // Standing on top of it: keep whatever bearing was last published rather than picking a
            // new one out of nothing, or the constraint on his movement would flip about.
            return;
        }
        drag.towardLoadX = toX / apart;
        drag.towardLoadY = toY / apart;
        follow(dragger, drag, load, at, loadAt, tether, deltaTime);
    }

    /**
     * A velocity that closes whatever slack there is this tick, capped at the speed of whoever is
     * pulling. Nothing drives a held unit, so this is the only thing writing it (an enforced rule) -
     * and the constraint solver still has the last word about where it may end up.
     */
    private static void follow(
            Entity dragger, Drag drag, Entity load, Position at, Position loadAt, float tether, float deltaTime) {
        if (!VELOCITY.has(load) || deltaTime <= 0f) {
            return;
        }
        float wantX = at.x + drag.towardLoadX * tether;
        float wantY = at.y + drag.towardLoadY * tether;
        float dx = (wantX - loadAt.x) / deltaTime;
        float dy = (wantY - loadAt.y) / deltaTime;
        float cap = SPEED.has(dragger) ? SPEED.get(dragger).value : 0f;
        float wanted = (float) Math.sqrt(dx * dx + dy * dy);
        if (cap > 0f && wanted > cap) {
            dx = dx / wanted * cap;
            dy = dy / wanted * cap;
        }
        VELOCITY.get(load).set(dx, dy);
    }

    private void letGo(Drag drag) {
        if (!drag.active()) {
            return;
        }
        Entity load = loadOf(drag.load);
        if (load != null) {
            Dragged dragged = DRAGGED.get(load);
            if (!dragged.wasMovable && COLLIDER.has(load)) {
                COLLIDER.get(load).set(COLLIDER.get(load).solid, false);
            }
            dragged.by = Drag.NOTHING;
            if (VELOCITY.has(load)) {
                VELOCITY.get(load).set(0f, 0f);
            }
        }
        drag.release();
    }

    private @Nullable Entity loadOf(long dragId) {
        for (int i = 0; i < loads.size(); i++) {
            if (DRAGGED.get(loads.get(i)).by == dragId) {
                return loads.get(i);
            }
        }
        return null;
    }

    private @Nullable Entity nearestGrabbable(Entity dragger, float reach) {
        Position at = POSITION.get(dragger);
        float radius = BODY.get(dragger).radius;
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < loads.size(); i++) {
            Entity candidate = loads.get(i);
            if (DRAGGED.get(candidate).held() || !grabbable(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            float gap = (float) Math.sqrt(toX * toX + toY * toY) - radius - BODY.get(candidate).radius;
            if (gap <= reach && gap < nearestGap) {
                nearest = candidate;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /**
     * Whether this has nothing else driving it. A man who is down does not; a barrel never did; a man
     * on his feet does, and a corpse is past helping.
     *
     * <p>A gun with somebody on it does too: the man aboard owns where it points and what it fires,
     * and hauling a working gun about by the barrel is the same mistake as picking up a trooper who
     * is still standing.
     */
    private static boolean grabbable(Entity entity) {
        if (DYING.has(entity) && DYING.get(entity).begun) {
            return false;
        }
        if (TURRET.has(entity) && TURRET.get(entity).crewed) {
            return false;
        }
        return !CASUALTY.has(entity) || CASUALTY.get(entity).begun;
    }
}
