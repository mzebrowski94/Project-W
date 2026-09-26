package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.GroundDef;

/**
 * Turns a commander's intent into movement and gunfire.
 *
 * <p>Movement and aim are read separately and never derived from each other (G0002): the velocity
 * this writes says nothing about where the commander is pointing, and the direction a projectile
 * leaves in says nothing about where the commander is walking.
 *
 * <p>A held commander runs none of this, and his velocity is {@code StunSystem}'s.
 *
 * <p><strong>The leash lives here because this system owns a commander's velocity</strong> (pillar
 * 4): the outward part of a step past {@code leashRadius} from the middle of the commanders is refused
 * exactly. <strong>It never pushes</strong> (pillar 2) - one past the line walks home himself.
 */
public final class CommanderControlSystem extends IteratingSystem {

    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Drag> DRAG = ComponentMapper.getFor(Drag.class);

    /** Below this a direction is treated as no direction, rather than normalised into a huge one. */
    private static final float EPSILON = 1e-4f;

    /** Gap between the commander's body and the projectile it fires, so a shot never starts inside it. */
    private static final float MUZZLE_GAP = 0.05f;

    private final Spawner spawner;

    /** A gun's own pattern, for the one he may be holding that throws more than one thing at once. */
    private final Rng scatter;

    private final float leashRadius;

    /** The ground he is standing on, and what each kind of it costs him. */
    private final TileMap map;

    private final Defs<Terrain, GroundDef> ground;

    /** Scratch, so working out where a shot leaves from costs no allocation per shot. */
    private final float[] muzzle = new float[2];

    /** The middle of the commanders, worked out once a tick before any of them is driven. */
    @NotState("scratch: recomputed at the start of every update")
    private float centreX;

    @NotState("scratch: recomputed at the start of every update")
    private float centreY;

    @NotState("scratch: recomputed at the start of every update")
    private int watched;

    public CommanderControlSystem(SimConfig config, TileMap map, Spawner spawner, Rng scatter) {
        this.scatter = scatter;
        super(Family.all(
                        Commander.class,
                        Intent.class,
                        Position.class,
                        Velocity.class,
                        Speed.class,
                        Body.class,
                        Weapon.class,
                        Faction.class)
                .get());
        this.spawner = spawner;
        this.map = map;
        this.ground = config.ground();
        this.leashRadius = config.commander().leashRadius();
    }

    /**
     * Where the commanders are, before any of them has been driven.
     *
     * <p>Once a tick, so every commander is leashed to the same place.
     */
    @Override
    public void update(float deltaTime) {
        findCentre();
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (Drivers.held(entity)) {
            return;
        }
        Intent intent = INTENT.get(entity);
        drive(entity, intent);
        leash(entity, deltaTime);
        if (Drivers.handsFull(entity)) {
            return;
        }
        fire(entity, intent);
    }

    private void findCentre() {
        ImmutableArray<Entity> commanders = getEntities();
        watched = commanders.size();
        centreX = 0f;
        centreY = 0f;
        if (watched == 0) {
            return;
        }
        for (int i = 0; i < watched; i++) {
            Position at = POSITION.get(commanders.get(i));
            centreX += at.x;
            centreY += at.y;
        }
        centreX /= watched;
        centreY /= watched;
    }

    /**
     * Takes off whatever part of this step would carry him past the end of the rope.
     *
     * <p>What survives lands him exactly on the line; the position is never clamped.
     */
    private void leash(Entity entity, float deltaTime) {
        if (leashRadius <= 0f || watched < 2 || deltaTime <= 0f) {
            return;
        }
        Position at = POSITION.get(entity);
        float outX = at.x - centreX;
        float outY = at.y - centreY;
        float distance = (float) Math.sqrt(outX * outX + outY * outY);
        if (distance <= EPSILON) {
            return;
        }
        outX /= distance;
        outY /= distance;
        Velocity velocity = VELOCITY.get(entity);
        float outward = velocity.x * outX + velocity.y * outY;
        if (outward <= 0f) {
            return;
        }
        float rope = leashRadius - distance;
        float allowance = rope > 0f ? rope / deltaTime : 0f;
        if (outward <= allowance) {
            return;
        }
        float excess = outward - allowance;
        velocity.set(velocity.x - excess * outX, velocity.y - excess * outY);
    }

    /**
     * A move intent longer than one unit is normalised, so holding two keys is not faster than
     * holding one; a shorter one is kept as-is, so a half-pushed stick moves at half speed.
     *
     * <p>The speed is {@link Pace}d first: his velocity is not committed by {@code DriveSystem}.
     */
    private void drive(Entity entity, Intent intent) {
        Velocity velocity = VELOCITY.get(entity);
        float length = (float) Math.sqrt(intent.moveX * intent.moveX + intent.moveY * intent.moveY);
        if (length <= EPSILON) {
            velocity.set(0f, 0f);
            return;
        }
        float speed = SPEED.get(entity).value * Pace.of(entity, map, ground);
        float scale = length > 1f ? speed / length : speed;
        haul(entity, velocity, intent.moveX * scale, intent.moveY * scale, speed);
    }

    /**
     * Writes the velocity he actually gets, which is the one he asked for unless he is hauling
     * something.
     *
     * <p><strong>Backing away, and only backing away</strong>: the part towards the load is dropped,
     * the sideways part partly kept, and the rest slowed. Here, not in {@code DragSystem}, because this
     * system owns his velocity.
     */
    private static void haul(Entity entity, Velocity velocity, float wantX, float wantY, float speed) {
        if (!Drivers.handsFull(entity)) {
            velocity.set(wantX, wantY);
            return;
        }
        Drag drag = DRAG.get(entity);
        float awayX = -drag.towardLoadX;
        float awayY = -drag.towardLoadY;
        float along = wantX * awayX + wantY * awayY;
        // Split first, clamp second, or a step straight at the load survives as "sideways".
        float sidewaysX = (wantX - along * awayX) * drag.lateralFactor;
        float sidewaysY = (wantY - along * awayY) * drag.lateralFactor;
        if (along < 0f) {
            along = 0f;
        }
        float x = along * awayX + sidewaysX;
        float y = along * awayY + sidewaysY;
        float cap = speed * drag.speedFactor;
        float length = (float) Math.sqrt(x * x + y * y);
        if (length > cap && length > EPSILON) {
            x = x / length * cap;
            y = y / length * cap;
        }
        velocity.set(x, y);
    }

    /** The cooldown itself is counted down by {@code CooldownSystem}, before this runs. */
    private void fire(Entity entity, Intent intent) {
        Weapon weapon = WEAPON.get(entity);
        if (!intent.firing || !Guns.ready(entity, weapon)) {
            return;
        }
        Position position = POSITION.get(entity);
        float toAimX = intent.aimX - position.x;
        float toAimY = intent.aimY - position.y;
        float distance = (float) Math.sqrt(toAimX * toAimX + toAimY * toAimY);
        if (distance <= EPSILON) {
            return;
        }
        float directionX = toAimX / distance;
        float directionY = toAimY / distance;
        Guns.volley(
                spawner,
                scatter,
                entity,
                weapon,
                position.x,
                position.y,
                BODY.get(entity).radius,
                MUZZLE_GAP,
                directionX,
                directionY,
                FACTION.get(entity).side,
                muzzle);
        Guns.spend(entity, weapon, weapon.cooldownTicks);
    }
}
