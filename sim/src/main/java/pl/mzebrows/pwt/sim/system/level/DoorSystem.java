package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;

/**
 * What becomes of a door over a tick (G0069): the work put into it counted, the door opened once there is enough,
 * an opened door sunk and taken off the field - and the one place that says "solid now?" told whenever a door
 * stops being a wall (ADR 0022).
 *
 * <p><strong>One tick of work a tick, however many hands</strong>: whoever is at it this tick - a commander, the
 * one trooper it went to - marks it worked, and a tick nobody did loses all of it. A commander and a trooper
 * together open it no faster than one.
 *
 * <p><strong>Open ground on the tick it opens.</strong> The sinking that follows is a look, as a blast's flash is
 * after the tick it caught: the doorway is passable from the next system on. A broken door was marked dead by
 * {@code DamageSystem} and is reaped this tick; here it only stops being solid.
 *
 * <p><strong>One trooper a door</strong>: of the men whose way it shuts ({@code Trooper.door}, written by {@code
 * TrooperMoveSystem}), it goes to the first at arm's length - a tie to the lower handle - and stays his while he is
 * at it and on his feet. A wound on him loses the work, as on a commander.
 *
 * <p>After {@code ActionSystem} and {@code AidSystem}, which put the hands to it this tick, and after {@code
 * DamageSystem}, which breaks it and wounds whoever was opening it.
 */
public final class DoorSystem extends EntitySystem {

    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);

    private final Solid solid;
    private final Spawner spawner;

    /** The map's tile, which a doorway is counted in. */
    private final float tileSize;

    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public DoorSystem(Solid solid, Spawner spawner) {
        this.solid = solid;
        this.spawner = spawner;
        this.tileSize = solid.map().tileSize();
    }

    @Override
    public void addedToEngine(Engine engine) {
        doors = engine.getEntitiesFor(Family.all(Door.class, Handle.class).get());
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Action.class, Handle.class, Position.class, Body.class)
                        .get());
    }

    @Override
    public void update(float deltaTime) {
        boolean changed = false;
        for (int i = 0; i < doors.size(); i++) {
            Entity entity = doors.get(i);
            Door door = DOOR.get(entity);
            if (DEAD.has(entity)) {
                // Broken this tick, or sunk: either way no longer a wall.
                changed = true;
                continue;
            }
            if (door.open) {
                sink(entity, door);
                continue;
            }
            changed |= work(door, trooperAt(entity, door));
        }
        if (changed) {
            solid.derive(doors);
        }
    }

    /**
     * The trooper this door has gone to, kept while he is still fit to open it and otherwise given to the
     * first man at it, lowest handle on a tie - or null. Writes the answer on the door.
     */
    private @Nullable Entity trooperAt(Entity entity, Door door) {
        long handle = HANDLE.get(entity).id;
        @Nullable Entity kept = Handles.resolve(troopers, door.trooper);
        if (kept != null && opens(kept, entity, handle)) {
            return kept;
        }
        Entity first = null;
        long firstHandle = Long.MAX_VALUE;
        for (int i = 0; i < troopers.size(); i++) {
            Entity trooper = troopers.get(i);
            long id = HANDLE.get(trooper).id;
            if (id < firstHandle && opens(trooper, entity, handle)) {
                first = trooper;
                firstHandle = id;
            }
        }
        door.trooper = first == null ? Handle.NOBODY : firstHandle;
        return first;
    }

    /** Whether a trooper could be opening this door now: his way shut by it, at arm's length, on his feet. */
    private boolean opens(Entity trooper, Entity door, long handle) {
        return TROOPER.get(trooper).door == handle
                && !Drivers.held(trooper)
                && Reach.doorGap(trooper, door, tileSize) <= ACTION.get(trooper).reach;
    }

    /** Counts this tick's work on a shut door, and says whether that opened it. Ice is never opened (ADR 0025). */
    private static boolean work(Door door, @Nullable Entity trooper) {
        if (!door.opensByHand()) {
            return false;
        }
        if (trooper != null && VITALITY.has(trooper) && VITALITY.get(trooper).woundedThisTick()) {
            // A wound on the man at it loses the work, as it does on a commander (G0069).
            door.workTicks = 0;
        } else if (trooper != null) {
            door.worked = true;
        }
        boolean worked = door.worked;
        door.worked = false;
        if (!worked) {
            // A commitment, not a total (G0021): a tick nobody worked loses every tick that was.
            door.workTicks = 0;
            return false;
        }
        door.workTicks++;
        if (door.workTicks < door.openTicks) {
            return false;
        }
        door.open = true;
        door.workTicks = 0;
        door.trooper = Handle.NOBODY;
        door.sinkingTicks = door.sinkTicks;
        return true;
    }

    /** An opened door seen sinking, and taken off the field once it has. */
    private void sink(Entity entity, Door door) {
        if (door.sinkingTicks > 0) {
            door.sinkingTicks--;
        }
        if (door.sinkingTicks <= 0) {
            spawner.markDead(entity);
        }
    }
}
