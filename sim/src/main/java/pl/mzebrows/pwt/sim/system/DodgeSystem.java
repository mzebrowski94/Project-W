package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dodge;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Sidesteps a shot that is going to hit, in a way a player can learn.
 *
 * <p><strong>Nothing here is random, and that is the mechanic.</strong> The sidestep is perpendicular
 * to the shot and goes to whichever side of the shot's line the creature is already on - so the same
 * shot from the same place always gets the same answer, and leading a rabbit is a skill rather than a
 * coin toss. A seeded wobble would have been easy and would have made every miss read as bad luck.
 *
 * <p>What stops that being trivial is {@code commitSeconds}: once it has broken one way it cannot
 * answer anything else until the window closes, so the counter to a dodge is a second shot rather than
 * a perfect first one.
 *
 * <p><strong>It runs after avoidance and is the last word on a course.</strong> A dodge bent around a
 * rock would not be the same dodge twice, and a promise the terrain can break is not a promise. The
 * cost is that a cornered creature will sidestep into a wall - which is what being cornered should
 * cost it.
 *
 * <p>Threat is a closest-approach test rather than an overlap: where the shot <em>will</em> pass, not
 * where it is. A shot already past is ignored however close it is, which is why a creature does not
 * flinch at something that has already missed.
 */
public final class DodgeSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Dodge> DODGE = ComponentMapper.getFor(Dodge.class);

    private static final float EPSILON = 1e-4f;

    private ImmutableArray<Entity> dodgers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> shots = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        dodgers = engine.getEntitiesFor(Family.all(Dodge.class, Position.class, Body.class, Steering.class)
                .get());
        shots = engine.getEntitiesFor(Family.all(Projectile.class, Position.class, Velocity.class, Body.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < dodgers.size(); i++) {
            Entity entity = dodgers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Dodge dodge = DODGE.get(entity);
            if (!dodge.enabled) {
                continue;
            }
            if (dodge.breaking()) {
                STEERING.get(entity).set(dodge.directionX * dodge.speed, dodge.directionY * dodge.speed);
                continue;
            }
            if (dodge.committed()) {
                continue;
            }
            answer(entity, dodge);
        }
    }

    /** Breaks away from the first shot on course to hit, or does nothing if none is. */
    private void answer(Entity entity, Dodge dodge) {
        Position position = POSITION.get(entity);
        float radius = BODY.get(entity).radius;
        for (int i = 0; i < shots.size(); i++) {
            Entity shot = shots.get(i);
            Position shotPosition = POSITION.get(shot);
            Velocity shotVelocity = VELOCITY.get(shot);
            float awayX = position.x - shotPosition.x;
            float awayY = position.y - shotPosition.y;
            float closing = awayX * shotVelocity.x + awayY * shotVelocity.y;
            if (closing <= 0f) {
                continue;
            }
            float shotSpeedSquared = shotVelocity.x * shotVelocity.x + shotVelocity.y * shotVelocity.y;
            if (shotSpeedSquared <= EPSILON) {
                continue;
            }
            float range = dodge.reactionRange;
            if (awayX * awayX + awayY * awayY > range * range) {
                continue;
            }
            float atClosest = closing / shotSpeedSquared;
            float missX = awayX - shotVelocity.x * atClosest;
            float missY = awayY - shotVelocity.y * atClosest;
            float clearance = radius + BODY.get(shot).radius + dodge.margin;
            if (missX * missX + missY * missY > clearance * clearance) {
                continue;
            }
            breakAside(dodge, awayX, awayY, shotVelocity);
            return;
        }
    }

    /**
     * Perpendicular to the shot, on the side the creature is already on.
     *
     * <p>The cross product of the shot's heading with the line to the creature is the signed distance
     * off that line, so its sign <em>is</em> the answer - no branch on quadrant, no angle, and nothing
     * that could differ between machines. A creature dead on the line has no side to be on; it breaks
     * left, always, because a fixed answer is still a learnable one and a coin toss is not.
     */
    private static void breakAside(Dodge dodge, float awayX, float awayY, Velocity shotVelocity) {
        float shotSpeed = (float) Math.sqrt(shotVelocity.x * shotVelocity.x + shotVelocity.y * shotVelocity.y);
        float headingX = shotVelocity.x / shotSpeed;
        float headingY = shotVelocity.y / shotSpeed;
        float side = headingX * awayY - headingY * awayX;
        if (side >= 0f) {
            dodge.breakAway(-headingY, headingX);
        } else {
            dodge.breakAway(headingY, -headingX);
        }
    }
}
