package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
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
 *
 * <p><strong>A taken point is a taken site</strong> (G0110): what it earns and leaves - the goal on the tally, the
 * grenade caches, the medkit, and for a biome's point its crates - is {@link Sites#taken}'s, the step every kind of
 * site goes through. The point that holds afterwards is the finale's, not a biome's, and leaves no crates.
 */
public final class ControlPointSystem extends EntitySystem {

    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);

    private final Spawner spawner;
    private final Sites sites;

    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> holders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    /**
     * @param rng where what a taken point leaves falls
     * @param capacity how many slots the game holds
     */
    public ControlPointSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng, int capacity) {
        this.spawner = spawner;
        this.sites = new Sites(config, map, spawner, rng, capacity);
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
        sites.addedToEngine(engine);
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
                sites.taken(entity, at, point.radius, !point.holdsAfterwards);
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
