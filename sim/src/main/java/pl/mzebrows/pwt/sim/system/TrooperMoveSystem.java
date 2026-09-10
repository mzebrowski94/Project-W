package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * Walks each trooper towards whatever it has been given to head for, sweeping its attention from side
 * to side as it goes.
 *
 * <p><strong>Urgency blends the route against the sweep, and never the speed.</strong> At one a
 * trooper makes a beeline; at zero it weaves so hard it barely advances. Speed is deliberately left
 * alone, because "move out" is the thing that changes speed - one dial doing both would make that
 * order invisible, which is the sort of overlap that quietly removes a mechanic.
 *
 * <p>The sweep is what "looking around" actually is. Leaning off the route turns the trooper, and its
 * facing is written from the lean rather than from where it happens to be walking - so a trooper that
 * stops still has somewhere it is looking, which is what the stand-still order will need.
 *
 * <p>How long a lean lasts is drawn from the squad's own stream between two bounds, so a squad does
 * not sweep in lockstep. Sweeping together reads as a chorus line rather than as men watching their
 * own arcs, and it is the sort of thing only visible by running it.
 *
 * <p>It writes a desired velocity rather than a velocity, so avoidance still gets a say and
 * {@code DriveSystem} has the last word.
 */
public final class TrooperMoveSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private static final float EPSILON = 1e-4f;

    /** Rejection sampling inside the unit circle succeeds about 79% of the time; this never runs out. */
    private static final int BEARING_ATTEMPTS = 8;

    private final TileMap map;
    private final Rng rng;
    private final float urgency;
    private final float scanSpread;
    private final float scanMinSeconds;
    private final float scanMaxSeconds;
    private final float moveOutSpeedFactor;
    private final float followDistance;
    private final float walkingSpeed;

    /** One field, re-aimed at whatever a trooper is currently walking to. */
    private final FlowField route;

    /** Scratch for one direction, so sampling a field costs no allocation per unit per tick. */
    private final float[] direction = new float[2];

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());

    public TrooperMoveSystem(SimConfig config, TileMap map, Rng rng) {
        this.map = map;
        this.rng = rng;
        SimConfig.TrooperConfig trooper = config.trooper();
        this.urgency = trooper.urgency();
        this.scanSpread = trooper.scanSpread();
        this.scanMinSeconds = trooper.scanMinSeconds();
        this.scanMaxSeconds = trooper.scanMaxSeconds();
        this.moveOutSpeedFactor = trooper.moveOutSpeedFactor();
        this.followDistance = trooper.followDistance();
        this.walkingSpeed = trooper.speed();
        this.route = FlowField.over(map, trooper.radius());
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers = engine.getEntitiesFor(Family.all(Trooper.class, Position.class, Steering.class, Speed.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            sweep(trooper, deltaTime);
            if (trooper.mode == Trooper.Mode.HOLD || trooper.mode == Trooper.Mode.SECURE) {
                // 3.c, and a squad holding a point: still watching. It asks for no velocity at all
                // rather than for a zero one, so DriveSystem stops it and a shove still moves it.
                keepWatching(trooper);
                continue;
            }
            if (!trooper.hasTarget) {
                continue;
            }
            Position at = POSITION.get(entity);
            if (trooper.mode == Trooper.Mode.FIGHT) {
                // 1.d: a trooper stands still to shoot, and looks straight at what it is shooting
                // rather than sweeping past it. It has stopped watching its own arc, so when the
                // fight ends it takes a fresh bearing rather than resuming the one it had before.
                trooper.settled = false;
                aimAt(trooper, at);
                continue;
            }
            if (!headingTowards(entity, at, trooper)) {
                continue;
            }
            if (trooper.mode == Trooper.Mode.FOLLOW && closeEnoughToTheCommander(at, trooper)) {
                // 3.a: a loose formation, not a queue on his heels. Close enough is close enough, and
                // it goes on watching while it waits.
                keepWatching(trooper);
                continue;
            }
            trooper.settled = false;
            trooper.baseLookX = direction[0];
            trooper.baseLookY = direction[1];
            lean(trooper);
            trooper.lookX = direction[0];
            trooper.lookY = direction[1];
            float speed = SPEED.get(entity).value * pace(trooper, deltaTime);
            STEERING.get(entity).set(direction[0] * speed, direction[1] * speed);
        }
    }

    /**
     * Goes on watching without moving: the sweep leans off the direction it was last looking, rather
     * than off wherever it happens to be walking, because it is not walking.
     *
     * <p>This is the reason a trooper carries a look direction of its own instead of inheriting one
     * from its velocity. A unit told to hold that kept pointing wherever it last moved would be a
     * unit staring at the ground it came from.
     */
    private void keepWatching(Trooper trooper) {
        takeABearing(trooper);
        direction[0] = trooper.baseLookX;
        direction[1] = trooper.baseLookY;
        lean(trooper);
        trooper.lookX = direction[0];
        trooper.lookY = direction[1];
    }

    /**
     * Gives a trooper that has just stopped somewhere of its own to watch.
     *
     * <p><strong>Once per stop, and different for each man.</strong> Keeping the direction it was
     * walking in means a squad following the commander all faces him and a squad on a point all faces
     * its middle - four men looking at the same thing, which is the opposite of a squad covering its
     * surroundings. A bearing each, drawn from the squad's seeded stream, reads as men watching their
     * own arcs and still replays identically (pillar 6).
     *
     * <p>Rejection sampled inside the unit circle rather than taken from an angle, because turning an
     * angle into a direction needs trigonometry and {@code ArchitectureTest} bans it here.
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
     * <p>Move out buys a <em>distance</em>, so it is charged for ground covered rather than for time
     * elapsed. A squad told to push on and then pinned by a fight gets the whole push afterwards,
     * which is what the order would look like it promised.
     */
    private float pace(Trooper trooper, float deltaTime) {
        if (!trooper.movingOut()) {
            return 1f;
        }
        trooper.moveOutRemaining -= walkingSpeed * moveOutSpeedFactor * deltaTime;
        return moveOutSpeedFactor;
    }

    /** Close enough to the commander that a follower stops crowding him. */
    private boolean closeEnoughToTheCommander(Position at, Trooper trooper) {
        return Sight.withinRange(at.x, at.y, trooper.targetX, trooper.targetY, followDistance);
    }

    /**
     * Turns to face what it is shooting at, without moving.
     *
     * <p>Its facing is written even though it is standing still, which is the point of a trooper
     * owning a look direction rather than inheriting one from its velocity: a stationary unit that
     * kept pointing wherever it was last walking would be aiming somewhere other than where its
     * bullets went.
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
     * <p>Runs for a held trooper too - being knocked down does not stop you looking about, and it
     * keeps a squad's sweeps from all resetting together when a grenade goes off among them.
     */
    private void sweep(Trooper trooper, float deltaTime) {
        trooper.scanRemaining -= deltaTime;
        if (trooper.scanRemaining > 0f) {
            return;
        }
        trooper.scanSide = -trooper.scanSide;
        trooper.scanRemaining = rng.nextFloat(scanMinSeconds, scanMaxSeconds);
    }

    /**
     * Writes the unit direction to walk into {@link #direction}, and reports whether there was one.
     *
     * <p>Straight at it when the way is clear, and by the grid when it is not - the same order the
     * enemies' navigation uses, and for the same reason: a four-connected field's gradient in the
     * open is grid-shaped, so a squad crossing an empty arena would converge onto one row and walk
     * along it.
     */
    private boolean headingTowards(Entity entity, Position at, Trooper trooper) {
        float toX = trooper.targetX - at.x;
        float toY = trooper.targetY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        float radius = BODY.get(entity).radius;
        if (distance <= EPSILON) {
            return false;
        }
        if (distance <= map.tileSize() || map.isClearPath(at.x, at.y, trooper.targetX, trooper.targetY, radius)) {
            direction[0] = toX / distance;
            direction[1] = toY / distance;
            return true;
        }
        route.rebuildFor(map.colAt(trooper.targetX), map.rowAt(trooper.targetY));
        if (route.directionAt(at.x, at.y, direction)) {
            return true;
        }
        direction[0] = toX / distance;
        direction[1] = toY / distance;
        return true;
    }

    /**
     * Leans the direction off the route by however much urgency leaves over.
     *
     * <p>The lean is perpendicular to the route, so the trooper keeps making ground while it looks:
     * a sweep that pointed it away from the objective would be a patrol rather than an advance.
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
