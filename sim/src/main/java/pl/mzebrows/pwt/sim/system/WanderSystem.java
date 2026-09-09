package pl.mzebrows.pwt.sim.system;

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
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Wander;

/**
 * Walks a unit that has noticed nobody slowly around the patch it was spawned in.
 *
 * <p>Pick a spot, walk to it, stand a moment, pick again. Nothing more, deliberately: exploring a
 * level is a different behaviour with different questions attached, and it is not designed yet.
 *
 * <p><strong>The patch is wherever it last had nothing to do</strong>, not where it was spawned. The
 * anchor is dragged along behind anything that is hunting or feeding, so the moment it gives up it is
 * already home. Anchoring to the spawn instead means a unit that broke off a chase turns round and
 * walks the length of the level back, which reads as the level rewinding rather than as a creature
 * losing interest.
 *
 * <p>It drives only units that are <em>not</em> hunting, and navigation drives only the ones that
 * are, so the two never write the same tick and neither has to know the other exists.
 * {@code PerceptionSystem} decided which is which, upstream of both.
 *
 * <p><strong>Waypoints are drawn from the wander stream, not the world's.</strong> Sharing one
 * sequence with the leap would mean re-tuning how often a unit ambles reshuffles every wind-up in the
 * game - see {@code RngStream}.
 */
public final class WanderSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Wander> WANDER = ComponentMapper.getFor(Wander.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);

    /**
     * How many spots it will try before settling for home. A cap rather than a loop: a unit walled
     * into a space smaller than its own patch would otherwise search forever, once per tick.
     */
    private static final int PLACEMENT_ATTEMPTS = 8;

    private final Rng rng;
    private final TileMap map;

    private ImmutableArray<Entity> idlers = new ImmutableArray<>(new Array<>());

    public WanderSystem(Rng rng, TileMap map) {
        this.rng = rng;
        this.map = map;
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
            if (PERCEPTION.has(entity) && PERCEPTION.get(entity).occupied()) {
                // Its patch follows it while it has something better to do, so the ground it gives up
                // on is the ground it settles into. A unit that broke off a chase and then walked the
                // length of the level back to where it spawned read as the level rewinding.
                Position position = POSITION.get(entity);
                wander.anchor(position.x, position.y);
                wander.rest(0f);
                continue;
            }
            if (!wander.enabled) {
                continue;
            }
            if (wander.phase == Wander.Phase.RESTING) {
                if (wander.remainingSeconds <= 0f) {
                    pickSomewhere(entity, wander);
                }
                continue;
            }
            walkOn(entity, wander);
        }
    }

    /** Steers toward the spot it picked, or gives up on it - by arriving, or by running out of patience. */
    private void walkOn(Entity entity, Wander wander) {
        Position position = POSITION.get(entity);
        float toTargetX = wander.targetX - position.x;
        float toTargetY = wander.targetY - position.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= wander.arriveRadius || wander.remainingSeconds <= 0f) {
            wander.rest(rng.nextFloat(wander.pauseMinSeconds, wander.pauseMaxSeconds));
            return;
        }
        float speed = SPEED.get(entity).value * wander.speedFactor;
        STEERING.get(entity).set(toTargetX / distance * speed, toTargetY / distance * speed);
    }

    /**
     * Chooses somewhere inside the patch that its body actually fits, or home if it cannot find one.
     *
     * <p>Rejection sampling inside the bounding square rather than an angle and a radius, because an
     * angle needs a sine and a cosine and {@code ArchitectureTest} bans both: they are specified only
     * to within an ulp and may differ between JVMs, which would make a replay stop replaying on
     * somebody else's machine. Rejecting the corners of a square costs about a quarter of the draws
     * and is exact everywhere.
     */
    private void pickSomewhere(Entity entity, Wander wander) {
        float bodyRadius = BODY.get(entity).radius;
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            float offsetX = rng.nextFloat(-wander.radius, wander.radius);
            float offsetY = rng.nextFloat(-wander.radius, wander.radius);
            if (offsetX * offsetX + offsetY * offsetY > wander.radius * wander.radius) {
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
