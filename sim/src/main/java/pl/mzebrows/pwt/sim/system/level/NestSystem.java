package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Runs the nests: wakes them, hatches what they owe, and empties the last of one into the fight that
 * killed it.
 *
 * <p>A <strong>place</strong> that produces creatures, so it can be walked to, shot and taken away.
 *
 * <ul>
 *   <li><strong>A destroyed nest empties itself</strong> - a last brood and a shout - while its body is
 *       still on the field, before the reaper.
 *   <li><strong>A nest nobody in its biome has come near does nothing</strong> (G0062). Waking latches,
 *       and the first brood comes soon after.
 *   <li><strong>A nest nobody is near is left behind</strong> (G0031): with no man - trooper or commander -
 *       within {@code sim.nest.leftBehindRadius}, its countdown moves once every
 *       {@code sim.nest.leftBehindSlowdown} ticks. It still breeds, and is back at its pace the tick a man
 *       is near again. The men are filed in a {@link SpatialGrid} once a tick, and each nest asks it.
 *   <li><strong>A nest being hit calls the neighbours</strong> (G0016), read from its health against
 *       last tick - damage is already cleared by then.
 * </ul>
 *
 * <p>Hatches through {@link Brood} on its own stream: the ground nearest the hole first, never its own body, and
 * nothing within the keep-off of anybody on the player's side - a brood comes out of the nest, not out of the man
 * who is standing over it.
 */
public final class NestSystem extends EntitySystem {

    private static final ComponentMapper<Nest> NEST = ComponentMapper.getFor(Nest.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private final SimConfig config;
    private final TileMap map;
    private final Rng rng;
    private final Brood brood;

    /** What the adaptations held make of a brood's size (ADR 0017). */
    private final Modifiers modifiers;

    /** How long after waking a nest's first brood comes: one number for every nest (G0062). */
    private final int firstBroodTicks;

    /** How close a man keeps a nest at its pace; zero, and no nest is ever left behind (G0031). */
    private final float leftBehindRadius;

    /** How many ticks a left-behind nest takes for each of its own. */
    private final int leftBehindSlowdown;

    /** The men on the field, filed by where they stand; none when nothing is ever left behind. */
    private final @Nullable SpatialGrid grid;

    /** What the grid hands back for one nest; reused, so asking costs nothing per nest. */
    private final Array<Entity> nearby = new Array<>(false, 16);

    private ImmutableArray<Entity> nests = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> men = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());

