package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Hauling something along behind you.
 *
 * <p>It never touches the commander's velocity: it publishes the load's bearing and
 * {@code CommanderControlSystem} constrains him. The load is moved by velocity, never position, so it
 * snags on rock; a grip stretched past an arm's reach is dropped. <strong>What may be grabbed is "has
 * nothing else driving it"</strong> (G0008).
 */
public final class DragSystem extends EntitySystem {

    private static final ComponentMapper<Drag> DRAG = ComponentMapper.getFor(Drag.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);

    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);

    private static final float EPSILON = 1e-4f;

    private ImmutableArray<Entity> draggers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> loads = new ImmutableArray<>(new Array<>());

    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        draggers = engine.getEntitiesFor(
                Family.all(Commander.class, Drag.class, Intent.class, Position.class, Body.class, Action.class)
                        .get());
        loads = engine.getEntitiesFor(
                Family.all(Dragged.class, Position.class, Body.class).get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
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
                letGo(dragger, drag);
                continue;
            }
            if (pressed) {
                if (drag.active()) {
                    letGo(dragger, drag);
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
        drag.load = Handles.of(load);
        boolean movable = !COLLIDER.has(load) || COLLIDER.get(load).movable;
        DRAGGED.get(load).take(Handles.of(dragger), movable);
        if (!movable) {
            // Movable while held, so the solver keeps it out of rock.
            COLLIDER.get(load).set(COLLIDER.get(load).solid, true);
        }
    }

    /**
     * Pulls the load to where it should be trailing, and drops it if it is no longer there to pull.
     *
     * <p>The grip breaks when the load falls further behind than an arm could stretch.
     */
    private void haul(Entity dragger, Drag drag, float deltaTime) {
        if (!drag.active()) {
            return;
        }
        Entity load = loadOf(dragger, drag);
        if (load == null || !grabbable(load)) {
            letGo(dragger, drag);
            return;
        }
        Position at = POSITION.get(dragger);
        Position loadAt = POSITION.get(load);
        float toX = loadAt.x - at.x;
        float toY = loadAt.y - at.y;
        float apart = (float) Math.sqrt(toX * toX + toY * toY);
        float tether = BODY.get(dragger).radius + BODY.get(load).radius + drag.gap;
        if (apart > tether + ACTION.get(dragger).reach) {
            letGo(dragger, drag);
            return;
        }
        if (apart <= EPSILON) {
            // Standing on top of it: keep the last bearing.
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

    private void letGo(Entity dragger, Drag drag) {
        if (!drag.active()) {
            return;
        }
        Entity load = loadOf(dragger, drag);
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

    /** What this man is holding, if it is still on the field and still his - nobody else took it. */
    private @Nullable Entity loadOf(Entity dragger, Drag drag) {
        Entity load = Handles.resolve(handled, drag.load);
        if (load == null || !DRAGGED.has(load) || DRAGGED.get(load).by != Handles.of(dragger)) {
            return null;
        }
        return load;
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
     * <p>A crewed gun has somebody driving it.
     */
    private static boolean grabbable(Entity entity) {
        if (Life.dying(entity)) {
            return false;
        }
        if (TURRET.has(entity) && TURRET.get(entity).crewed) {
            return false;
        }
        return !VITALITY.has(entity) || Life.casualty(entity);
    }
}
