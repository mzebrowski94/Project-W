package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Seats;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.PickupKind;
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
 * <p><strong>A taken point lays grenade caches</strong> beside itself, once, on the tick it falls: so many a seated
 * slot, in the ring its reinforcements lie in, off every point (G0078). Allocates a {@code Scatter} then - once per
 * point taken, never per entity.
 */
public final class ControlPointSystem extends EntitySystem {

    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);
    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);

    private final Spawner spawner;
    private final TileMap map;
    private final Rng rng;
    private final SimConfig.ControlPointConfig config;
    private final float cacheRadius;

    /** Scratch for counting seats, by slot; overwritten before each read. */
    private final boolean[] seated;

    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> holders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> seatedCommanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> returning = new ImmutableArray<>(new Array<>());

    /**
     * @param rng where a taken point's grenade caches fall
     * @param capacity how many slots the game holds
     */
    public ControlPointSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng, int capacity) {
        this.spawner = spawner;
        this.map = map;
        this.rng = rng;
        this.config = config.controlPoint();
        this.cacheRadius = config.pickup(PickupKind.ARMOURY_GRENADES).radius();
        this.seated = new boolean[capacity];
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
        seatedCommanders = engine.getEntitiesFor(Family.all(Commander.class).get());
        lying = engine.getEntitiesFor(
                Family.all(Pickup.class, Position.class, Body.class).get());
        returning = engine.getEntitiesFor(Family.all(Respawning.class).get());
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
                layGrenadeCaches(at);
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
     * The grenade caches a point lays the tick it is taken (G0078): so many for every seated slot - standing, down or
     * on his way back, away or not - outside every point and off every pickup lying there, within the reinforcements'
     * reach of this one. A cornered point lays fewer.
     */
    private void layGrenadeCaches(Position at) {
        int count = config.grenadeCaches() * Seats.count(seated, seatedCommanders, returning);
        if (count <= 0) {
            return;
        }
        Scatter scatter = new Scatter(map, rng, cacheRadius, cacheRadius);
        for (int i = 0; i < points.size(); i++) {
            Position other = POSITION.get(points.get(i));
            scatter.reserve(other.x, other.y, config.radius());
        }
        // Off what already lies there - the point's supply drops share this ring - so nothing lands on anything.
        for (int i = 0; i < lying.size(); i++) {
            Entity pickup = lying.get(i);
            Position where = POSITION.get(pickup);
            scatter.reserve(where.x, where.y, BODY.get(pickup).radius);
        }
        scatter.scatter(
                TileRect.around(map, at.x, at.y, config.reinforcementRadius()),
                count,
                cacheRadius,
                (x, y) -> spawner.spawnPickup(PickupKind.ARMOURY_GRENADES, x, y));
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
