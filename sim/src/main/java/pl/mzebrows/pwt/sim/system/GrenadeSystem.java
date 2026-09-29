package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Throws what was asked for, and flies what is already in the air.
 *
 * <p><strong>Along the aim direction, never at the aim point</strong>, or zoom would become range. It
 * spends a <em>distance</em>, and a bounce changes direction, never speed (G0019).
 *
 * <p>Immediately after the solver, so this tick's contacts are fresh. Rock and the arena edge are
 * handled here: a grenade is non-solid, so the solver does not.
 */
public final class GrenadeSystem extends EntitySystem {

    private static final ComponentMapper<Thrown> THROWN = ComponentMapper.getFor(Thrown.class);
    private static final ComponentMapper<Thrower> THROWER = ComponentMapper.getFor(Thrower.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    /** Below this the aim point is on top of the thrower and gives no direction to throw along. */
    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final TileMap map;
    private final float width;
    private final float height;

    private ImmutableArray<Entity> flying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> throwers = new ImmutableArray<>(new Array<>());

    public GrenadeSystem(TileMap map, Spawner spawner, Modifiers modifiers) {
        this.map = map;
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.width = map.worldWidth();
        this.height = map.worldHeight();
    }

    @Override
    public void addedToEngine(Engine engine) {
        flying = engine.getEntitiesFor(
                Family.all(Thrown.class, Position.class, PreviousPosition.class, Velocity.class, Body.class)
                        .get());
        throwers = engine.getEntitiesFor(Family.all(Thrower.class, Intent.class, Position.class, Body.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        fly(deltaTime);
        throwWhateverWasAskedFor();
    }

    private void fly(float deltaTime) {
        for (int i = flying.size() - 1; i >= 0; i--) {
            Entity entity = flying.get(i);
            if (entity.isScheduledForRemoval()) {
                continue;
            }
            Thrown thrown = THROWN.get(entity);
            Velocity velocity = VELOCITY.get(entity);
            comeOffWhatItTouched(thrown, velocity);
            comeOffTheGround(entity, velocity);
            thrown.remainingDistance -= thrown.speed * deltaTime;
            if (thrown.remainingDistance <= 0f) {
                spawner.markDead(entity);
            }
        }
    }

    /** Answers whatever the solver reported this tick, and forgets it either way. */
    private static void comeOffWhatItTouched(Thrown thrown, Velocity velocity) {
        if (!thrown.bounced) {
            return;
        }
        float normalX = thrown.bounceX;
        float normalY = thrown.bounceY;
        thrown.settled();
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY);
        if (length <= EPSILON) {
            return;
        }
        reflect(velocity, normalX / length, normalY / length);
    }

    /**
     * Mirrors a course about a surface, and <strong>only while it is still heading into it</strong>.
     *
     * <p>The guard stops the jitter: a non-solid grenade still overlaps next tick.
     */
    private static void reflect(Velocity velocity, float normalX, float normalY) {
        float into = velocity.x * normalX + velocity.y * normalY;
        if (into >= 0f) {
            return;
        }
        velocity.x -= 2f * into * normalX;
        velocity.y -= 2f * into * normalY;
    }

    /**
     * Comes off rock and off the arena edge, neither of which is a body.
     *
     * <p>Which axis to mirror is decided by asking where it could have got to on each one alone: if
     * only the sideways half of the step was blocked it came off a vertical face, and if neither half
     * was blocked on its own it went diagonally into an inside corner and comes back the way it came.
     *
     * <p>It is put back where it started the tick, so it is not asked again next tick.
     */
    private void comeOffTheGround(Entity entity, Velocity velocity) {
        Position at = POSITION.get(entity);
        PreviousPosition from = PREVIOUS.get(entity);
        if (!map.passableAt(at.x, at.y)) {
            boolean sideways = !map.passableAt(at.x, from.y);
            boolean along = !map.passableAt(from.x, at.y);
            if (sideways || !along) {
                velocity.x = -velocity.x;
            }
            if (along || !sideways) {
                velocity.y = -velocity.y;
            }
            at.set(from.x, from.y);
        }
        float radius = BODY.get(entity).radius;
        if (at.x - radius < 0f || at.x + radius > width) {
            velocity.x = -velocity.x;
            at.x = clamp(at.x, radius, width - radius);
        }
        if (at.y - radius < 0f || at.y + radius > height) {
            velocity.y = -velocity.y;
            at.y = clamp(at.y, radius, height - radius);
        }
    }

    private void throwWhateverWasAskedFor() {
        for (int i = 0; i < throwers.size(); i++) {
            Entity entity = throwers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Thrower thrower = THROWER.get(entity);
            Intent intent = INTENT.get(entity);
            if (!intent.throwing || !thrower.ready() || Drivers.handsFull(entity)) {
                continue;
            }
            Position at = POSITION.get(entity);
            float towardsX = intent.aimX - at.x;
            float towardsY = intent.aimY - at.y;
            float distance = (float) Math.sqrt(towardsX * towardsX + towardsY * towardsY);
            if (distance <= EPSILON) {
                continue;
            }
            Throws.release(spawner, modifiers, entity, thrower, towardsX / distance, towardsY / distance);
        }
    }

    /** Guards against an arena narrower than the thing bouncing around inside it. */
    private static float clamp(float value, float min, float max) {
        if (min >= max) {
            return (min + max) * 0.5f;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
