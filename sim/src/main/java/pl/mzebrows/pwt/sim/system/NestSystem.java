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
 * <p>A nest is the third way a level puts creatures on the field, after "everything is here from the
 * first tick" and a {@code Waves} schedule. What makes it different from both is that it is a
 * <strong>place</strong>: it can be walked to, shot, and taken away, so ground you have cleared stays
 * cleared only if you deal with what was filling it in.
 *
 * <p><strong>Three things happen here and the order between them matters.</strong>
 *
 * <ul>
 *   <li><strong>A nest that has just been destroyed empties itself</strong> - two or three more, and
 *       a shout to everything within earshot. It is the moment a nest is worst, which is the whole of
 *       what makes killing one a decision rather than a chore: you choose when to pay for it. This
 *       runs while the body is still on the field, in the same window respawning and blasts run in,
 *       and before the reaper takes it.
 *   <li><strong>A nest nobody has come near does nothing at all.</strong> On a level four screens
 *       wide a nest that worked from the first tick would have sent twenty creatures at empty ground
 *       before anybody arrived, and the player would meet the pile rather than the nest. Waking
 *       latches - backing off does not stop it - or a nest would be a thing you could wait out.
 *   <li><strong>A nest that is being hit calls the neighbours</strong>, exactly as a control point
 *       being taken does, through the one place that answers that (G0016). It reads its own health
 *       against what it had last tick rather than reading damage: damage is applied and cleared a
 *       dozen systems earlier, and a bar that went down is all the evidence that survives.
 * </ul>
 *
 * <p>Everything it hatches is jittered inside the tiles around it, never stood at a tile centre
 * (G0009) - the same {@code Scatter} a wave arrives through, for the same reason, and with the same
 * consequence: a nest in a corner with nowhere to put anything simply produces fewer.
 *
 * <p>It draws from a stream of its own, so re-tuning how big a brood is never reshuffles where a wave
 * lands or how a spitter aims (pillar 6).
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because each nest asks a question
 * about every man on the player's side, so the families are walked against each other.
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
                // The last of it, into whoever is standing over it. Nothing is reset afterwards -
                // the reaper takes the entity at the end of this tick and the component goes back to
                // the pool, so a second visit is impossible.
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
     * <p>A commander or a trooper, and anything else his side ever puts on the field - the same list
     * an alien hunts from, asked here as a question about a radius. A man who is down does not wake
     * one: walking into range on your back is not arriving.
     *
     * <p>A scan rather than a neighbour query, for the reason {@code Drivers.nearestLivingTarget}
     * gives: the player's side is a commander, a handful of men and whatever guns a level put down,
     * and there are a handful of nests. The collision grid exists for everything against everything,
     * which this is not.
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
     * <p>The allocation is deliberate and safe, exactly as {@code WaveSystem}'s is: a brood arrives
     * every several seconds at worst, and this is not a per-entity loop. Keeping a {@code Scatter}
     * alive for the length of a level to save an object a handful of times would be the worse trade.
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
