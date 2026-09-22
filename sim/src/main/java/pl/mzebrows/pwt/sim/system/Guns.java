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
 * <p>Three things fire: the commander, a shooting alien, and a gun on a mount. Every one of them
 * asks the same two questions - may I, and what did that cost - and the answers must be identical,
 * or a gun that runs hot in one pair of hands is free in another. The same argument {@link Aid}
 * makes for one revive path and {@link Muzzle} for one muzzle.
 *
 * <p>Something carrying no {@link Heat} fires as it always did. That is what keeps heat a property
 * of the gun and of who is holding it, rather than a new rule every shooter has to opt into.
 *
 * <p><strong>And what one pull of the trigger puts in the air.</strong> A gun that names more than
 * one pellet sends that many, each scattered inside the cone the <em>gun</em> declares - which is a
 * different cone from the one a creature's bad aim makes, and composes with it rather than replacing
 * it. It is here rather than at each shooter for the reason the two questions above are: a scattergun
 * that covered ground in the commander's hands and fired one round from a mount would be a gun whose
 * behaviour depended on who was holding it.
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
     * <p>The self-slow is here for the reason the heat is: a gun that slowed the commander in his
     * hands and nobody on a mount would be a gun whose cost depended on who was holding it. A gun
     * that names none writes nothing, and a shooter with no {@code Slowed} - a turret - is unaffected
     * either way.
     */
    static void spend(Entity firer, Weapon weapon, float cooldownSeconds) {
        weapon.remainingSeconds = cooldownSeconds;
        if (HEAT.has(firer)) {
            HEAT.get(firer).spend(weapon.heatPerShot);
        }
        if (SLOWED.has(firer)) {
            SLOWED.get(firer).apply(weapon.selfSlowFactor, weapon.selfSlowSeconds);
        }
    }

    /**
     * Everything one pull of this trigger puts in the air, along the direction it was aimed.
     *
     * <p>The aim must already be a unit vector, and already carry whatever the <em>shooter</em> was
     * going to do to it - a creature's cone is applied once to the whole volley, because it is one
     * bad guess rather than one per pellet. What is applied here is the gun's own scatter, per
     * pellet, which is the whole of what makes a scattergun cover ground.
     *
     * <p>Heat and the cooldown are the caller's, through {@link #spend}: what one pull costs is not
     * a question about how many things it put in the air.
     *
     * <p>{@code muzzle} is the caller's scratch pair, so a volley allocates nothing. Neither does the
     * turn: a pellet is nudged by a rotation rather than by aiming somewhere else, so every pellet of
     * a volley is the same length and the cone is the same width at every distance - the trade
     * {@code RangedDef} already makes, for the same reason.
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
