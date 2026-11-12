package pl.mzebrows.pwt.sim.system.creature;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Carries off whatever a gripping leap took hold of, and finishes him when his time is up (G0045).
 *
 * <p>The hold itself is taken where contacts are decided ({@code CollisionSystem}); this is everything
 * after that. Each tick, for every creature with a man in its jaws:
 *
 * <ul>
 *   <li><strong>It lets go the moment the pair stops being true</strong> - it is dying, he is down or
 *       dead, either is gone. Killing it is how a man is freed, and nothing else frees him: struggling
 *       is landing blows on it like anybody else does. <strong>Getting out costs a wound</strong>
 *       (G0053): a man it dies holding is thrown clear, wounded.
 *   <li><strong>His clock runs</strong>, and at the end of it the creature lands its own blow, with the
 *       lethality its claws declare (G0020). The clock is published on him for the renderer.
 *   <li><strong>It walks away from whoever is nearest him</strong> - the man most likely to come for
 *       him - at its carrying pace, as a desired velocity, so avoidance still bends it round rock and
 *       what it is carrying goes where it goes. A creature held still by a blow carries nobody anywhere.
 *   <li><strong>He is pulled along at arm's length</strong> on the side he was taken from, by velocity
 *       rather than position, so the solver still keeps him out of rock.
 * </ul>
 *
 * <p><strong>A grip that does not pin clings</strong> (G0083), and the carrying goes the other way: he goes where he
 * likes and it goes with him, kept touching his edge on the side it took him from. It is no body to him while it
 * clings - not solid, so it neither blocks nor shoves the man it is on, and still touched by whatever comes at it - and
 * a body again once it lets go. Getting it off throws nobody clear: what it costs him is whatever it leaves when it
 * dies. One that is <strong>spent on finishing him</strong> dies with its blow.
 *
 * <p>Runs after the course setters that would otherwise speak for the creature (7-9) and before
 * avoidance (G0010), and before anything that would drive the man - every driver leaves a held unit
 * alone, which is what makes this the only thing that moves him.
 */
public final class GripSystem extends EntitySystem {

