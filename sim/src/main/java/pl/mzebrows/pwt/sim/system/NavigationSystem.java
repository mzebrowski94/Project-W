package pl.mzebrows.pwt.sim.system;

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
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Asks every enemy that is hunting somebody to head for him at its own speed, round whatever is in
 * the way.
 *
 * <p>Who that is was decided by {@code PerceptionSystem} and published as a place (G0026); this
 * system walks the answer and never asks the question.
 *
 * <p>A unit that has been hurt and has not noticed him walks at <em>where the hit came from</em>
 * instead (G0016) - a place rather than whoever landed it, so nothing holds a reference to something
 * that may not last the tick.
 *
 * <p>Whether a unit is hunting at all, and whether it has something else in mind instead, are
 * {@code PerceptionSystem}'s answers rather than this system's. A unit hunting him is routed to him; a
 * unit that has not noticed him but has spotted something edible is walked at that; a unit with
 * neither is not spoken for here at all, asks for nothing, and is left to whatever drives an idle one.
 * A world with no commander in it still has units going to dinner, which is why nothing here gives up
 * early just because there is nobody to hunt.
 *
 * <p><strong>Routes are built for the commander only.</strong> A {@link FlowField} per strain,
 * rebuilt when he leaves the tile it was pointed at; anything hunting somebody else steers straight
 * and leans off what is in the way, exactly as a provoked unit does, because a field per man being
 * hunted would be a grid pass each per tick - the cost a flow field exists to avoid.
 *
 * <p>Each field is rebuilt only when the commander leaves the tile it was pointed at. One grid pass serves every unit of that strain, however many there are, and each
 * field is built for that strain's body radius - which is what lets a grunt take a one-tile crack that
 * a brute has to walk around, without either of them knowing there is a rule about it.
 *
 * <p><strong>A unit that can see him walks straight at him.</strong> The field is consulted only when
 * rock is actually in the way. Distances in it are four-connected, so its gradient in the open is
 * grid-shaped rather than straight, and a pack crossing an empty arena would converge onto the
 * commander's row and arrive along it. Checking the line first costs a march down it and buys back
 * the straight approach, which is also the one every existing arena was tuned around.
 *
 * <p><strong>The last of the approach is steered straight at him too.</strong> A field flattens out at
 * its own goal, so once a unit is on the commander's tile it reports nothing useful; that is also the
 * honest answer when a body that size cannot reach him at all, and walking at him and being stopped by
 * rock is the right behaviour for a brute facing a crack he has backed into.
 *
 * <p>It writes a desired velocity rather than a velocity, so what it asks for is one opinion among
 * several rather than the last word. {@code DriveSystem} commits it; a unit this system says nothing
 * about asks for nothing and stands still, which is why there is no halt-everything branch here.
 *
 * <p>Closing the last of the distance is {@code EnemyLeapSystem}'s business, and it runs after
 * {@code DriveSystem} and overwrites the velocity of anything mid-leap outright. Neither system knows
 * about the other.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because the target is resolved
 * once per tick rather than once per enemy.
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

    /**
     * One field per strain, indexed by ordinal for the same reason {@code Defs} is: the array is built
     * from the enum's own constants in the process that reads it, so reordering the enum reorders the
     * array with it and nothing is persisted against the numbers.
     */
    private final FlowField[] fields;

    /**
     * One field per strain pointed at the post, built once. The post never moves, so a rebuild would
     * be a whole grid pass for an answer that cannot have changed.
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

    public NavigationSystem(SimConfig config, TileMap map) {
        this.map = map;
        this.fields = new FlowField[UnitKind.values().length];
        this.marches = new FlowField[UnitKind.values().length];
        for (UnitKind kind : UnitKind.values()) {
            fields[kind.ordinal()] = FlowField.over(map, config.unit(kind).radius());
            marches[kind.ordinal()] = FlowField.over(map, config.unit(kind).radius());
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        posts = engine.getEntitiesFor(Family.all(Post.class, Position.class).get());
        enemies = engine.getEntitiesFor(
                Family.all(Enemy.class, Position.class, Steering.class, Speed.class, Perception.class, Strain.class)
                        .get());
        targets = engine.getEntitiesFor(
                Family.all(Faction.class, Health.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        int goals = collectGoals();
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            Perception perception = PERCEPTION.get(enemy);
            if (Drivers.held(enemy) || !perception.occupied()) {
                continue;
            }
            // A shooter that holds ground is a place on the field rather than a pursuer, and "place"
            // is the operative word: it moves for somewhere to be and never for somebody to follow.
            //
            // It answers a provocation, because that is somewhere to be (G0016) - somewhere it was
            // shot from, or a point being taken off it. And it moves up to its own firing range
            // once, because the gap between noticing you and being able to shoot you is ground it
            // would otherwise never cross: it notices at thirty and shoots at eighteen. From its
            // first shot onwards it holds the position it took, however far you go.
            boolean rooted = RANGED.has(enemy) && RANGED.get(enemy).enabled && RANGED.get(enemy).holdsGround;
            if (!perception.aggro && perception.provoked) {
                towards(enemy, perception.provokedX, perception.provokedY);
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
            // Where whatever it is hunting was standing this tick, published by the one system that
            // decides who that is (G0026). A place, never a reference (G0010).
            hunted.set(perception.targetX, perception.targetY);
            Position target = hunted;
            if (rooted && !RANGED.get(enemy).takingUpPosition(distanceBetween(POSITION.get(enemy), target))) {
                // It has opened fire on him, or it is already close enough to. Either way it holds
                // where it is standing and he is not followed - which is the whole of what makes a
                // shooter ground to cross rather than a thing to outrun.
                continue;
            }
            Position position = POSITION.get(enemy);
            float speed = SPEED.get(enemy).value;
            float radius = BODY.get(enemy).radius;
            // Its own line in, taken after the shooter's question above and before anything is
            // steered: a rooted shooter asks how far away the man actually is, and everything else
            // walks at a point of its own beside him so a pack arrives abreast rather than in a
            // queue. The offset is spent by the time anything is close, so the last of the approach
            // is straight at him whatever this did.
            spreadOut(enemy, position, hunted);
            if (within(position, target, map.tileSize())
                    || map.isClearPath(position.x, position.y, target.x, target.y, radius)) {
                straightAt(enemy, position, target, speed);
                continue;
            }
            // One field per strain, flooded from every man on the player's side at once. A
            // multi-source flood costs what a single-source one costs, so routing to whoever is
            // nearest BY ROUTE is the same grid pass as routing to one named man - and it is what
            // G0026 says happens, which until now was only true by line. What it replaced was a
            // field built for the commander alone: anything hunting somebody else walked straight at
            // them, which on a level with rock in it meant walking into the rock and staying there.
            //
            // The field leads to the nearest of them and the published target names one of them; the
            // two agree except in a corner, and the last of the approach below is steered at the man
            // rather than by the grid either way.
            FlowField field = fields[STRAIN.get(enemy).kind.ordinal()];
            field.rebuildFor(goalCols, goalRows, goals);
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
     * <p>Collected once and handed to every strain's field, which rebuilds only if the set has
     * changed - so a squad standing still costs nothing at all, and a squad on the move costs one
     * grid pass per strain, which is what it cost when the goal was the commander alone.
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
     * <p><strong>The goal moves, not the course.</strong> Everything hunting one man was steered at
     * one point, and a flow field's gradient in the open is grid-shaped, so a pack crossing open
     * ground converged onto his row and came down it in single file. Avoidance cannot answer that -
     * it is an opinion about what is in the way, and what is in the way is another creature heading
     * for the identical spot. A different spot each makes the problem stop existing.
     *
     * <p>Which side and how wide is {@code Approach.bearing}, seeded from where the creature was
     * spawned: nothing here is drawn from the {@code Rng}, so a pack fans out the same way on every
     * run (G0012's argument, applied to an approach).
     *
     * <p>It mutates the scratch position the caller is about to steer by, and only that - the goals
     * the flow field is flooded from are still the men themselves, so a creature that has to come
     * round a wall is routed to him and takes its own line again on the open side of it.
     *
     * <p>A trooper walking to a point or after the commander uses it too (G0041), which is why it is
     * reachable from {@code TrooperMoveSystem}: one spread, not a second way of doing it.
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
        // Perpendicular to the line in, which is what keeps the offset a width rather than a
        // distance: it never brings the creature closer or sends it further away, only wide.
        target.set(target.x - toTargetY / distance * swing, target.y + toTargetX / distance * swing);
    }

    /**
     * Whether the two are close enough that the grid has nothing left to say.
     *
     * <p>Inside a tile a field is not merely useless but wrong: it points at the goal <em>tile</em>,
     * so a unit standing on the commander would be steered at that tile's centre and orbit him a
     * couple of units out. The last tile of an approach is aimed at the man, not at the square he is
     * standing in.
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
     * Straight at a place something is cross about (G0016).
     *
     * <p><strong>No flow field for this one</strong>, and that is a decision rather than an
     * oversight. A field is built per goal and rebuilt when the goal moves; provoked units are each
     * cross about a <em>different</em> place, so routing them properly would mean a grid pass per
     * unit per tick - the exact per-unit cost a flow field exists to avoid. Straight-line steering
     * plus avoidance is what the forage path already does for the same reason.
     *
     * <p>The cost is that a unit provoked from the far side of a wall walks into the wall and mills
     * about there rather than coming round. At the ranges a fight actually happens at that is rare,
     * and it is listed with the other accepted limits. It stops being acceptable the moment
     * something can provoke from across the level.
     */
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
     * <p><strong>Routed, unlike everything else that is not the commander.</strong> A post is one
     * goal for the whole level and it never moves, so its field is built once per strain and then
     * answers every marching unit for the rest of the run - the case a flow field is exactly right
     * for, and the reason a level can send a wave in from a corner and have it arrive round the rock
     * instead of milling against it.
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
     * <p>No field for this one. Food is only ever chased from inside the secondary range, the shortest
     * range in the game, so a grid pass would be answering a question about the next few world units -
     * and a unit that cannot reach a mushroom round a corner simply stands beside the rock and gives up
     * on it, which is the right amount of stupid for something that is not hunting anybody.
     */
    private static void foodAt(Entity enemy, Perception perception) {
        towards(enemy, perception.foodX, perception.foodY);
    }
}
