package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Throws a unit at the commander once he is close enough, and owns its velocity while it is in the
 * air.
 *
 * <p>Runs after {@code EnemyChaseSystem} on purpose: chasing writes a velocity for every enemy every
 * tick, and this overwrites it for the ones that are doing something more interesting. That ordering
 * is why neither system needs to know the other exists.
 *
 * <p>Three things here are the mechanic rather than the implementation, and none of them should be
 * tidied away:
 *
 * <ul>
 *   <li><strong>The hesitation is drawn from the simulation's seeded {@link Rng}.</strong> It varies
 *       between leaps so the player has to watch rather than count, and it does not vary between runs
 *       so a replay still matches. Any other source of variety here breaks pillar 6.
 *   <li><strong>The direction is fixed at launch.</strong> A leap that steered toward its target
 *       could not be dodged, and an attack that cannot be dodged cannot be countered.
 *   <li><strong>A stun cancels the whole sequence.</strong> That is what a landed counter is for: it
 *       does not merely interrupt this hop, it puts the unit back to walking and starts its cooldown.
 * </ul>
 */
public final class EnemyLeapSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    /** Below this a direction is treated as no direction, rather than normalised into a huge one. */
    private static final float EPSILON = 1e-4f;

    private final Rng rng;

    private ImmutableArray<Entity> leapers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public EnemyLeapSystem(Rng rng) {
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        leapers = engine.getEntitiesFor(
                Family.all(Leap.class, Position.class, Velocity.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        Position target = livingTarget();
        for (int i = 0; i < leapers.size(); i++) {
            Entity entity = leapers.get(i);
            Leap leap = LEAP.get(entity);
            if (held(entity)) {
                if (leap.committed()) {
                    leap.finish();
                }
                continue;
            }
            if (!leap.enabled) {
                continue;
            }
            if (target == null) {
                if (leap.committed()) {
                    leap.finish();
                }
                continue;
            }
            advance(entity, leap, target);
        }
    }

    /** The commander worth leaping at: the first one still alive. A body on the ground is not a target. */
    private @Nullable Position livingTarget() {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (DYING.has(commander) && DYING.get(commander).begun) {
                continue;
            }
            return POSITION.get(commander);
        }
        return null;
    }

    /** Held still by a blow, or dead on the ground. Either way it is not going anywhere under its own power. */
    private static boolean held(Entity entity) {
        if (STUNNED.has(entity) && STUNNED.get(entity).active()) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }

    private void advance(Entity entity, Leap leap, Position target) {
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        switch (leap.phase) {
            case READY -> commitIfClose(leap, position, target, velocity);
            case HESITATING -> {
                velocity.set(0f, 0f);
                if (leap.remainingSeconds <= 0f) {
                    launch(leap, position, target, velocity);
                }
            }
            case AIRBORNE -> {
                velocity.set(leap.directionX * leap.speed, leap.directionY * leap.speed);
                if (leap.remainingSeconds <= 0f) {
                    land(leap, velocity);
                }
            }
            case RECOVERING -> {
                velocity.set(0f, 0f);
                if (leap.remainingSeconds <= 0f) {
                    launch(leap, position, target, velocity);
                }
            }
        }
    }

    /** The tell: it stops dead, and only then does the wind-up clock start. */
    private void commitIfClose(Leap leap, Position position, Position target, Velocity velocity) {
        if (leap.cooldownRemaining > 0f) {
            return;
        }
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        if (toTargetX * toTargetX + toTargetY * toTargetY > leap.triggerRange * leap.triggerRange) {
            return;
        }
        leap.phase = Leap.Phase.HESITATING;
        leap.remainingSeconds = rng.nextFloat(leap.hesitationMinSeconds, leap.hesitationMaxSeconds);
        leap.leapsLeft = leap.sequenceLength;
        velocity.set(0f, 0f);
    }

    private static void launch(Leap leap, Position position, Position target, Velocity velocity) {
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= EPSILON) {
            leap.finish();
            velocity.set(0f, 0f);
            return;
        }
        leap.directionX = toTargetX / distance;
        leap.directionY = toTargetY / distance;
        leap.phase = Leap.Phase.AIRBORNE;
        leap.remainingSeconds = leap.airborneSeconds;
        leap.leapsLeft--;
        velocity.set(leap.directionX * leap.speed, leap.directionY * leap.speed);
    }

    private static void land(Leap leap, Velocity velocity) {
        velocity.set(0f, 0f);
        if (leap.leapsLeft > 0) {
            leap.phase = Leap.Phase.RECOVERING;
            leap.remainingSeconds = leap.betweenSeconds;
            return;
        }
        leap.finish();
    }
}