    public NestSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng, Modifiers modifiers) {
        this.config = config;
        this.map = map;
        this.rng = rng;
        this.modifiers = modifiers;
        this.brood = new Brood(map, spawner, config.nest().hatchKeepOff());
        this.firstBroodTicks = Ticks.of(config.nest().firstBroodSeconds(), config.tickSeconds());
        this.leftBehindRadius = config.nest().leftBehindRadius();
        this.leftBehindSlowdown = config.nest().leftBehindSlowdown();
        this.grid =
                leftBehindRadius > 0f ? new SpatialGrid(map.worldWidth(), map.worldHeight(), leftBehindRadius) : null;
    }

    @Override
    public void addedToEngine(Engine engine) {
        nests = engine.getEntitiesFor(
                Family.all(Nest.class, Position.class, Health.class).get());
        quarry = engine.getEntitiesFor(Family.all(Faction.class, Position.class)
                .one(Health.class, Vitality.class)
                .get());
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        // On the field in any state short of gone: a man down beside a nest is still a man beside it.
        men = engine.getEntitiesFor(ReinforcementSystem.onTheField());
    }

    @Override
    public void update(float deltaTime) {
        fileTheMen();
        for (int i = 0; i < nests.size(); i++) {
            Entity entity = nests.get(i);
            Nest nest = NEST.get(entity);
            Position at = POSITION.get(entity);
            Health health = HEALTH.get(entity);
            if (DEAD.has(entity)) {
                // The last of it. Reaped this tick, so no second visit.
                hatch(nest, at, bodyOf(entity), between(nest.deathBroodMin, nest.deathBroodMax));
                Rouse.within(watchers, at.x, at.y, nest.rouseRadius);
                continue;
            }
            if (health.current < nest.watchedHealth) {
                Rouse.within(watchers, at.x, at.y, nest.rouseRadius);
            }
            nest.watchedHealth = health.current;
            if (!nest.awake) {
                if (anybodyNear(at, nest.wakeRadius)) {
                    nest.wake(firstBroodTicks);
                }
                continue;
            }
            if (!keepsPace(nest, at)) {
                continue;
            }
            nest.untilNext--;
            if (nest.untilNext > 0) {
                continue;
            }
            hatch(nest, at, bodyOf(entity), nextBrood(entity, nest));
            nest.untilNext = nest.periodTicks;
        }
    }

    /** Every man on the field into the grid, once a tick, before any nest asks it. */
    private void fileTheMen() {
        SpatialGrid filed = grid;
        if (filed == null) {
            return;
        }
        filed.clear();
        for (int i = 0; i < men.size(); i++) {
            Entity man = men.get(i);
            Position at = POSITION.get(man);
            filed.insert(man, at.x, at.y);
        }
    }

    /**
     * Whether an awake nest's countdown moves this tick (G0031): every tick while a man is near, once every
     * {@link #leftBehindSlowdown} ticks while it is left behind. A man near again puts it back at its pace on
     * that tick, and what it spent alone is neither made up nor held over.
     */
    private boolean keepsPace(Nest nest, Position at) {
        SpatialGrid filed = grid;
        if (filed == null || anyManNear(filed, at)) {
            nest.leftBehindTicks = 0;
            return true;
        }
        nest.leftBehindTicks++;
        if (nest.leftBehindTicks < leftBehindSlowdown) {
            return false;
        }
        nest.leftBehindTicks = 0;
        return true;
    }

    /** Whether any man on the field stands within the left-behind radius - biome or none. */
    private boolean anyManNear(SpatialGrid filed, Position at) {
        filed.near(at.x, at.y, leftBehindRadius, nearby);
        for (int i = 0; i < nearby.size; i++) {
            Position where = POSITION.get(nearby.get(i));
            if (Sight.withinRange(at.x, at.y, where.x, where.y, leftBehindRadius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether anybody on the player's side is in the nest's biome and close enough to be worth waking for
     * (G0062): the radius alone reaches through a ridge into a biome nobody has entered.
     *
     * <p>The list an alien hunts from; a man who is down does not wake one. A scan, as
     * {@code Drivers.nearestLivingTarget}. The biome is looked up rather than kept: a nest never moves and
     * the lookup is an array read.
     */
    private boolean anybodyNear(Position at, float radius) {
        if (radius <= 0f) {
            return false;
        }
        float reach = radius * radius;
        int biome = map.biomeAt(at.x, at.y);
        for (int i = 0; i < quarry.size(); i++) {
            Entity candidate = quarry.get(i);
            if (!Drivers.huntable(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            if (toX * toX + toY * toY <= reach && map.biomeAt(where.x, where.y) == biome) {
                return true;
            }
        }
        return false;
    }

    /**
     * How big the next of a nest's broods is, dealt from the nest's own bag: every size in its range
     * comes out once before any comes out again, so a nest never sends its smallest three times running - and
     * then whatever the swarm's adaptations add to every brood.
     */
    private int nextBrood(Entity entity, Nest nest) {
        int sizes = nest.broodMax - nest.broodMin + 1;
        int size = Bag.draw(rng, nest.broodsDealt, sizes);
        nest.broodsDealt = Bag.take(nest.broodsDealt, size, sizes);
        return modifiers.broodSize(entity, nest.broodMin + size);
    }

    /** How big the brood it leaves when it dies is. Once per nest, so there is nothing to deal evenly. */
    private int between(int fewest, int most) {
        return fewest >= most ? fewest : fewest + rng.nextInt(most - fewest + 1);
    }

    /** The nest's own body, which nothing is hatched inside; none, if it was given none. */
    private static float bodyOf(Entity nest) {
        return BODY.has(nest) ? BODY.get(nest).radius : 0f;
    }

    /** One brood, out of the hole and off everybody on the player's side ({@link Brood}). */
    private void hatch(Nest nest, Position at, float bodyRadius, int count) {
        brood.hatch(
                rng, nest.kind, config.unit(nest.kind).radius(), 0, at, bodyRadius, nest.hatchRadius, count, quarry);
    }
}
