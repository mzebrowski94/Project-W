package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.Avoidance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Bends a unit's course around whatever it is about to run into, before it runs into it.
 *
 * <p><strong>Context steering.</strong> A fixed ring of candidate directions is scored twice: how much
 * the unit wanted to go that way, and how dangerous that way is. It goes the best-scoring way. The
 * alternative shape - feelers, and a turn when one is tripped - gets stuck in a concave arrangement,
 * which two obstacles and a wall make by accident, and cannot express "this way is bad but it is the
 * least bad".
 *
 * <p><strong>What makes it proactive is one multiply.</strong> Both parties are projected forward by
 * {@code lookaheadSeconds} before the gap between them is measured, so the unit is avoiding where
 * something is going to be rather than where it is. Something beside it and moving into its path is a
 * hazard; something dead ahead and moving away is not.
 *
 * <p><strong>It never avoids a pickup.</strong> A pickup is a thing you walk over, and one that walks
 * has to be solid to stay out of rock - so without this a hungry alien would steer politely around its
 * own dinner.
 *
 * <p><strong>It never avoids the other side.</strong> A hazard on an opposing side is what the unit is
 * trying to reach, and a grunt that swerved around the commander would never land a blow. Everything
 * else solid is avoided, its own kind and the bodies of its own kind included.
 *
 * <p>It adjusts a desired velocity and never sets one, so a unit nothing is driving stays undriven -
 * avoidance is an opinion about a course, not a reason to move.
 *
 * <p>It runs after navigation and wandering and before {@code DriveSystem}, which is the whole reason
 * those write a desired velocity rather than a velocity: this system has to be able to see what the
 * unit intended in order to adjust it.
 */
public final class AvoidanceSystem extends EntitySystem {

    /**
     * Twelve candidate directions, thirty degrees apart, as literals.
     *
     * <p>A table rather than a loop over an angle because {@code Math.sin} and {@code Math.cos} are
     * banned in the simulation - they are specified only to within an ulp and may differ between JVM
     * implementations, which is enough to make a replay stop replaying on somebody else's machine.
     * Twelve is enough resolution to slip past a circle and few enough to score for every unit every
     * tick without thinking about it.
     */
    private static final float[] DIRECTIONS = {
        1f,
        0f,
        0.8660254f,
        0.5f,
        0.5f,
        0.8660254f,
        0f,
        1f,
        -0.5f,
        0.8660254f,
        -0.8660254f,
        0.5f,
        -1f,
        0f,
        -0.8660254f,
        -0.5f,
        -0.5f,
        -0.8660254f,
        0f,
        -1f,
        0.5f,
        -0.8660254f,
        0.8660254f,
        -0.5f
    };

    private static final int SLOTS = DIRECTIONS.length / 2;
    private static final float EPSILON = 1e-4f;

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Avoidance> AVOIDANCE = ComponentMapper.getFor(Avoidance.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);

    /** Danger per candidate direction, refilled per unit. One array, reused, so nothing allocates. */
    private final float[] danger = new float[SLOTS];

    private ImmutableArray<Entity> steerers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> hazards = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        steerers = engine.getEntitiesFor(
                Family.all(Avoidance.class, Steering.class, Position.class, Velocity.class, Body.class, Faction.class)
                        .get());
        hazards = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Collider.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < steerers.size(); i++) {
            Entity entity = steerers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Avoidance avoidance = AVOIDANCE.get(entity);
            if (avoidance.enabled) {
                steer(entity, avoidance);
            }
        }
    }

    private void steer(Entity entity, Avoidance avoidance) {
        Steering steering = STEERING.get(entity);
        float speed = (float) Math.sqrt(steering.desiredX * steering.desiredX + steering.desiredY * steering.desiredY);
        if (speed <= EPSILON) {
            return;
        }
        float wantX = steering.desiredX / speed;
        float wantY = steering.desiredY / speed;
        if (!mapDanger(entity, avoidance)) {
            return;
        }
        int best = 0;
        float bestScore = -Float.MAX_VALUE;
        for (int slot = 0; slot < SLOTS; slot++) {
            float interest = DIRECTIONS[slot * 2] * wantX + DIRECTIONS[slot * 2 + 1] * wantY;
            float score = interest - avoidance.weight * danger[slot];
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        steering.set(DIRECTIONS[best * 2] * speed, DIRECTIONS[best * 2 + 1] * speed);
    }

    /**
     * Scores every candidate direction against everything worth avoiding, and reports whether anything
     * was.
     *
     * <p>False means the way ahead is clear, and the unit keeps exactly the course it was given -
     * which is what makes a straight line stay straight rather than drifting by a rounding error.
     */
    private boolean mapDanger(Entity entity, Avoidance avoidance) {
        for (int slot = 0; slot < SLOTS; slot++) {
            danger[slot] = 0f;
        }
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        float radius = BODY.get(entity).radius;
        Side side = FACTION.get(entity).side;
        float aheadX = position.x + velocity.x * avoidance.lookaheadSeconds;
        float aheadY = position.y + velocity.y * avoidance.lookaheadSeconds;
        boolean threatened = false;
        for (int i = 0; i < hazards.size(); i++) {
            Entity hazard = hazards.get(i);
            if (hazard == entity || !COLLIDER.get(hazard).solid || PICKUP.has(hazard) || opposed(side, hazard)) {
                continue;
            }
            Position hazardPosition = POSITION.get(hazard);
            float hazardAheadX = hazardPosition.x;
            float hazardAheadY = hazardPosition.y;
            if (VELOCITY.has(hazard)) {
                Velocity hazardVelocity = VELOCITY.get(hazard);
                hazardAheadX += hazardVelocity.x * avoidance.lookaheadSeconds;
                hazardAheadY += hazardVelocity.y * avoidance.lookaheadSeconds;
            }
            float towardsX = hazardAheadX - aheadX;
            float towardsY = hazardAheadY - aheadY;
            float distance = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
            if (distance <= EPSILON) {
                continue;
            }
            float gap = distance - radius - BODY.get(hazard).radius;
            if (gap >= avoidance.scanRange) {
                continue;
            }
            threatened = true;
            float closeness = gap <= 0f ? 1f : (avoidance.scanRange - gap) / avoidance.scanRange;
            float unitX = towardsX / distance;
            float unitY = towardsY / distance;
            for (int slot = 0; slot < SLOTS; slot++) {
                float alignment = DIRECTIONS[slot * 2] * unitX + DIRECTIONS[slot * 2 + 1] * unitY;
                if (alignment <= 0f) {
                    continue;
                }
                float threat = closeness * alignment;
                if (threat > danger[slot]) {
                    danger[slot] = threat;
                }
            }
        }
        return threatened;
    }

    /** On the other side, and therefore the thing being hunted rather than something in the way. */
    private static boolean opposed(Side side, Entity hazard) {
        if (side == Side.NEUTRAL || !FACTION.has(hazard)) {
            return false;
        }
        Side other = FACTION.get(hazard).side;
        return other != Side.NEUTRAL && other != side;
    }
}
