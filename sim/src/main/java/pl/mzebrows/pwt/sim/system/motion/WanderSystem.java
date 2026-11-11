package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Wander;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Walks a unit that has noticed nobody slowly around the patch it was spawned in.
 *
 * <p>Pick a spot, walk to it, stand a moment, pick again. <strong>The patch is wherever it last had
 * nothing to do</strong>: the anchor is dragged along while it hunts or feeds. It drives only units
 * that are not hunting, so it never writes the same tick as navigation. Its own stream.
 */
public final class WanderSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Wander> WANDER = ComponentMapper.getFor(Wander.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);

    /**
     * How many spots it will try before settling for home, so a walled-in unit cannot search forever.
     */
    private static final int PLACEMENT_ATTEMPTS = 8;

    private final Rng rng;

    /** The tick a drawn pause is rounded to ({@link Ticks}). */
    private final float tickSeconds;

    private final TileMap map;

    /** What the adaptations held make of a patch's radius (ADR 0017). */
    private final Modifiers modifiers;

    private ImmutableArray<Entity> idlers = new ImmutableArray<>(new Array<>());

    public WanderSystem(Rng rng, TileMap map, float tickSeconds, Modifiers modifiers) {
        this.rng = rng;
        this.tickSeconds = tickSeconds;
        this.map = map;
        this.modifiers = modifiers;
    }

    @Override
    public void addedToEngine(Engine engine) {
        idlers = engine.getEntitiesFor(Family.all(Wander.class, Position.class, Steering.class, Speed.class, Body.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < idlers.size(); i++) {
            Entity entity = idlers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Wander wander = WANDER.get(entity);
            if (PERCEPTION.has(entity) && PERCEPTION.get(entity).occupied() && !shuffles(entity)) {
                // Its patch follows it while it has something better to do.
                Position position = POSITION.get(entity);
                wander.anchor(position.x, position.y);
                wander.rest(0);
                continue;
            }
            if (!wander.enabled) {
                continue;
            }
            if (wander.phase == Wander.Phase.RESTING) {
                if (wander.remainingTicks <= 0) {
                    pickSomewhere(entity, wander);
                }
                continue;
            }
            walkOn(entity, wander);
        }
    }

    /**
     * Whether this unit goes on ambling even while it has something to shoot at.
     *
     * <p>A shooter that holds ground ambles in its patch while it fires - <strong>only once it is
     * holding a position</strong>, or this would overwrite {@code NavigationSystem}'s walk.
     */
    private static boolean shuffles(Entity entity) {
        if (!RANGED.has(entity) || !PERCEPTION.get(entity).aggro) {
            return false;
        }
        Ranged ranged = RANGED.get(entity);
        return ranged.enabled && ranged.holdsGround && ranged.inPosition;
    }

    /** Steers toward the spot it picked, or gives up on it - by arriving, or by running out of patience. */
    private void walkOn(Entity entity, Wander wander) {
        Position position = POSITION.get(entity);
        float toTargetX = wander.targetX - position.x;
        float toTargetY = wander.targetY - position.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= wander.arriveRadius || wander.remainingTicks <= 0) {
            wander.rest(Ticks.of(rng.nextFloat(wander.pauseMinSeconds, wander.pauseMaxSeconds), tickSeconds));
            return;
        }
        float speed = SPEED.get(entity).value * wander.speedFactor;
        STEERING.get(entity).set(toTargetX / distance * speed, toTargetY / distance * speed);
    }

    /**
     * Chooses somewhere inside the patch that its body actually fits, or home if it cannot find one.
     *
     * <p>Rejection sampling in the bounding square: no trigonometry in {@code sim} (pillar 6).
     */
    private void pickSomewhere(Entity entity, Wander wander) {
        float bodyRadius = BODY.get(entity).radius;
        float radius = modifiers.wanderRadius(entity, wander.radius);
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            float offsetX = rng.nextFloat(-radius, radius);
            float offsetY = rng.nextFloat(-radius, radius);
            if (offsetX * offsetX + offsetY * offsetY > radius * radius) {
                continue;
            }
            float x = wander.homeX + offsetX;
            float y = wander.homeY + offsetY;
            if (map.isClear(x, y, bodyRadius)) {
                wander.walkTo(x, y);
                return;
            }
        }
        wander.walkTo(wander.homeX, wander.homeY);
    }
}
