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
    private final Throws throwing;
    private final TileMap map;
    private final float width;
    private final float height;

    private ImmutableArray<Entity> flying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> throwers = new ImmutableArray<>(new Array<>());

    public GrenadeSystem(TileMap map, Spawner spawner, Throws throwing) {
        this.map = map;
        this.spawner = spawner;
        this.throwing = throwing;
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
        Bounce.reflect(velocity, normalX / length, normalY / length);
    }

    /** Comes off rock and off the arena edge, neither of which is a body (G0019). */
    private void comeOffTheGround(Entity entity, Velocity velocity) {
        Position at = POSITION.get(entity);
        Bounce.offRock(map, at, PREVIOUS.get(entity), velocity);
        Bounce.offEdge(at, BODY.get(entity).radius, width, height, velocity);
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
            throwing.release(entity, thrower, towardsX / distance, towardsY / distance);
        }
    }
}
