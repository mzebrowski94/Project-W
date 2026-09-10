package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Decides, once a tick, which units are hunting the commander and which have not noticed him.
 *
 * <p>Everything downstream reads the answer and never asks the question again: navigation drives the
 * ones that are hunting, wandering drives the ones that are not, and the renderer shows which is
 * which. One place deciding is what stops two systems disagreeing about whether a unit is awake.
 *
 * <p><strong>Him going down releases everything, whatever its switches say.</strong> A strain that
 * never loses interest never loses interest in a living man; a pack standing to attention over a
 * corpse for the length of a respawn is the game holding its breath, and the three seconds after a
 * kill are supposed to be a beat rather than a freeze frame.
 *
 * <p><strong>The hysteresis is the mechanic</strong> for a strain that can give up at all. It notices
 * him at one range and gives up at a longer one, so the distance at which it wakes is not the distance
 * at which it sleeps. With a single range, a commander standing on the boundary would flip a whole
 * pack between hunting and idling every tick - and the boundary is exactly where a player edging
 * forward stands. A unit notices him at one range and gives up at
 * a longer one, so the distance at which it wakes is not the distance at which it sleeps. With a
 * single range, a commander standing on the boundary would flip a whole pack between hunting and
 * idling every tick - and the boundary is exactly where a player edging forward stands.
 *
 * <p>A held unit is skipped entirely rather than re-evaluated. Being stunned does not make you forget
 * what you were chasing, and a corpse is not deciding anything.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because the commander is resolved
 * once per tick rather than once per unit.
 */
public final class PerceptionSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);

    private final Defs<PickupKind, PickupDef> pickupDefs;
    private final TileMap map;

    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> edible = new ImmutableArray<>(new Array<>());

    public PerceptionSystem(SimConfig config, TileMap map) {
        this.pickupDefs = config.pickups();
        this.map = map;
    }

    @Override
    public void addedToEngine(Engine engine) {
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
        edible = engine.getEntitiesFor(Family.all(Pickup.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        @Nullable Position target = Drivers.livingTarget(commanders);
        for (int i = 0; i < watchers.size(); i++) {
            Entity watcher = watchers.get(i);
            if (Drivers.held(watcher)) {
                continue;
            }
            Perception perception = PERCEPTION.get(watcher);
            Position position = POSITION.get(watcher);
            if (target == null) {
                // Nobody left to hunt. This outranks every switch below it: a pack standing to
                // attention over a corpse for the length of a respawn is the game holding its breath.
                perception.aggro = false;
            } else if (perception.alwaysAggressive) {
                perception.aggro = true;
            } else if (perception.aggro && !perception.losesInterest) {
                perception.aggro = true;
            } else if (perception.aggro) {
                // Losing him is range alone, whatever else this strain declares. That is the
                // hysteresis: a trooper that has seen something must not forget it the instant it
                // turns, and a unit must not be un-noticed by a wall it walked behind.
                perception.aggro = withinRange(position, target, perception.loseRange);
            } else {
                perception.aggro = notices(watcher, perception, position, target);
            }
            if (perception.aggro) {
                // The commander outranks a grudge exactly as absolutely as he outranks food. A unit
                // that has noticed him drops where it was going, and does not resume afterwards -
                // whatever provoked it is a long way behind by then.
                perception.provoked = false;
            } else if (perception.provoked && withinRange(position, provokedPlace(perception), map.tileSize())) {
                // Arrived. Whatever is standing here is now a fight like any other, decided by
                // contact; there is nothing left for the latch to say.
                perception.provoked = false;
            }
            perception.foraging = false;
            if (!perception.occupied()) {
                lookForFood(watcher, perception, position);
            }
        }
    }

    /**
     * Whether this unit notices the target, by every test its strain declares (G0015).
     *
     * <p><strong>Cheapest first, short-circuiting</strong>, and the order is a cost ordering rather
     * than a meaningful one: a squared distance, then a dot product, then a walk of the tile grid.
     * The grid walk is reached only by something already in range and already in front, which is why
     * a line-of-sight test here costs a fraction of what one per unit per tick would.
     *
     * <p>A strain that declares neither an arc nor a line - every alien - pays two comparisons that
     * always pass and behaves exactly as it did before any of this existed.
     */
    private boolean notices(Entity watcher, Perception perception, Position position, Position target) {
        if (!withinRange(position, target, perception.detectionRange)) {
            return false;
        }
        if (!withinArc(watcher, perception, position, target)) {
            return false;
        }
        return !perception.requiresClearLine || map.clearLine(position.x, position.y, target.x, target.y);
    }

    /** Scratch for the latched place, so comparing against it costs no allocation per unit per tick. */
    private final Position latched = new Position();

    private Position provokedPlace(Perception perception) {
        latched.set(perception.provokedX, perception.provokedY);
        return latched;
    }

    private static boolean withinRange(Position from, Position to, float range) {
        float towardsX = to.x - from.x;
        float towardsY = to.y - from.y;
        return towardsX * towardsX + towardsY * towardsY <= range * range;
    }

    /**
     * Whether the target is inside the cone this unit notices within, centred on the way it is facing.
     *
     * <p>The facing read here is the one the renderer drew last frame, deliberately: the arc the game
     * uses is then exactly the arc the player was shown. A unit that has no facing to speak of - it
     * has never moved, or does not steer itself - notices all round rather than nothing, so an arc
     * cannot accidentally blind something before it has taken a step.
     */
    private static boolean withinArc(Entity watcher, Perception perception, Position position, Position target) {
        if (!APPEARANCE.has(watcher)) {
            return true;
        }
        Appearance appearance = APPEARANCE.get(watcher);
        return Sight.withinArc(
                appearance.facingX,
                appearance.facingY,
                position.x,
                position.y,
                target.x,
                target.y,
                perception.arcCosine);
    }

    /**
     * Sends an idle unit after the nearest thing it can eat, if anything is close enough.
     *
     * <p>The commander outranks food absolutely - this is only ever reached by a unit that has not
     * noticed him - so a hungry grunt never walks past him to get to a mushroom. The secondary range
     * is the shortest of the three for the same reason: this is what a unit does with its attention
     * when it has nothing else to do with it.
     *
     * <p>What is stored is a <em>place</em>, not the thing standing in it. A pickup can be claimed and
     * reaped between one tick and the next, and components are pooled, so a reference kept here could
     * come back pointing at whatever was built from the recycled instance.
     *
     * <p>Ties are broken by which entity was spawned first, because the family is in insertion order
     * and the comparison is strict. Two mushrooms at exactly equal distance therefore pull the same
     * way on every replay of the same run.
     */
    private void lookForFood(Entity watcher, Perception perception, Position position) {
        if (perception.secondaryRange <= 0f || !FACTION.has(watcher)) {
            return;
        }
        Side side = FACTION.get(watcher).side;
        float nearest = perception.secondaryRange * perception.secondaryRange;
        for (int i = 0; i < edible.size(); i++) {
            Entity food = edible.get(i);
            Pickup pickup = PICKUP.get(food);
            if (pickup.claimed || (DYING.has(food) && DYING.get(food).begun)) {
                continue;
            }
            if (!pickupDefs.of(pickup.kind).offersSomethingTo(side)) {
                continue;
            }
            Position where = POSITION.get(food);
            float towardsX = where.x - position.x;
            float towardsY = where.y - position.y;
            float distance = towardsX * towardsX + towardsY * towardsY;
            if (distance < nearest) {
                nearest = distance;
                perception.feedAt(where.x, where.y);
            }
        }
    }
}
