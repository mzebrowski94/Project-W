package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Letting go of a grenade, in one place, for everything that throws one: where it leaves the hand, and
 * what the throw costs the thrower. Which way it goes is the caller's - along a direction, never at a
 * point (G0019) - and must already be a unit vector.
 */
final class Throws {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    /** Gap between the thrower's body and what leaves it, so a throw never starts inside him. */
    private static final float MUZZLE_GAP = 0.05f;

    private Throws() {}

    /** Throws one grenade from this thrower along this direction, and starts his wait for the next. */
    static void release(
            Spawner spawner, Modifiers modifiers, Entity entity, Thrower thrower, float directionX, float directionY) {
        Position at = POSITION.get(entity);
        float muzzle = BODY.get(entity).radius + thrower.radius + MUZZLE_GAP;
        spawner.spawnGrenade(
                at.x + directionX * muzzle,
                at.y + directionY * muzzle,
                directionX,
                directionY,
                thrower,
                Players.credit(entity));
        thrower.remainingTicks = modifiers.grenadeCooldownTicks(entity, thrower.cooldownTicks);
    }
}
