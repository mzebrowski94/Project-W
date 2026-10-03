package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Letting go of a grenade, in one place, for everything that throws one: where it leaves the hand, and
 * what the throw costs the thrower. Which way it goes is the caller's - along a direction, never at a
 * point (G0019) - and must already be a unit vector.
 *
 * <p>One per simulation. It leaves the hand by the same gap a shot leaves a gun by.
 */
public final class Throws {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final float muzzleGap;

    /** @param muzzleGap gap between the thrower's body and what leaves it, so a throw never starts inside him */
    public Throws(Spawner spawner, Modifiers modifiers, float muzzleGap) {
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.muzzleGap = muzzleGap;
    }

    /**
     * How far from this thrower's centre what he throws comes down, if nothing turns it: where it leaves
     * the hand, plus the distance it spends (G0019). The throw's reach, for anyone deciding whether to
     * make it.
     */
    public float reach(Entity entity, Thrower thrower) {
        return BODY.get(entity).radius + thrower.radius + muzzleGap + thrower.range;
    }

    /** Throws one grenade from this thrower along this direction, and starts his wait for the next. */
    public void release(Entity entity, Thrower thrower, float directionX, float directionY) {
        Position at = POSITION.get(entity);
        float muzzle = BODY.get(entity).radius + thrower.radius + muzzleGap;
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
