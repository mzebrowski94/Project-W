package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Approach;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Compass;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * Walks each trooper towards whatever it has been given to head for, sweeping its attention from side
 * to side as it goes.
 *
 * <p><strong>Urgency blends the route against the sweep, and never the speed</strong> - "forward"
 * changes speed. The sweep is "looking around": its look is written from the lean, not the walk. How
 * long a lean lasts is drawn from the squad's stream, so a squad does not sweep in lockstep.
 *
 * <p><strong>A shut door in his way stops him</strong> (G0069): the first man at arm's length is the one it goes to
 * ({@code DoorSystem}) and stands at it, facing it; every other man whose way it shuts stands where he is and
 * watches his sector. His route still runs through it, as every route does (ADR 0022).
 *
 * <p>Writes a desired velocity; {@code DriveSystem} commits it.
 */
public final class TrooperMoveSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Approach> APPROACH = ComponentMapper.getFor(Approach.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);

    private static final float EPSILON = 1e-4f;

    /** What {@link #headingTowards} found: nothing to walk to, a straight line, or the route. */
    private static final int NOWHERE = 0;

    private static final int STRAIGHT = 1;

    private static final int BY_ROUTE = 2;

    /** A turn of a hundred and twenty degrees, as its cosine and sine: back off and across. */
    private static final float DETOUR_COS = -0.5f;

    private static final float DETOUR_SIN = 0.8660254f;

    /** Rejection sampling inside the unit circle succeeds about 79% of the time; this never runs out. */
    private static final int BEARING_ATTEMPTS = 8;

    private final TileMap map;

    /** Whether a tile is solid now, for the one question of a way shut by a door. */
    private final Solid solid;

    private final Rng rng;
    private final float urgency;
    private final float scanSpread;
    private final float scanMinSeconds;
    private final float scanMaxSeconds;
    private final float forwardSpeedFactor;
    private final float followDistance;
    private final float walkingSpeed;
    private final int blockTicks;
    private final float blockDistance;
    private final int detourTicks;

    /** How far ahead a shut door shuts his way (G0069). */
    private final float doorWait;

    /** One field, re-aimed at whatever a trooper is currently walking to. */
    private final FlowField route;

    /** Scratch for one direction, so sampling a field costs no allocation per unit per tick. */
    private final float[] direction = new float[2];

    /** Scratch for the point beside where it is going, so spreading out costs no allocation either. */
    private final Position goal = new Position();

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());

    public TrooperMoveSystem(SimConfig config, Solid solid, Rng rng) {
        this.solid = solid;
        this.map = solid.map();
        this.doorWait = config.door().waitDistance();
        this.rng = rng;
        SimConfig.TrooperConfig trooper = config.trooper();
        this.urgency = trooper.urgency();
        this.scanSpread = trooper.scanSpread();
        this.scanMinSeconds = trooper.scanMinSeconds();
        this.scanMaxSeconds = trooper.scanMaxSeconds();
        this.forwardSpeedFactor = trooper.forwardSpeedFactor();
        this.followDistance = trooper.followDistance();
        this.walkingSpeed = trooper.speed();
        this.blockTicks = Ticks.of(trooper.blockSeconds(), config.tickSeconds());
        this.blockDistance = trooper.blockDistance();
        this.detourTicks = Ticks.of(trooper.detourSeconds(), config.tickSeconds());
        this.route = FlowField.over(this.map, trooper.radius());
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers = engine.getEntitiesFor(Family.all(Trooper.class, Position.class, Steering.class, Speed.class)
                .get());
        doors = engine.getEntitiesFor(Family.all(Door.class, Handle.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            Trooper trooper = TROOPER.get(entity);
            // Asked afresh every tick he walks; a man standing, fighting or held has no door in his way.
            trooper.door = Handle.NOBODY;
            if (Drivers.held(entity)) {
                continue;
            }
            sweep(trooper, deltaTime);
            if (trooper.mode == Trooper.Mode.HOLD
                    || trooper.mode == Trooper.Mode.SECURE
                    || (trooper.mode == Trooper.Mode.AID && !trooper.hasTarget)) {
                // Holding, securing, or standing over somebody: asks for no velocity, still watching.
                keepWatching(trooper, POSITION.get(entity));
                continue;
            }
            if (!trooper.hasTarget) {
                trooper.sampling = false;
                continue;
            }
            Position at = POSITION.get(entity);
            if (trooper.mode == Trooper.Mode.FIGHT && !trooper.closing) {
                // 1.d: stands still to shoot, facing its target; a fresh bearing when the fight ends.
                trooper.settled = false;
                trooper.sampling = false;
                aimAt(trooper, at);
                continue;
            }
            // Past here a man in a fight is closing in: straight at it, eyes on it, at his own pace.
            boolean closingIn = trooper.mode == Trooper.Mode.FIGHT;
            int way = headingTowards(entity, at, trooper);
            if (way == NOWHERE) {
                trooper.sampling = false;
                continue;
            }
            // A walk of the grid ahead of him, skipped on a level with no doors at all.
            trooper.door = doors.size() == 0
                    ? Handle.NOBODY
                    : solid.doorAhead(at.x, at.y, direction[0], direction[1], doorWait);
            if (trooper.door != Handle.NOBODY && waitsAtTheDoor(entity, trooper, at)) {
                continue;
            }
            if (trooper.mode == Trooper.Mode.FOLLOW && closeEnoughToTheCommander(at, trooper)) {
                // 3.a: a loose formation, still watching while it waits.
                keepWatching(trooper, at);
                continue;
            }
            trooper.settled = false;
            trooper.baseLookX = direction[0];
            trooper.baseLookY = direction[1];
            watchForABlock(trooper, at);
            if (trooper.detourRemaining > 0) {
                trooper.detourRemaining--;
                sidestep(entity, trooper);
            } else if (way == STRAIGHT && !closingIn) {
                // Only in the open: on the route a lean is a lean into the walls.
                lean(trooper);
            }
            trooper.lookX = direction[0];
            trooper.lookY = direction[1];
            float speed = SPEED.get(entity).value * (closingIn ? 1f : pace(trooper, deltaTime));
            STEERING.get(entity).set(direction[0] * speed, direction[1] * speed);
        }
    }

    /**
     * Whether a man whose way a shut door shuts stands rather than walks, and which way he looks if he does
     * (G0069): the man it went to stands at it facing it, as does the first to reach it before it has gone to
     * anybody; a man it went to somebody else stands where he is and watches his sector. A man still short of arm's
     * length of a door nobody has walks on up to it.
     */
    private boolean waitsAtTheDoor(Entity entity, Trooper trooper, Position at) {
        Entity door = Handles.resolve(doors, trooper.door);
        if (door == null || !ACTION.has(entity)) {
            return false;
        }
        long opener = DOOR.get(door).trooper;
        boolean atIt = Reach.doorGap(entity, door, map.tileSize()) <= ACTION.get(entity).reach;
        if (opener != Handle.NOBODY && opener != Handles.of(entity)) {
            keepWatching(trooper, at);
            return true;
        }
        if (!atIt) {
            return false;
        }
        trooper.sampling = false;
        trooper.settled = false;
        trooper.look(direction[0], direction[1]);
        return true;
    }

    /**
     * Goes on watching without moving: the sweep leans off the direction it was last looking.
     */
    private void keepWatching(Trooper trooper, Position at) {
        trooper.sampling = false;
        if (trooper.alertRemaining > 0) {
            turnTowardsTheHit(trooper, at);
        } else if (trooper.sectorSpan > 0) {
            watchItsSector(trooper);
        } else {
            takeABearing(trooper);
        }
        direction[0] = trooper.baseLookX;
        direction[1] = trooper.baseLookY;
        lean(trooper);
        trooper.lookX = direction[0];
        trooper.lookY = direction[1];
    }

    /**
     * Faces where the last hit came from while it is alert; the mind forgives his arc as long.
     */
    private static void turnTowardsTheHit(Trooper trooper, Position at) {
        float toX = trooper.alertX - at.x;
        float toY = trooper.alertY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        trooper.settled = false;
        trooper.baseLookX = toX / distance;
        trooper.baseLookY = toY / distance;
    }

    /**
     * Looks along its own sector of the circle, one slot further each time the sweep turns over, and
     * back again at the end of the run rather than jumping across it.
     *
     * <p>The sector is handed out by {@code TrooperMindSystem}; the sweep's lean still applies on top.
     */
    private static void watchItsSector(Trooper trooper) {
        int span = trooper.sectorSpan;
        int offset = 0;
        if (span > 1) {
            int cycle = 2 * span - 2;
            int phase = Math.floorMod(trooper.sectorStep, cycle);
            offset = phase < span ? phase : cycle - phase;
        }
        int slot = trooper.sectorFrom + offset;
        trooper.settled = true;
        trooper.baseLookX = Compass.x(slot);
        trooper.baseLookY = Compass.y(slot);
    }

    /**
     * Gives a trooper that has just stopped somewhere of its own to watch.
     *
     * <p><strong>Once per stop, and different for each man</strong>, from the squad's seeded stream.
     * Rejection sampled inside the unit circle: no trigonometry in {@code sim}.
     */
    private void takeABearing(Trooper trooper) {
        if (trooper.settled) {
            return;
        }
        trooper.settled = true;
        for (int attempt = 0; attempt < BEARING_ATTEMPTS; attempt++) {
            float x = rng.nextFloat(-1f, 1f);
            float y = rng.nextFloat(-1f, 1f);
            float lengthSquared = x * x + y * y;
            if (lengthSquared > EPSILON && lengthSquared <= 1f) {
                float scale = 1f / (float) Math.sqrt(lengthSquared);
                trooper.baseLookX = x * scale;
                trooper.baseLookY = y * scale;
                return;
            }
        }
        // Vanishingly unlikely. Keeping the direction it had is better than pointing at nothing.
    }

    /**
     * How much faster it is walking than usual, and spends the push as it goes.
     *
     * <p>Charged for ground covered, not time elapsed.
     */
    private float pace(Trooper trooper, float deltaTime) {
        if (!trooper.pushingForward()) {
            return 1f;
        }
        trooper.forwardRemaining -= walkingSpeed * forwardSpeedFactor * deltaTime;
        return forwardSpeedFactor;
    }

    /** Close enough to the commander that a follower stops crowding him. */
    private boolean closeEnoughToTheCommander(Position at, Trooper trooper) {
        return Sight.withinRange(at.x, at.y, trooper.targetX, trooper.targetY, followDistance);
    }

    /**
     * Turns to face what it is shooting at, without moving.
     *
     * <p>Its look is written even though it stands still, so it faces where its bullets go.
     */
    private static void aimAt(Trooper trooper, Position at) {
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        trooper.look(toX / distance, toY / distance);
    }

    /**
     * Counts down to the next sweep and flips which way it is leaning.
     *
     * <p>Runs for a held trooper too, so a squad's sweeps do not reset together.
     */
    private void sweep(Trooper trooper, float deltaTime) {
        trooper.scanRemaining--;
        if (trooper.scanRemaining > 0) {
            return;
        }
        trooper.scanSide = -trooper.scanSide;
        trooper.sectorStep++;
        trooper.scanRemaining = Ticks.of(rng.nextFloat(scanMinSeconds, scanMaxSeconds), deltaTime);
    }

    /**
     * Writes the unit direction to walk into {@link #direction}, and reports how it got it: straight
     * at the place, by the route, or {@link #NOWHERE} when it is already standing on it.
     *
     * <p>Straight when the way is clear, by the grid when not, as {@code NavigationSystem}.
     */
    private int headingTowards(Entity entity, Position at, Trooper trooper) {
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        float radius = BODY.get(entity).radius;
        if (distance <= EPSILON) {
            return NOWHERE;
        }
        if (walksAbreast(trooper)) {
            // The pack's spread (G0033, G0041), only while the line to that point is clear.
            goal.set(trooper.targetX, trooper.targetY);
            NavigationSystem.spreadOut(entity, at, goal);
            float toGoalX = goal.x - at.x;
            float toGoalY = goal.y - at.y;
            float toGoal = (float) Math.sqrt(toGoalX * toGoalX + toGoalY * toGoalY);
            if (toGoal > EPSILON
                    && (goal.x != trooper.targetX || goal.y != trooper.targetY)
                    && map.isClearPath(at.x, at.y, goal.x, goal.y, radius)) {
                direction[0] = toGoalX / toGoal;
                direction[1] = toGoalY / toGoal;
                return STRAIGHT;
            }
        }
        if (distance <= map.tileSize() || map.isClearPath(at.x, at.y, trooper.targetX, trooper.targetY, radius)) {
            direction[0] = toX / distance;
            direction[1] = toY / distance;
            return STRAIGHT;
        }
        route.rebuildFor(map.colAt(trooper.targetX), map.rowAt(trooper.targetY));
        if (route.directionAt(at.x, at.y, direction)) {
            return BY_ROUTE;
        }
        direction[0] = toX / distance;
        direction[1] = toY / distance;
        return BY_ROUTE;
    }

    /**
     * Notices a man who is walking and getting nowhere, and sends him off to one side for a moment.
     *
     * <p><strong>Block detection</strong>: less than the block distance in the block time is stuck. The
     * route would give the same answer again, so the answer is a short detour.
     */
    private void watchForABlock(Trooper trooper, Position at) {
        if (blockTicks <= 0) {
            return;
        }
        if (!trooper.sampling) {
            trooper.sampling = true;
            trooper.sampleX = at.x;
            trooper.sampleY = at.y;
            trooper.sampleClock = 0;
            return;
        }
        trooper.sampleClock++;
        if (trooper.sampleClock < blockTicks) {
            return;
        }
        boolean stuck = Sight.withinRange(at.x, at.y, trooper.sampleX, trooper.sampleY, blockDistance)
                && trooper.detourRemaining <= 0;
        trooper.sampleX = at.x;
        trooper.sampleY = at.y;
        trooper.sampleClock = 0;
        if (stuck) {
            trooper.detourRemaining = detourTicks;
            trooper.detours++;
        }
    }

    /**
     * Turns the way he wanted to go two thirds of the way round, to one side: back off and across,
     * out of whatever he walked into.
     *
     * <p>The side is seeded at spawn and alternates with every detour; nothing is drawn.
     */
    private void sidestep(Entity entity, Trooper trooper) {
        float own = APPROACH.has(entity) && APPROACH.get(entity).bearing < 0f ? -1f : 1f;
        float side = (trooper.detours & 1) == 1 ? own : -own;
        float wantX = direction[0];
        float wantY = direction[1];
        direction[0] = wantX * DETOUR_COS - wantY * DETOUR_SIN * side;
        direction[1] = wantX * DETOUR_SIN * side + wantY * DETOUR_COS;
    }

    /**
     * Whether this walk is one a squad makes together: to a point, or after the commander. Roaming,
     * going to a casualty and going to a gun are each a man's own errand, to a place of his own.
     */
    private static boolean walksAbreast(Trooper trooper) {
        return trooper.mode == Trooper.Mode.ADVANCE || trooper.mode == Trooper.Mode.FOLLOW;
    }

    /**
     * Leans the direction off the route by however much urgency leaves over.
     *
     * <p>Perpendicular to the route, so the trooper keeps making ground while it looks.
     */
    private void lean(Trooper trooper) {
        float lateral = scanSpread * (1f - urgency) * trooper.scanSide;
        if (lateral == 0f) {
            return;
        }
        float leanedX = direction[0] - direction[1] * lateral;
        float leanedY = direction[1] + direction[0] * lateral;
        float length = (float) Math.sqrt(leanedX * leanedX + leanedY * leanedY);
        if (length <= EPSILON) {
            return;
        }
        direction[0] = leanedX / length;
        direction[1] = leanedY / length;
    }
}
