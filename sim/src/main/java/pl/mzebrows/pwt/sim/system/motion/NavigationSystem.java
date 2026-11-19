package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import java.util.Arrays;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Approach;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Asks every enemy that is hunting somebody to head for him at its own speed, round whatever is in
 * the way.
 *
 * <p>Who that is was decided by {@code PerceptionSystem} and published as a place (G0026); this
 * system walks the answer and never asks the question.
 *
 * <p>A unit hurt before it noticed anybody walks at <em>where the hit came from</em> (G0016); one that
 * has spotted food walks at that; one with neither is not spoken for here.
 *
 * <p><strong>Routes:</strong> one {@link FlowField} per strain, built for its body radius and flooded
 * from every man on the player's side at once, rebuilt only when that set of tiles changes. <strong>A
 * unit that can see its target walks straight at it</strong> - the field is four-connected and would
 * funnel a pack onto one row - and the last tile is steered at the man, not the tile. Provocations and
 * food are steered straight, without a field.
 *
 * <p><strong>Round a shut door, or at it</strong> (G0075, amending ADR 0022): a hunter's route and its line both go
 * over the ground as it is now ({@link Solid}), so a shut house between it and a man in the open is walked round.
 * Only a hunter that cannot reach any man with the doors shut takes the terrain's route, which runs through the
 * door to the man behind it, and so walks up to the door it then leaps at (G0069).
 *
 * <p>Writes a desired velocity; {@code DriveSystem} commits it (G0010).
 */
