package pl.mzebrows.pwt.sim.system;

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
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Harm;

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
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<ContactDamage> CONTACT_DAMAGE = ComponentMapper.getFor(ContactDamage.class);

    private static final float EPSILON = 1e-4f;

    private ImmutableArray<Entity> grippers = new ImmutableArray<>(new Array<>());

    /** Everyone a grip could take, which is also everyone who might come for a man who was taken. */
    private ImmutableArray<Entity> takeable = new ImmutableArray<>(new Array<>());

    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

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
            if (man == null || dying(gripper) || Drivers.down(man)) {
                if (man != null && dying(gripper) && !Drivers.down(man)) {
                    escape(gripper, grip, man, deltaTime);
                }
                letGo(grip, man);
                continue;
            }
            grip.remainingTicks--;
            GRIPPED.get(man).fraction = grip.fraction();
            if (grip.remainingTicks <= 0) {
                finish(gripper, man);
                letGo(grip, man);
                continue;
            }
            if (!Drivers.held(gripper)) {
                carryAway(gripper, grip, man);
            }
            pullAlong(gripper, man, deltaTime);
        }
        releaseStrays();
    }

    /** The man in its jaws, if he is still on the field and still agrees he is in them. */
    private @Nullable Entity heldBy(Entity gripper, Grip grip) {
        Entity man = Handles.resolve(handled, grip.load);
        if (man == null || !GRIPPED.has(man) || GRIPPED.get(man).by != Handles.of(gripper)) {
            return null;
        }
        return man;
    }

    private static boolean dying(Entity entity) {
        return DYING.has(entity) && DYING.get(entity).begun;
    }

    private static void letGo(Grip grip, @Nullable Entity man) {
        grip.release();
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
        if (!DAMAGE.has(man) || !CONTACT_DAMAGE.has(gripper)) {
            return;
        }
        ContactDamage bite = CONTACT_DAMAGE.get(gripper);
        Damage owed = DAMAGE.get(man);
        owed.add(bite.amount, bite.victimInvulnerableTicks, bite.harm);
        Position at = POSITION.get(gripper);
        owed.cameFrom(at.x, at.y);
    }

    /**
     * What getting out of its jaws costs him (G0053): a wound, and a throw clear of the body. Owed like
     * any blow, so his grace, and whatever else lands this tick, are weighed with it. The amount is its
     * bite's; only the harm matters to a man.
     */
    private static void escape(Entity gripper, Grip grip, Entity man, float tickSeconds) {
        if (!DAMAGE.has(man) || !CONTACT_DAMAGE.has(gripper)) {
            return;
        }
        Damage owed = DAMAGE.get(man);
        owed.add(CONTACT_DAMAGE.get(gripper).amount, 0, Harm.WOUND);
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
