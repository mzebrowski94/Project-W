package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Takes control points off the map for whoever is standing on them, and wakes what is nearby when
 * they start doing it.
 *
 * <p>Dwell, not touch: the clock <strong>goes back to zero the moment the last one leaves</strong>.
 * <strong>Taking a point is loud, once</strong>, on the tick an attempt starts, through {@link Rouse}
 * (G0016). The point a level names turns into a post when taken, and releases what it held back.
 * Progress is drawn by {@code AppearanceSystem}.
 */
public final class ControlPointSystem extends EntitySystem {

    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);
    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);

    private final Spawner spawner;

    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> holders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public ControlPointSystem(Spawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        points = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
        holders =
                engine.getEntitiesFor(Family.all(Faction.class, Position.class).get());
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        levels = engine.getEntitiesFor(Family.all(Waves.class).get());
        commanders =
                engine.getEntitiesFor(Family.all(Tally.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < points.size(); i++) {
            Entity entity = points.get(i);
            ControlPoint point = POINT.get(entity);
            if (point.captured) {
                continue;
            }
            Position at = POSITION.get(entity);
            boolean held = anyoneHolding(at, point.radius);
            if (!held) {
                point.heldTicks = 0;
                continue;
            }
            boolean starting = point.heldTicks == 0;
            point.heldTicks++;
            if (point.heldTicks >= point.captureTicks) {
                point.captured = true;
                point.takenOrder = lastTaken() + 1;
                point.heldTicks = point.captureTicks;
                creditTheTaking(at, point.radius);
                if (point.holdsAfterwards) {
                    standTo(point, at);
                }
            }
            if (starting) {
                // Through Rouse, shared with the nest (G0016).
                Rouse.within(watchers, at.x, at.y, point.aggroRadius);
            }
        }
    }

    /**
     * Turns ground that has just been taken into ground the level tells you to hold.
     *
     * <p>Once, on the tick the bar fills: a post goes down here and what was held back is let go. Not
     * from the first tick, or everything would walk at it from across the level (G0027).
     */
    private void standTo(ControlPoint point, Position at) {
        spawner.spawnPost(at.x, at.y, point.watchRadius);
        for (int i = 0; i < levels.size(); i++) {
            WAVES.get(levels.get(i)).arm();
        }
    }

    /**
     * A goal on the tally of every commander standing inside the point the tick it is taken (G0044) -
     * the same men who count as holding it, so a body in its throes takes no credit.
     */
    private void creditTheTaking(Position at, float radius) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (!Drivers.held(commander) && within(at, POSITION.get(commander), radius)) {
                TALLY.get(commander).goals++;
            }
        }
    }

    /**
     * Whether anyone on the player's side is inside the point.
     *
     * <p>A body in its throes holds nothing.
     */
    private boolean anyoneHolding(Position at, float radius) {
        for (int i = 0; i < holders.size(); i++) {
            Entity holder = holders.get(i);
            if (FACTION.get(holder).side != Side.PLAYER || Drivers.held(holder)) {
                continue;
            }
            if (within(at, POSITION.get(holder), radius)) {
                return true;
            }
        }
        return false;
    }

    /** The order the last point taken was taken in; zero while none has been (G0055). */
    private int lastTaken() {
        int last = 0;
        for (int i = 0; i < points.size(); i++) {
            last = Math.max(last, POINT.get(points.get(i)).takenOrder);
        }
        return last;
    }

    private static boolean within(Position a, Position b, float range) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return dx * dx + dy * dy <= range * range;
    }
}
