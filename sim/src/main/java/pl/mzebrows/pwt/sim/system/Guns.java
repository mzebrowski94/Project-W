package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Weapon;

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
 */
final class Guns {

    private static final ComponentMapper<Heat> HEAT = ComponentMapper.getFor(Heat.class);

    private Guns() {}

    /** Whether this thing may fire at all this tick: the gun is off cooldown and has not stopped itself. */
    static boolean ready(Entity firer, Weapon weapon) {
        if (!weapon.ready()) {
            return false;
        }
        return !HEAT.has(firer) || !HEAT.get(firer).locked;
    }

    /** Charges a shot to the cooldown and to the heat, whichever of them this gun actually has. */
    static void spend(Entity firer, Weapon weapon, float cooldownSeconds) {
        weapon.remainingSeconds = cooldownSeconds;
        if (HEAT.has(firer)) {
            HEAT.get(firer).spend(weapon.heatPerShot);
        }
    }
}