    private static final ComponentMapper<Grip> GRIP = ComponentMapper.getFor(Grip.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<ContactDamage> CONTACT_DAMAGE = ComponentMapper.getFor(ContactDamage.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);

    private static final float EPSILON = 1e-4f;

    private ImmutableArray<Entity> grippers = new ImmutableArray<>(new Array<>());

    /** Everyone a grip could take, which is also everyone who might come for a man who was taken. */
    private ImmutableArray<Entity> takeable = new ImmutableArray<>(new Array<>());

    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    private final Spawner spawner;

    public GripSystem(Spawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        grippers = engine.getEntitiesFor(Family.all(Grip.class, Position.class, Body.class, Steering.class)
                .get());
        takeable = engine.getEntitiesFor(
                Family.all(Gripped.class, Position.class, Faction.class).get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < grippers.size(); i++) {
            Entity gripper = grippers.get(i);
            Grip grip = GRIP.get(gripper);
            if (!grip.holding()) {
                continue;
            }
            Entity man = heldBy(gripper, grip);
            if (man == null || Life.dying(gripper) || Life.down(man) || gripper.isScheduledForRemoval()) {
                if (man != null && grip.pins && Life.dying(gripper) && !Life.down(man)) {
                    escape(gripper, grip, man, deltaTime);
                }
                letGo(gripper, grip, man);
                continue;
            }
            grip.remainingTicks--;
            GRIPPED.get(man).fraction = grip.fraction();
            if (grip.remainingTicks <= 0) {
                finish(gripper, man);
                letGo(gripper, grip, man);
                if (grip.spentOnFinish) {
                    // It goes off with its blow, by dying (G0018): whatever it leaves, it leaves on him.
                    spawner.markDead(gripper);
                }
                continue;
            }
            if (!grip.pins) {
                cling(gripper, man, deltaTime);
                continue;
            }
            if (!Drivers.held(gripper)) {
                carryAway(gripper, grip, man);
            }
            pullAlong(gripper, man, deltaTime);
        }
        releaseStrays();
    }

    /**
     * Puts a thing that clings on his edge, on the side it is already on, where he will be once this tick's course is
     * spent (G0083). By its position, so nothing that holds it still or slows it - a stun, the ground - takes it off
     * him, and rock never scrapes it off; not solid while it is on him, so the two never push each other about.
     */
    private static void cling(Entity gripper, Entity man, float deltaTime) {
        if (deltaTime <= 0f) {
            return;
        }
        if (COLLIDER.has(gripper)) {
            COLLIDER.get(gripper).solid = false;
        }
        Position at = POSITION.get(gripper);
        Position where = POSITION.get(man);
        float bearingX = at.x - where.x;
        float bearingY = at.y - where.y;
        float apart = (float) Math.sqrt(bearingX * bearingX + bearingY * bearingY);
        if (apart <= EPSILON) {
            bearingX = FACING.has(man) ? -FACING.get(man).x : 1f;
            bearingY = FACING.has(man) ? -FACING.get(man).y : 0f;
            apart = (float) Math.sqrt(bearingX * bearingX + bearingY * bearingY);
            if (apart <= EPSILON) {
                bearingX = 1f;
                bearingY = 0f;
                apart = 1f;
            }
        }
        float reach = BODY.get(gripper).radius + (BODY.has(man) ? BODY.get(man).radius : 0f);
        float aheadX = VELOCITY.has(man) ? VELOCITY.get(man).x * deltaTime : 0f;
        float aheadY = VELOCITY.has(man) ? VELOCITY.get(man).y * deltaTime : 0f;
        at.set(where.x + aheadX + bearingX / apart * reach, where.y + aheadY + bearingY / apart * reach);
        if (VELOCITY.has(gripper)) {
            VELOCITY.get(gripper).set(0f, 0f);
        }
    }

    /** The man in its jaws, if he is still on the field and still agrees he is in them. */
    private @Nullable Entity heldBy(Entity gripper, Grip grip) {
        Entity man = Handles.resolve(handled, grip.load);
        if (man == null || !GRIPPED.has(man) || GRIPPED.get(man).by != Handles.of(gripper)) {
            return null;
        }
        return man;
    }

    private static void letGo(Entity gripper, Grip grip, @Nullable Entity man) {
        grip.release();
        if (!grip.pins && COLLIDER.has(gripper) && !Life.dying(gripper)) {
            // Off him, it is a body again.
            COLLIDER.get(gripper).solid = true;
        }
        if (man == null) {
            return;
        }
        GRIPPED.get(man).release();
        if (VELOCITY.has(man)) {
            VELOCITY.get(man).set(0f, 0f);
        }
    }

    /** Its own blow, with its own lethality, at the end of the carry (G0020). */
    private static void finish(Entity gripper, Entity man) {
        if (!DAMAGE.has(man) || !CONTACT_DAMAGE.has(gripper) || !CONTACT_DAMAGE.get(gripper).enabled) {
            return;
        }
        Damage owed = DAMAGE.get(man);
        owed.land(CONTACT_DAMAGE.get(gripper));
        Position at = POSITION.get(gripper);
        owed.cameFrom(at.x, at.y);
    }

    /**
     * What getting out of its jaws costs him (G0053): the harm its grip declares - a wound for every
     * gripper there is (G0057) - and a throw clear of the body. Owed like any blow, so his grace, and
     * whatever else lands this tick, are weighed with it. A man has no health, so it owes none.
     */
    private static void escape(Entity gripper, Grip grip, Entity man, float tickSeconds) {
        if (!DAMAGE.has(man)) {
            return;
        }
        Damage owed = DAMAGE.get(man);
        owed.land(0, grip.escapeHarm);
        Position at = POSITION.get(gripper);
        Position where = POSITION.get(man);
        owed.cameFrom(at.x, at.y);
        float awayX = where.x - at.x;
        float awayY = where.y - at.y;
        float apart = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (apart <= EPSILON || grip.escapeKnockbackSpeed <= 0f || grip.escapeShoveTicks <= 0) {
            return;
        }
        float seconds = grip.escapeShoveTicks * tickSeconds;
        owed.shove(
                awayX / apart,
                awayY / apart,
                grip.escapeKnockbackSpeed,
                grip.escapeShoveTicks,
                grip.escapeKnockbackSpeed / seconds);
    }

    /**
     * Straight away from whoever else on his side is nearest him, at its carrying pace. With nobody
     * left to run from it keeps going the way it faces.
     */
    private void carryAway(Entity gripper, Grip grip, Entity man) {
        Position at = POSITION.get(gripper);
        Position from = POSITION.get(man);
        @Nullable Position rescuer = nearestRescuer(man, from);
        float awayX;
        float awayY;
        if (rescuer != null) {
            awayX = at.x - rescuer.x;
            awayY = at.y - rescuer.y;
        } else {
            awayX = FACING.has(gripper) ? FACING.get(gripper).x : 0f;
            awayY = FACING.has(gripper) ? FACING.get(gripper).y : 0f;
        }
        float length = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (length <= EPSILON) {
            return;
        }
        STEERING.get(gripper).set(awayX / length * grip.carrySpeed, awayY / length * grip.carrySpeed);
    }

    /** The nearest man on his feet on his side, other than him. A handful of entities: a scan. */
    private @Nullable Position nearestRescuer(Entity man, Position from) {
        @Nullable Position nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < takeable.size(); i++) {
            Entity candidate = takeable.get(i);
            if (Objects.equals(candidate, man) || !Drivers.huntable(candidate) || Drivers.gripped(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - from.x;
            float toY = where.y - from.y;
            float distance = toX * toX + toY * toY;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = where;
            }
        }
        return nearest;
    }

    /**
     * A velocity that keeps him at arm's length from it on the side he is already on - the same shape
     * as a haul closing on the place behind the man hauling ({@code DragSystem}'s follow). Taken by the
     * head, he stays where he was taken until it turns, and then he is dragged: held at a bearing rather
     * than at its facing, so a creature knocked round by a blow does not swing him through itself.
     */
    private static void pullAlong(Entity gripper, Entity man, float deltaTime) {
        if (!VELOCITY.has(man) || deltaTime <= 0f) {
            return;
        }
        Position at = POSITION.get(gripper);
        Position where = POSITION.get(man);
        float bearingX = where.x - at.x;
        float bearingY = where.y - at.y;
        float apart = (float) Math.sqrt(bearingX * bearingX + bearingY * bearingY);
        if (apart <= EPSILON) {
            return;
        }
        float reach = BODY.get(gripper).radius + (BODY.has(man) ? BODY.get(man).radius : 0f);
        // Where it will be once this tick's course is spent, so he keeps up rather than trailing a tick.
        float aheadX = VELOCITY.has(gripper) ? VELOCITY.get(gripper).x * deltaTime : 0f;
        float aheadY = VELOCITY.has(gripper) ? VELOCITY.get(gripper).y * deltaTime : 0f;
        float wantX = at.x + aheadX + bearingX / apart * reach;
        float wantY = at.y + aheadY + bearingY / apart * reach;
        VELOCITY.get(man).set((wantX - where.x) / deltaTime, (wantY - where.y) / deltaTime);
    }

    /**
     * A man who says he is held by something that no longer says it holds him is put back on his feet:
     * the creature was reaped between two ticks, or recycled. Rarely anybody at all.
     */
    private void releaseStrays() {
        for (int i = 0; i < takeable.size(); i++) {
            Entity man = takeable.get(i);
            Gripped gripped = GRIPPED.get(man);
            if (!gripped.held()) {
                continue;
            }
            Entity gripper = Handles.resolve(handled, gripped.by);
            if (gripper == null || !GRIP.has(gripper) || GRIP.get(gripper).load != Handles.of(man)) {
                gripped.release();
            }
        }
    }
}
