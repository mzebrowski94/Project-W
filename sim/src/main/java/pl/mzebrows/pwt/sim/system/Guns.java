package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * What it costs to pull a trigger, in one place, for everything that pulls one.
 *
 * <p>Every shooter asks the same questions - may I, what did it cost, what goes in the air - and the
 * answers must not depend on whose hands the gun is in. Something with no {@link Heat} never runs hot.
 */
final class Guns {

    private static final ComponentMapper<Heat> HEAT = ComponentMapper.getFor(Heat.class);
    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);

    private Guns() {}

    /** Whether this thing may fire at all this tick: the gun is off cooldown and has not stopped itself. */
    static boolean ready(Entity firer, Weapon weapon) {
        if (!weapon.ready()) {
            return false;
        }
        return !HEAT.has(firer) || !HEAT.get(firer).locked;
    }

    /**
     * Charges a shot to the cooldown, to the heat, and to the shooter's own pace - whichever of the
     * three this gun actually asks for.
     *
     * <p>A shooter with no {@code Slowed} - a turret - is unaffected by a self-slow.
     */
    static void spend(Entity firer, Weapon weapon, int cooldownTicks) {
        weapon.remainingTicks = cooldownTicks;
        if (HEAT.has(firer)) {
            HEAT.get(firer).spend(weapon.heatPerShot);
        }
        if (SLOWED.has(firer)) {
            SLOWED.get(firer).apply(weapon.selfSlowFactor, weapon.selfSlowTicks);
        }
    }

    /**
     * Everything one pull of this trigger puts in the air, along the direction it was aimed.
     *
     * <p>The aim must be a unit vector already carrying the shooter's cone; the gun's scatter is applied
     * here per pellet, by rotation. Cost is {@link #spend}'s. {@code muzzle} is the caller's scratch.
     */
    static void volley(
            Spawner spawner,
            Rng rng,
            Entity firer,
            Weapon weapon,
            float x,
            float y,
            float bodyRadius,
            float gap,
            float aimX,
            float aimY,
            Side side,
            float[] muzzle) {
        int pellets = weapon.pellets < 1 ? 1 : weapon.pellets;
        for (int pellet = 0; pellet < pellets; pellet++) {
            float directionX = aimX;
            float directionY = aimY;
            if (weapon.halfSpreadRadians > 0f) {
                float turn = (rng.nextFloat() * 2f - 1f) * weapon.halfSpreadRadians;
                float cos = (float) StrictMath.cos(turn);
                float sin = (float) StrictMath.sin(turn);
                directionX = aimX * cos - aimY * sin;
                directionY = aimX * sin + aimY * cos;
            }
            Muzzle.next(x, y, bodyRadius, weapon, gap, directionX, directionY, muzzle);
            spawner.spawnProjectile(muzzle[0], muzzle[1], directionX, directionY, weapon, side);
        }
    }
}
