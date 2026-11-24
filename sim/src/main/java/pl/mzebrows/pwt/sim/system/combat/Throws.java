package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
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
 *
 * <p>Under Engineer (G0129) the same press sets a sentry down at his feet instead, for the same grenade and the same
 * wait - refused, spending nothing, where the ground there is solid.
 */
public final class Throws {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private final Spawner spawner;
    private final Modifiers modifiers;
    private final Solid solid;
    private final float muzzleGap;
    private final float sentryRadius;

    /**
     * @param muzzleGap gap between the thrower's body and what leaves it, so a throw never starts inside him
     * @param sentryRadius how big a sentry he sets down is, so it comes down clear of him
     */
    public Throws(Spawner spawner, Modifiers modifiers, Solid solid, float muzzleGap, float sentryRadius) {
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.solid = solid;
        this.muzzleGap = muzzleGap;
        this.sentryRadius = sentryRadius;
    }

    /**
     * How far from this thrower's centre what he throws comes down, if nothing turns it: where it leaves
     * the hand, plus the distance it spends (G0019). The throw's reach, for anyone deciding whether to
     * make it.
     */
    public float reach(Entity entity, Thrower thrower) {
        return BODY.get(entity).radius + thrower.radius + muzzleGap + thrower.range;
    }

    /**
     * How many grenades he carries at most, upgrades included (G0077) - the one place it is folded, for the
     * throw and for his panel. Zero for a thrower whose throws are not counted.
     */
    public int most(Entity entity, Thrower thrower) {
        return thrower.counted() ? modifiers.grenadeMost(entity, thrower.most) : 0;
    }

    /** How many he has left to throw (G0077); zero for a thrower whose throws are not counted. */
    public int left(Entity entity, Thrower thrower) {
        return Math.max(0, most(entity, thrower) - thrower.spent);
    }

    /**
     * Whether his press sets a sentry down rather than throwing (G0129) - the one place it is folded, for the press and
     * for his panel.
     */
    public boolean setsSentries(Entity entity) {
        return modifiers.setsSentries(entity);
    }

    /** Whether he can throw now: his wait has run out and, where his throws are counted, he has one left. */
    public boolean ready(Entity entity, Thrower thrower) {
        return thrower.ready() && (!thrower.counted() || left(entity, thrower) > 0);
    }

    /**
     * Throws one grenade from this thrower along this direction, spends it where his throws are counted, and starts
     * his wait for the next.
     */
    public void release(Entity entity, Thrower thrower, float directionX, float directionY) {
        if (setsSentries(entity)) {
            setDown(entity, thrower, directionX, directionY);
            return;
        }
        Position at = POSITION.get(entity);
        float muzzle = BODY.get(entity).radius + thrower.radius + muzzleGap;
        spawner.spawnGrenade(
                at.x + directionX * muzzle,
                at.y + directionY * muzzle,
                directionX,
                directionY,
                thrower,
                Players.credit(entity));
        pay(entity, thrower);
    }

    /**
     * Sets a sentry down just in front of him along this direction, clear of his body by the gap a throw leaves it by,
     * and pays for it as for a grenade (G0129). Nothing - and nothing spent - where the ground between is solid.
     */
    private void setDown(Entity entity, Thrower thrower, float directionX, float directionY) {
        Position at = POSITION.get(entity);
        float gap = BODY.get(entity).radius + sentryRadius + muzzleGap;
        float x = at.x + directionX * gap;
        float y = at.y + directionY * gap;
        if (!solid.isClearPath(at.x, at.y, x, y, sentryRadius)) {
            return;
        }
        spawner.spawnSentry(x, y, Players.credit(entity));
        pay(entity, thrower);
    }

    /** What a throw costs, a grenade's or a sentry's: one spent where his throws are counted, and his wait begun. */
    private void pay(Entity entity, Thrower thrower) {
        if (thrower.counted()) {
            thrower.spent++;
        }
        thrower.remainingTicks = modifiers.grenadeCooldownTicks(entity, thrower.cooldownTicks);
    }
}
