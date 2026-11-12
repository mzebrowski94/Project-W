package pl.mzebrows.pwt.sim.system.creature;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Sling;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

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
 *
 * <p><strong>A hunter whose way a shut door shuts leaps at the door</strong> (G0069, amending G0026): its route
 * runs through the door, as every route does (ADR 0022), so the way it is going meets the door before it meets the
 * man. It commits as for a man, for one leap aimed once at the door's face, and on reaching it lands {@code
 * leapDamage} on the door, is thrown back off it and shakes the ground for the commanders near it - then leaps again
 * on its own leap clock. Nothing asks who is inside; only a hunter finds its way shut.
 */
public final class EnemyLeapSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Grip> GRIP = ComponentMapper.getFor(Grip.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Shake> SHAKE = ComponentMapper.getFor(Shake.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Sling> SLING = ComponentMapper.getFor(Sling.class);
    private static final ComponentMapper<ContactDamage> CONTACT = ComponentMapper.getFor(ContactDamage.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);

    /** Below this a direction is treated as no direction, rather than normalised into a huge one. */
    private static final float EPSILON = 1e-4f;

    /** How near a body's edge has to be to a door's face to have reached it: the solver leaves it touching. */
    private static final float TOUCHING = 0.05f;

    /**
     * The share of a tick's flight a slung rider must have made good to still be flying: below it, rock or scenery
     * has stopped it, and it comes down there.
     */
    private static final float STOPPED = 0.5f;

    private final Rng rng;

    /** The tick a drawn hesitation is rounded to ({@link Ticks}). */
    private final float tickSeconds;

    /** Whether a tile is solid now, for the one question of a way shut by a door. */
    private final Solid solid;

    /** The map's tile, which a doorway is counted in. */
    private final float tileSize;

    /** What a leap lands on a door, whatever the strain (G0069). */
    private final int doorDamage;

    /** How hard a leap landing on a door shakes the ground there. */
    private final float shakeStrength;

    private final float shakeRadius;
    private final int shakeTicks;

    /** How a creature is thrown back off a door: this fast, held this long, slowing to a stop as it ends. */
    private final float throwSpeed;

    private final int throwTicks;
    private final float throwDecay;

    /** What the adaptations held make of a leap's reach, speed, time in the air and sequence (ADR 0017). */
    private final Modifiers modifiers;

    /** Scratch for the place being leapt at, so reading it costs no allocation per unit per tick. */
    private final Position hunted = new Position();

    /** Scratch for a door's face being leapt at, likewise. */
    private final Position faced = new Position();

    private ImmutableArray<Entity> leapers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());

    public EnemyLeapSystem(Rng rng, float tickSeconds, Solid solid, SimConfig.DoorConfig door, Modifiers modifiers) {
        this.rng = rng;
        this.tickSeconds = tickSeconds;
        this.solid = solid;
        this.tileSize = solid.map().tileSize();
        this.doorDamage = door.leapDamage();
        this.shakeStrength = door.shake().strength();
        this.shakeRadius = door.shake().radius();
        this.shakeTicks = Ticks.of(door.shake().seconds(), tickSeconds);
        this.throwSpeed = door.throwSpeed();
        this.throwTicks = Ticks.of(door.throwSeconds(), tickSeconds);
        this.throwDecay = door.throwSpeed() / door.throwSeconds();
        this.modifiers = modifiers;
    }

    @Override
    public void addedToEngine(Engine engine) {
        leapers = engine.getEntitiesFor(Family.all(
                        Leap.class,
                        Grip.class,
                        Position.class,
                        Velocity.class,
                        Perception.class,
                        ContactDamage.class,
                        Collider.class)
                .get());
        doors = engine.getEntitiesFor(
                Family.all(Door.class, Damage.class, Shake.class, Handle.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < leapers.size(); i++) {
            Entity entity = leapers.get(i);
            Leap leap = LEAP.get(entity);
            holdOffSceneryInTheAir(entity, leap);
            if (leap.slung()) {
                // Aimed once as it was slung, and nothing can hold it or change its mind in the air (G0074).
                fly(entity, leap);
                continue;
            }
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
            if (SLING.has(entity) && SLING.get(entity).loaded()) {
                // Back full: it slings its riders, and leaps once there are none left (G0074).
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

    /**
     * What never walks is scenery on the ground (G0048) and a body in the air like any leaper's, held off rock and
     * scenery by the solver (G0083): movable exactly while airborne. A body in its throes is left as its death made it.
     */
    private static void holdOffSceneryInTheAir(Entity entity, Leap leap) {
        if (SPEED.has(entity) && SPEED.get(entity).rooted() && !Life.dying(entity)) {
            COLLIDER.get(entity).movable = leap.airborne();
        }
    }

    private void advance(Entity entity, Leap leap, Position target) {
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        switch (leap.phase) {
            case READY -> commitIfClose(entity, leap, position, target, velocity);
            case HESITATING -> {
                velocity.set(0f, 0f);
                if (leap.remainingTicks <= 0) {
                    launch(entity, leap, position, target, velocity);
                }
            }
            case AIRBORNE -> {
                @Nullable Entity door = standing(leap.door);
                if (door != null && reached(entity, position, door)) {
                    bounceOff(entity, leap, door, velocity);
                    return;
                }
                velocity.set(leap.directionX * leap.flightSpeed, leap.directionY * leap.flightSpeed);
                if (leap.remainingTicks <= 0) {
                    land(leap, velocity);
                }
            }
            case RECOVERING -> {
                velocity.set(0f, 0f);
                if (leap.remainingTicks <= 0) {
                    launch(entity, leap, position, target, velocity);
                }
            }
        }
    }

    /**
     * The tell: it stops dead, and only then does the wind-up clock start. At the man once he is within its
     * trigger range - or, for one leap, at a shut door its way runs into within it (G0069).
     */
    private void commitIfClose(Entity entity, Leap leap, Position position, Position target, Velocity velocity) {
        if (leap.cooldownRemaining > 0) {
            return;
        }
        float toTargetX = target.x - position.x;
        float toTargetY = target.y - position.y;
        float triggerRange = modifiers.leapTriggerRange(entity, leap.triggerRange);
        long door = doorInTheWay(position, toTargetX, toTargetY, velocity, triggerRange);
        if (door == Handle.NOBODY && toTargetX * toTargetX + toTargetY * toTargetY > triggerRange * triggerRange) {
            return;
        }
        leap.phase = Leap.Phase.HESITATING;
        leap.remainingTicks =
                Ticks.of(rng.nextFloat(leap.hesitationMinSeconds, leap.hesitationMaxSeconds), tickSeconds);
        leap.leapsLeft = door == Handle.NOBODY ? modifiers.leapSequenceLength(entity, leap.sequenceLength) : 1;
        leap.door = door;
        velocity.set(0f, 0f);
    }

    /**
     * The shut door the way it is going runs into within this distance, or nobody: the way it is walking, or the
     * way to the man while it stands (G0069).
     */
    private long doorInTheWay(Position position, float toTargetX, float toTargetY, Velocity velocity, float range) {
        if (doors.size() == 0) {
            // A walk of the grid ahead of every hunter ready to leap, skipped on a level with no doors at all.
            return Handle.NOBODY;
        }
        float wayX = velocity.x;
        float wayY = velocity.y;
        if (wayX * wayX + wayY * wayY <= EPSILON * EPSILON) {
            wayX = toTargetX;
            wayY = toTargetY;
        }
        float length = (float) Math.sqrt(wayX * wayX + wayY * wayY);
        if (length <= EPSILON) {
            return Handle.NOBODY;
        }
        return solid.doorAhead(position.x, position.y, wayX / length, wayY / length, range);
    }

    /** The door a leap is at, while it still stands shut; null for a leap at a man, or at a door gone. */
    private @Nullable Entity standing(long door) {
        @Nullable Entity entity = Handles.resolve(doors, door);
        return entity == null || DOOR.get(entity).open || DEAD.has(entity) ? null : entity;
    }

    /** Whether a leaper has come up against a door's face: the solver stops it there, touching. */
    private boolean reached(Entity entity, Position position, Entity door) {
        float radius = BODY.has(entity) ? BODY.get(entity).radius : 0f;
        return DOOR.get(door).distanceFrom(position.x, position.y, tileSize) <= radius + TOUCHING;
    }

    /**
     * A leap landing on a door (G0069): its blow on the door through the one way a blow is recorded, the ground
     * shaken for the commanders near it, the creature thrown back off it the way it came, and the leap over.
     */
    private void bounceOff(Entity entity, Leap leap, Entity door, Velocity velocity) {
        DAMAGE.get(door).land(doorDamage, Harm.NONE);
        SHAKE.get(door).set(shakeStrength, shakeRadius, shakeTicks);
        if (DAMAGE.has(entity)) {
            // A harmless blow carrying the throw, so DamageSystem throws it as it throws anything (G0057).
            Damage thrown = DAMAGE.get(entity);
            thrown.land(0, Harm.NONE);
            thrown.shove(-leap.directionX, -leap.directionY, throwSpeed, throwTicks, throwDecay);
        }
        velocity.set(0f, 0f);
        leap.finish();
    }

    /**
     * Off the ground, along a direction fixed now: at the man, or at the face of the door the leap is at - at a speed
     * and for a time fixed now too, so an adaptation landing mid-flight changes the next leap, not this one.
     */
    private void launch(Entity entity, Leap leap, Position position, Position target, Velocity velocity) {
        Position at = target;
        @Nullable Entity door = standing(leap.door);
        if (door == null) {
            leap.door = Handle.NOBODY;
        } else {
            // Its face: the nearest part of the doorway.
            Door doorway = DOOR.get(door);
            float left = doorway.col * tileSize;
            float bottom = doorway.row * tileSize;
            faced.set(
                    Math.max(left, Math.min(position.x, left + doorway.width * tileSize)),
                    Math.max(bottom, Math.min(position.y, bottom + doorway.height * tileSize)));
            at = faced;
        }
        float toTargetX = at.x - position.x;
        float toTargetY = at.y - position.y;
        float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
        if (distance <= EPSILON) {
            leap.finish();
            velocity.set(0f, 0f);
            return;
        }
        leap.directionX = toTargetX / distance;
        leap.directionY = toTargetY / distance;
        leap.phase = Leap.Phase.AIRBORNE;
        leap.remainingTicks = modifiers.leapAirborneTicks(entity, leap.airborneTicks);
        leap.flightSpeed = modifiers.leapSpeed(entity, leap.speed);
        leap.leapsLeft--;
        leap.leaps++;
        velocity.set(leap.directionX * leap.flightSpeed, leap.directionY * leap.flightSpeed);
    }

    /**
     * A slung rider's flight (G0074): on along the line it was slung until its time is up, or until it has stopped
     * making headway - rock, the arena's edge or scenery held it - and then down where it is, its claws sheathed for
     * a moment, and an ordinary creature from then on.
     */
    private void fly(Entity entity, Leap leap) {
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        float headway = (position.x - leap.fromX) * leap.directionX + (position.y - leap.fromY) * leap.directionY;
        boolean stopped = leap.flownTicks > 0 && headway < leap.flightSpeed * tickSeconds * STOPPED;
        if (leap.remainingTicks <= 0 || stopped) {
            velocity.set(0f, 0f);
            CONTACT.get(entity).sheathe(leap.landingTicks);
            COLLIDER.get(entity).throughBodies = false;
            leap.finish();
            return;
        }
        leap.fromX = position.x;
        leap.fromY = position.y;
        leap.flownTicks++;
        velocity.set(leap.directionX * leap.flightSpeed, leap.directionY * leap.flightSpeed);
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