@SuppressWarnings("EnumOrdinal")
public final class NavigationSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Approach> APPROACH = ComponentMapper.getFor(Approach.class);

    private static final float EPSILON = 1e-4f;

    private final TileMap map;

    /** Whether a tile is solid now: the terrain and every shut door (ADR 0022). */
    private final Solid solid;

    /**
     * One field per strain over the ground as it is now, indexed by ordinal, never persisted: the way round a shut
     * door.
     */
    private final FlowField[] fields;

    /**
     * One field per strain over the terrain alone, through shut doors: the way to a man a shut door is between.
     */
    private final FlowField[] sieges;

    /** The {@link Solid#revision} the fields were last flooded over; a different one floods them again. */
    @NotState("cache of the shut doors the fields were flooded over: a pure function of Solid's revision")
    private int floodedOver;

    /**
     * One field per strain over the ground as it is now, pointed at the place the first provoked unit of that strain
     * is walking to this tick (G0016): a cry's place is shared by everything that heard it, so one field serves them.
     * A unit walking to another place goes straight, as it always did.
     */
    private final FlowField[] places;

    /** The tile each place field is claimed for this tick, or {@code -1}: scratch, cleared every update. */
    @NotState("scratch: cleared at the start of every update")
    private final int[] placeClaim;

    /**
     * One field per strain pointed at the post, built once: the post never moves.
     */
    private final FlowField[] marches;

    /** Scratch for one direction, so sampling a field costs no allocation per unit per tick. */
    private final float[] direction = new float[2];

    /** Scratch for the place being hunted, so reading it costs no allocation per unit per tick. */
    private final Position hunted = new Position();

    private ImmutableArray<Entity> posts = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    /** Everything that could be hunted, so the field can be built for all of them at once. */
    private ImmutableArray<Entity> targets = new ImmutableArray<>(new Array<>());

    /** Their tiles, reused between ticks so collecting them costs no allocation per tick. */
    @NotState("scratch: refilled from the targets every update; only its capacity survives a tick")
    private int[] goalCols = new int[8];

    @NotState("scratch: refilled from the targets every update; only its capacity survives a tick")
    private int[] goalRows = new int[8];

    public NavigationSystem(SimConfig config, Solid solid) {
        this.solid = solid;
        this.map = solid.map();
        this.floodedOver = solid.revision();
        this.fields = new FlowField[UnitKind.values().length];
        this.sieges = new FlowField[UnitKind.values().length];
        this.marches = new FlowField[UnitKind.values().length];
        this.places = new FlowField[UnitKind.values().length];
        this.placeClaim = new int[UnitKind.values().length];
        for (UnitKind kind : UnitKind.values()) {
            fields[kind.ordinal()] =
                    FlowField.over(map, solid, config.unit(kind).radius());
            sieges[kind.ordinal()] = FlowField.over(map, config.unit(kind).radius());
            marches[kind.ordinal()] = FlowField.over(map, config.unit(kind).radius());
            places[kind.ordinal()] =
                    FlowField.over(map, solid, config.unit(kind).radius());
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        posts = engine.getEntitiesFor(Family.all(Post.class, Position.class).get());
        enemies = engine.getEntitiesFor(
                Family.all(Enemy.class, Position.class, Steering.class, Speed.class, Perception.class, Strain.class)
                        .get());
        targets = engine.getEntitiesFor(Family.all(Faction.class, Position.class)
                .one(Health.class, Vitality.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        int goals = collectGoals();
        if (solid.revision() != floodedOver) {
            floodedOver = solid.revision();
            for (FlowField field : fields) {
                field.invalidate();
            }
            for (FlowField field : places) {
                field.invalidate();
            }
        }
        Arrays.fill(placeClaim, -1);
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            Perception perception = PERCEPTION.get(enemy);
            if (Drivers.held(enemy) || !perception.occupied()) {
                continue;
            }
            // A shooter that holds ground moves for somewhere to be, never for somebody to follow: a
            // provocation (G0016), or up to its firing range once. Then it holds.
            boolean rooted = RANGED.has(enemy) && RANGED.get(enemy).enabled && RANGED.get(enemy).holdsGround;
            if (!perception.aggro && perception.provoked) {
                toAPlace(enemy, perception.provokedX, perception.provokedY);
                continue;
            }
            if (!perception.aggro && perception.foraging) {
                foodAt(enemy, perception);
                continue;
            }
            if (!perception.aggro) {
                marchToThePost(enemy);
                continue;
            }
            // Where its target stood this tick, published by PerceptionSystem (G0026, G0010).
            hunted.set(perception.targetX, perception.targetY);
            Position target = hunted;
            if (rooted && !RANGED.get(enemy).takingUpPosition(distanceBetween(POSITION.get(enemy), target))) {
                // In position: it holds, and he is not followed.
                continue;
            }
            Position position = POSITION.get(enemy);
            float speed = SPEED.get(enemy).value;
            float radius = BODY.get(enemy).radius;
            // Its own line in (G0033), after the shooter's range question: a pack arrives abreast.
            spreadOut(enemy, position, hunted);
            if (within(position, target, map.tileSize())
                    || solid.isClearPath(position.x, position.y, target.x, target.y, radius)) {
                straightAt(enemy, position, target, speed);
                continue;
            }
            // Flooded from every man at once - one grid pass either way - so it leads to the nearest
            // by route (G0026). The last of the approach is steered at the man. Round a shut door if
            // there is a way round; through it, to the door, if there is none (G0075).
            int kind = STRAIN.get(enemy).kind.ordinal();
            FlowField field = fields[kind];
            field.rebuildFor(goalCols, goalRows, goals);
            if (!field.reachesFrom(position.x, position.y)) {
                field = sieges[kind];
                field.rebuildFor(goalCols, goalRows, goals);
            }
            if (field.directionAt(position.x, position.y, direction)) {
                STEERING.get(enemy).set(direction[0] * speed, direction[1] * speed);
                continue;
            }
            straightAt(enemy, position, target, speed);
        }
    }

    /**
     * The tile of everything an alien would come for, for this tick's fields.
     *
     * <p>Each strain's field rebuilds only if the set has changed.
     */
    private int collectGoals() {
        int count = 0;
        for (int i = 0; i < targets.size(); i++) {
            Entity candidate = targets.get(i);
            if (!Drivers.huntable(candidate)) {
                continue;
            }
            if (count == goalCols.length) {
                goalCols = Arrays.copyOf(goalCols, count * 2);
                goalRows = Arrays.copyOf(goalRows, count * 2);
            }
            Position where = POSITION.get(candidate);
            goalCols[count] = map.colAt(where.x);
            goalRows[count] = map.rowAt(where.y);
            count++;
        }
        return count;
    }

    /**
     * Moves the point a creature is walking at sideways, onto the line it has taken for itself.
     *
     * <p><strong>The goal moves, not the course</strong> (G0033): a different spot each, so a pack does
     * not converge. {@code Approach.bearing} is seeded at spawn, nothing drawn. Only the caller's scratch
     * point moves; the field is still flooded from the men. Troopers use it too (G0041).
     */
    static void spreadOut(Entity enemy, Position from, Position target) {
        if (!APPROACH.has(enemy)) {
            return;
        }
        Approach approach = APPROACH.get(enemy);
        if (!approach.active()) {
            return;
        }
        float toTargetX = target.x - from.x;
        float toTargetY = target.y - from.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= EPSILON) {
            return;
        }
        float swing = approach.swingAt(distance);
        if (swing == 0f) {
            return;
        }
        // Perpendicular to the line in: a width, never closer or further.
        target.set(target.x - toTargetY / distance * swing, target.y + toTargetX / distance * swing);
    }

    /**
     * Whether the two are close enough that the grid has nothing left to say.
     *
     * <p>Inside a tile a field points at the tile's centre, and a unit would orbit him.
     */
    private static boolean within(Position position, Position target, float range) {
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        return toTargetX * toTargetX + toTargetY * toTargetY <= range * range;
    }

    /** How far apart two points are, in world units. */
    private static float distanceBetween(Position position, Position target) {
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        return (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
    }

    /** The last of the approach, and what a unit falls back on when the field has nothing to say. */
    private static void straightAt(Entity enemy, Position position, Position target, float speed) {
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= EPSILON) {
            return;
        }
        STEERING.get(enemy).set(toTargetX / distance * speed, toTargetY / distance * speed);
    }

    /**
     * To a place something is cross about (G0016): straight when nothing is in the way, otherwise round it.
     *
     * <p><strong>One field per strain per tick</strong>: the first unit of a strain to ask claims it for its place and
     * every other that wants the same tile reads it - a cry is one place for everything that heard it. A unit with a
     * place of its own, or one the field cannot reach it from, goes straight and may mill at a wall; that is the
     * limit left, and it is a rare one.
     */
    private void toAPlace(Entity enemy, float x, float y) {
        Position position = POSITION.get(enemy);
        float radius = BODY.get(enemy).radius;
        hunted.set(x, y);
        if (within(position, hunted, map.tileSize()) || solid.isClearPath(position.x, position.y, x, y, radius)) {
            towards(enemy, x, y);
            return;
        }
        int kind = STRAIN.get(enemy).kind.ordinal();
        int tile = map.rowAt(y) * map.cols() + map.colAt(x);
        if (placeClaim[kind] == -1) {
            placeClaim[kind] = tile;
            places[kind].rebuildFor(map.colAt(x), map.rowAt(y));
        }
        FlowField field = places[kind];
        if (placeClaim[kind] == tile
                && field.reachesFrom(position.x, position.y)
                && field.directionAt(position.x, position.y, direction)) {
            float speed = SPEED.get(enemy).value;
            STEERING.get(enemy).set(direction[0] * speed, direction[1] * speed);
            return;
        }
        towards(enemy, x, y);
    }

    /** Straight at a place, with nothing to say about what is between. */
    private static void towards(Entity enemy, float x, float y) {
        Position position = POSITION.get(enemy);
        float towardsX = x - position.x;
        float towardsY = y - position.y;
        float distance = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
        if (distance <= EPSILON) {
            return;
        }
        float speed = SPEED.get(enemy).value;
        STEERING.get(enemy).set(towardsX / distance * speed, towardsY / distance * speed);
    }

    /**
     * Walks a creature that has noticed nobody at the ground the level told its side to come for.
     *
     * <p><strong>Routed</strong>: the post never moves, so its field is built once per strain.
     */
    private void marchToThePost(Entity enemy) {
        if (posts.size() == 0) {
            return;
        }
        Position post = POSITION.get(posts.first());
        Position position = POSITION.get(enemy);
        float speed = SPEED.get(enemy).value;
        float radius = BODY.get(enemy).radius;
        if (within(position, post, map.tileSize()) || map.isClearPath(position.x, position.y, post.x, post.y, radius)) {
            straightAt(enemy, position, post, speed);
            return;
        }
        FlowField field = marches[STRAIN.get(enemy).kind.ordinal()];
        field.rebuildFor(map.colAt(post.x), map.rowAt(post.y));
        if (field.directionAt(position.x, position.y, direction)) {
            STEERING.get(enemy).set(direction[0] * speed, direction[1] * speed);
            return;
        }
        straightAt(enemy, position, post, speed);
    }

    /**
     * Straight at whatever it is going to eat.
     *
     * <p>No field: food is only chased from inside the shortest range in the game.
     */
    private static void foodAt(Entity enemy, Perception perception) {
        towards(enemy, perception.foodX, perception.foodY);
    }
}
