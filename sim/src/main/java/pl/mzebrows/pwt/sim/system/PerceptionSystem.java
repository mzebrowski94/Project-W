package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.SimConfig;
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

    private final Defs<PickupKind, PickupDef> pickupDefs;

    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> edible = new ImmutableArray<>(new Array<>());

    public PerceptionSystem(SimConfig config) {
        this.pickupDefs = config.pickups();
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
            } else {
                float toTargetX = target.x - position.x;
                float toTargetY = target.y - position.y;
                float range = perception.rangeInPlay();
                perception.aggro = toTargetX * toTargetX + toTargetY * toTargetY <= range * range;
            }
            perception.foraging = false;
            if (!perception.aggro) {
                lookForFood(watcher, perception, position);
            }
        }
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
