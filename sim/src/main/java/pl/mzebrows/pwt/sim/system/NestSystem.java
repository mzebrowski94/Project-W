package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Runs the nests: wakes them, hatches what they owe, and empties the last of one into the fight that
 * killed it.
 *
 * <p>A <strong>place</strong> that produces creatures, so it can be walked to, shot and taken away.
 *
 * <ul>
 *   <li><strong>A destroyed nest empties itself</strong> - a last brood and a shout - while its body is
 *       still on the field, before the reaper.
 *   <li><strong>A nest nobody has come near does nothing.</strong> Waking latches.
 *   <li><strong>A nest being hit calls the neighbours</strong> (G0016), read from its health against
 *       last tick - damage is already cleared by then.
 * </ul>
 *
 * <p>Hatches through {@code Scatter} (G0009), so a cornered nest produces fewer; its own stream.
 */
public final class NestSystem extends EntitySystem {

    private static final ComponentMapper<Nest> NEST = ComponentMapper.getFor(Nest.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    private final SimConfig config;
    private final TileMap map;
    private final Spawner spawner;
    private final Rng rng;

    private ImmutableArray<Entity> nests = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());

    public NestSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng) {
        this.config = config;
        this.map = map;
        this.spawner = spawner;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        nests = engine.getEntitiesFor(
                Family.all(Nest.class, Position.class, Health.class).get());
        quarry = engine.getEntitiesFor(
                Family.all(Faction.class, Position.class, Health.class).get());
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < nests.size(); i++) {
            Entity entity = nests.get(i);
            Nest nest = NEST.get(entity);
            Position at = POSITION.get(entity);
            Health health = HEALTH.get(entity);
            if (DEAD.has(entity)) {
                // The last of it. Reaped this tick, so no second visit.
                hatch(nest, at, between(nest.deathBroodMin, nest.deathBroodMax));
                Rouse.within(watchers, at.x, at.y, nest.rouseRadius);
                continue;
            }
            if (health.current < nest.watchedHealth) {
                Rouse.within(watchers, at.x, at.y, nest.rouseRadius);
            }
            nest.watchedHealth = health.current;
            if (!nest.awake) {
                if (anybodyNear(at, nest.wakeRadius)) {
                    nest.wake();
                }
                continue;
            }
            nest.untilNext--;
            if (nest.untilNext > 0) {
                continue;
            }
            hatch(nest, at, nextBrood(nest));
            nest.untilNext = nest.periodTicks;
        }
    }

    /**
     * Whether anybody on the player's side is close enough to be worth waking for.
     *
     * <p>The list an alien hunts from; a man who is down does not wake one. A scan, as
     * {@code Drivers.nearestLivingTarget}.
     */
    private boolean anybodyNear(Position at, float radius) {
        if (radius <= 0f) {
            return false;
        }
        float reach = radius * radius;
        for (int i = 0; i < quarry.size(); i++) {
            Entity candidate = quarry.get(i);
            if (!Drivers.huntable(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            if (toX * toX + toY * toY <= reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * How big the next of a nest's broods is, dealt from the nest's own bag: every size in its range
     * comes out once before any comes out again, so a nest never sends its smallest three times running.
     */
    private int nextBrood(Nest nest) {
        int sizes = nest.broodMax - nest.broodMin + 1;
        int size = Bag.draw(rng, nest.broodsDealt, sizes);
        nest.broodsDealt = Bag.take(nest.broodsDealt, size, sizes);
        return nest.broodMin + size;
    }

    /** How big the brood it leaves when it dies is. Once per nest, so there is nothing to deal evenly. */
    private int between(int fewest, int most) {
        return fewest >= most ? fewest : fewest + rng.nextInt(most - fewest + 1);
    }

    /**
     * One brood, scattered into the tiles around the nest.
     *
     * <p>Allocates, as {@code WaveSystem} does: once per brood, not in a per-entity loop.
     */
    private void hatch(Nest nest, Position at, int count) {
        if (count <= 0) {
            return;
        }
        float radius = config.unit(nest.kind).radius();
        Scatter scatter = new Scatter(map, rng, radius, radius);
        scatter.scatter(
                TileRect.around(map, at.x, at.y, nest.hatchRadius),
                count,
                radius,
                (x, y) -> spawner.spawnUnit(nest.kind, x, y));
    }
}
