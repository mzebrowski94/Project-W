package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Points everything that has a {@link Facing} where it ended the tick looking.
 *
 * <p>Runs at the end of the tick, after every aim and every move has settled, so the facing
 * {@code PerceptionSystem} reads next tick is the one the renderer drew this frame: the arc the game
 * uses is the arc the player was shown (G0015, pillar 5).
 *
 * <p><strong>Aim first, and never a heading, for anything with an {@link Intent}</strong> (G0002). A
 * commander backing away while shooting forwards faces his shots, or the tell contradicts the bullets.
 * A gun on a mount faces where it points, a trooper where it decided to look, a shooter where its gun
 * is aimed, a barrage where it shoots - which is all that ever turns a thing rooted (G0048) - and anything
 * else that steers itself the way it is going.
 */
public final class FacingSystem extends IteratingSystem {

    private static final ComponentMapper<Barrage> BARRAGE = ComponentMapper.getFor(Barrage.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);

    public FacingSystem() {
        super(Family.all(Facing.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Facing facing = FACING.get(entity);
        if (TURRET.has(entity)) {
            Turret turret = TURRET.get(entity);
            facing.face(turret.aimX, turret.aimY);
            return;
        }
        if (TROOPER.has(entity)) {
            Trooper trooper = TROOPER.get(entity);
            facing.face(trooper.lookX, trooper.lookY);
            return;
        }
        if (INTENT.has(entity) && POSITION.has(entity)) {
            Position at = POSITION.get(entity);
            Intent intent = INTENT.get(entity);
            facing.face(intent.aimX - at.x, intent.aimY - at.y);
            return;
        }
        if (RANGED.has(entity) && RANGED.get(entity).aiming()) {
            Ranged ranged = RANGED.get(entity);
            facing.face(ranged.aimX, ranged.aimY);
            return;
        }
        if (BARRAGE.has(entity) && BARRAGE.get(entity).aiming()) {
            Barrage barrage = BARRAGE.get(entity);
            facing.face(barrage.aimX, barrage.aimY);
            return;
        }
        if (STEERING.has(entity) && VELOCITY.has(entity)) {
            Velocity velocity = VELOCITY.get(entity);
            facing.face(velocity.x, velocity.y);
        }
    }
}
