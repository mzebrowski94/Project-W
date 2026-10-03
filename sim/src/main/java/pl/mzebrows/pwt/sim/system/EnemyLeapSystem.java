package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Throws a unit at whoever it is hunting once he is close enough, and owns its velocity while it is
 * in the air.
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
 *
 * <p><strong>Who it leaps at is read off its own {@code Perception}</strong>, which is where the
 * answer already lives (G0026): {@code PerceptionSystem} decides, every tick, who on the player's
 * side is nearest to <em>this</em> unit and publishes it as a place. Asking a second time here is how
 * this system spent a while throwing every leaper in the level at the first commander who happened to
 * be standing - the second player was never leapt at, and neither was a trooper. A unit that has not
 * noticed anybody does not leap; that is the same gate everything else it does goes through.
 */
public final class EnemyLeapSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Grip> GRIP = ComponentMapper.getFor(Grip.class);

    /** Below this a direction is treated as no direction, rather than normalised into a huge one. */
    private static final float EPSILON = 1e-4f;

    private final Rng rng;

    /** The tick a drawn hesitation is rounded to ({@link Ticks}). */
    private final float tickSeconds;

    /** Scratch for the place being leapt at, so reading it costs no allocation per unit per tick. */
    private final Position hunted = new Position();

    private ImmutableArray<Entity> leapers = new ImmutableArray<>(new Array<>());

    public EnemyLeapSystem(Rng rng, float tickSeconds) {
        this.rng = rng;
        this.tickSeconds = tickSeconds;
    }

    @Override
    public void addedToEngine(Engine engine) {
        leapers = engine.getEntitiesFor(
                Family.all(Leap.class, Grip.class, Position.class, Velocity.class, Perception.class)
                        .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < leapers.size(); i++) {
            Entity entity = leapers.get(i);
            Leap leap = LEAP.get(entity);
            if (Drivers.held(entity)) {
                if (leap.committed()) {
                    leap.finish();
                }
                continue;
            }
            if (!leap.enabled || GRIP.get(entity).holding()) {
                // Jaws full: nothing to leap with until it lets go (G0045).
                continue;
            }
            Perception perception = PERCEPTION.get(entity);
            if (!perception.aggro) {
                if (leap.committed()) {
                    leap.finish();
                }
                continue;
            }
            hunted.set(perception.targetX, perception.targetY);
            advance(entity, leap, hunted);
        }
    }

    private void advance(Entity entity, Leap leap, Position target) {
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        switch (leap.phase) {
            case READY -> commitIfClose(leap, position, target, velocity);
            case HESITATING -> {
                velocity.set(0f, 0f);
                if (leap.remainingTicks <= 0) {
                    launch(leap, position, target, velocity);
                }
            }
            case AIRBORNE -> {
                velocity.set(leap.directionX * leap.speed, leap.directionY * leap.speed);
                if (leap.remainingTicks <= 0) {
                    land(leap, velocity);
                }
            }
            case RECOVERING -> {
                velocity.set(0f, 0f);
                if (leap.remainingTicks <= 0) {
                    launch(leap, position, target, velocity);
                }
            }
        }
    }

    /** The tell: it stops dead, and only then does the wind-up clock start. */
    private void commitIfClose(Leap leap, Position position, Position target, Velocity velocity) {
        if (leap.cooldownRemaining > 0) {
            return;
        }
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        if (toTargetX * toTargetX + toTargetY * toTargetY > leap.triggerRange * leap.triggerRange) {
            return;
        }
        leap.phase = Leap.Phase.HESITATING;
        leap.remainingTicks =
                Ticks.of(rng.nextFloat(leap.hesitationMinSeconds, leap.hesitationMaxSeconds), tickSeconds);
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
        leap.remainingTicks = leap.airborneTicks;
        leap.leapsLeft--;
        velocity.set(leap.directionX * leap.speed, leap.directionY * leap.speed);
    }

    private static void land(Leap leap, Velocity velocity) {
        velocity.set(0f, 0f);
        if (leap.leapsLeft > 0) {
            leap.phase = Leap.Phase.RECOVERING;
            leap.remainingTicks = leap.betweenTicks;
            return;
        }
        leap.finish();
    }
}
