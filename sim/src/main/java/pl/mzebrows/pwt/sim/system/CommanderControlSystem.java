package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
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

/**
 * Turns a commander's intent into movement and gunfire.
 *
 * <p>Movement and aim are read separately and never derived from each other (G0002): the velocity
 * this writes says nothing about where the commander is pointing, and the direction a projectile
 * leaves in says nothing about where the commander is walking.
 *
 * <p>A commander held still by a blow - or lying on the field having taken a fatal one - runs none of
 * this. It does not zero his velocity either: while he is held, that belongs to {@code StunSystem},
 * which bleeds off whatever the blow gave him.
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

    public CommanderControlSystem(Spawner spawner) {
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
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (Drivers.held(entity)) {
            return;
        }
        Intent intent = INTENT.get(entity);
        drive(entity, intent);
        if (Drivers.handsFull(entity)) {
            return;
        }
        fire(entity, intent);
    }

    /**
     * A move intent longer than one unit is normalised, so holding two keys is not faster than
     * holding one; a shorter one is kept as-is, so a half-pushed stick moves at half speed.
     */
    private static void drive(Entity entity, Intent intent) {
        Velocity velocity = VELOCITY.get(entity);
        float length = (float) Math.sqrt(intent.moveX * intent.moveX + intent.moveY * intent.moveY);
        if (length <= EPSILON) {
            velocity.set(0f, 0f);
            return;
        }
        float speed = SPEED.get(entity).value;
        float scale = length > 1f ? speed / length : speed;
        haul(entity, velocity, intent.moveX * scale, intent.moveY * scale, speed);
    }

    /**
     * Writes the velocity he actually gets, which is the one he asked for unless he is hauling
     * something.
     *
     * <p><strong>Backing away, and only backing away.</strong> The step is split along the line to
     * the load: whatever part of it points towards what he is dragging is thrown away outright, and
     * whatever part is sideways survives only in part. So he reverses out of a fight with a man in
     * one hand, leaning left and right, and cannot walk forwards over him. Everything that is left
     * is then slowed, because hauling is not walking.
     *
     * <p>It happens here rather than in {@code DragSystem} because this system owns his velocity.
     * Two systems writing one velocity is how a unit ends up travelling somewhere neither asked for,
     * so the other one publishes the bearing and this one decides what to do about it.
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
        // Split first, clamp second. Clamping to zero and then subtracting would leave the whole
        // step behind as "sideways", so walking straight at the load would carry him straight over
        // it at full lateral speed - the constraint reading as if it were not there at all.
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
        if (!intent.firing || !weapon.ready()) {
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
        float muzzle = BODY.get(entity).radius + weapon.projectileRadius + MUZZLE_GAP;
        spawner.spawnProjectile(
                position.x + directionX * muzzle,
                position.y + directionY * muzzle,
                directionX,
                directionY,
                weapon,
                FACTION.get(entity).side);
        weapon.remainingSeconds = weapon.cooldownSeconds;
    }
}
