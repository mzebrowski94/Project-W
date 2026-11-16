package pl.mzebrows.pwt.sim.system.creature;

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
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Decides, once a tick, which units are hunting somebody, and who that somebody is.
 *
 * <p><strong>Whoever on the player's side is nearest</strong> (G0026), published as a place on
 * {@code Perception}; everything downstream reads it and never asks again. <strong>It keeps the man it is
 * after</strong> until another is nearer by the switch margin (G0097): his handle goes back into the question, and
 * only while it is hunting - a man it never noticed is not kept.
 *
 * <p><strong>Its target going down releases it, whatever its switches say</strong>, and it may notice
 * somebody else on the same tick. <strong>The hysteresis is the mechanic</strong>: it notices at one
 * range and gives up at a longer one ({@code PerceptionDef}). A held unit is skipped, not re-evaluated.
 */
public final class PerceptionSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);

    private final Defs<PickupKind, PickupDef> pickupDefs;
    private final Solid map;
    private final float loiterRadius;

    /** How much nearer another man must be before a hunter leaves the one it is after (G0097). Zero is off. */
    private final float switchMargin;

    /** What the adaptations held make of both ranges (ADR 0017). */
    private final Modifiers modifiers;

    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> sides = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> posts = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> edible = new ImmutableArray<>(new Array<>());

    public PerceptionSystem(SimConfig config, Solid map, Modifiers modifiers) {
        this.pickupDefs = config.pickups();
        this.map = map;
        this.loiterRadius = config.controlPoint().loiterRadius();
        this.switchMargin = config.switchMargin();
        this.modifiers = modifiers;
    }

    @Override
    public void addedToEngine(Engine engine) {
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        // Aliens excluded by family: an enemy is never on the player's side.
        sides = engine.getEntitiesFor(Family.all(Faction.class, Position.class)
                .one(Health.class, Vitality.class)
                .exclude(Enemy.class)
                .get());
        posts = engine.getEntitiesFor(Family.all(Post.class, Position.class).get());
        edible = engine.getEntitiesFor(Family.all(Pickup.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < watchers.size(); i++) {
            Entity watcher = watchers.get(i);
            if (Drivers.held(watcher)) {
                continue;
            }
            Perception perception = PERCEPTION.get(watcher);
            Position position = POSITION.get(watcher);
            // Asked again every tick (G0026): the nearest, leaning towards the man it is after (G0097).
            long after = perception.aggro ? perception.kept : Handles.NOBODY;
            @Nullable Entity quarry = Drivers.nearestLivingTarget(sides, position, after, switchMargin);
            @Nullable Position target = quarry == null ? null : POSITION.get(quarry);
            if (target == null) {
                // Nobody left to hunt. This outranks every switch below it.
                perception.aggro = false;
            } else if (perception.alwaysAggressive) {
                perception.aggro = true;
            } else if (perception.aggro && !perception.losesInterest) {
                perception.aggro = true;
            } else if (perception.aggro) {
                // Losing him is range alone, whatever else this strain declares.
                perception.aggro = withinRange(position, target, modifiers.loseRange(watcher, perception.loseRange));
            } else {
                perception.aggro = notices(watcher, perception, position, target);
            }
            if (perception.aggro && quarry != null && target != null) {
                perception.hunt(Handles.of(quarry), target.x, target.y);
                // Somebody in front of it outranks a grudge, which is dropped for good.
                perception.provoked = false;
            } else if (perception.provoked
                    && withinRange(
                            position, provokedPlace(perception), map.map().tileSize())) {
                // Arrived: whatever is here is decided by contact.
                perception.provoked = false;
            }
            perception.foraging = false;
            perception.marching = false;
            if (!perception.occupied()) {
                lookForFood(watcher, perception, position);
            }
            if (!perception.occupied()
                    && posts.size() > 0
                    && !withinRange(position, POSITION.get(posts.first()), loiterRadius)) {
                // Nothing noticed, owed or edible, and a post to walk at: the lowest priority. Near it,
                // it ambles about its patch instead of piling onto it (G0027).
                perception.marching = true;
            }
        }
    }

    /**
     * Whether this unit notices the target, by every test its strain declares (G0015).
     *
     * <p><strong>Cheapest first, short-circuiting</strong>: squared distance (its range folded through the
     * adaptations held, a pass over the ledger), dot product, grid walk.
     */
    private boolean notices(Entity watcher, Perception perception, Position position, Position target) {
        if (!withinRange(position, target, modifiers.detectionRange(watcher, perception.detectionRange))) {
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
     * <p>Last tick's {@code Facing}, the one the renderer drew. No facing notices all round, and so does
     * anything inside its all-round range.
     */
    private static boolean withinArc(Entity watcher, Perception perception, Position position, Position target) {
        if (!FACING.has(watcher)) {
            return true;
        }
        Facing facing = FACING.get(watcher);
        return Sight.withinNotice(
                facing.x,
                facing.y,
                position.x,
                position.y,
                target.x,
                target.y,
                perception.arcCosine,
                perception.allRoundRange);
    }

    /**
     * Sends an idle unit after the nearest thing it can eat, if anything is close enough.
     *
     * <p>Only reached by a unit that has noticed nobody. Stores a <em>place</em>. Ties go to the first
     * spawned: families keep insertion order and the comparison is strict.
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
            if (pickup.claimed || Life.dying(food)) {
                continue;
            }
            if (pickup.shunned && !swallows(watcher)) {
                // Shunned is not food, except to something that swallows it whole (G0022).
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

    /** Whether this unit takes an explosive whole rather than setting it off, and therefore wants one. */
    private static boolean swallows(Entity watcher) {
        return TAKING.has(watcher) && TAKING.get(watcher).swallows;
    }
}
