package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How a unit uses the gun it is carrying: how close it gets before firing, how loosely it shoots,
 * and whether it stands still to do it.
 *
 * <p>What it fires is on {@link Weapon}, which is the same component a trooper and the commander
 * carry and is filled from the same table. This is only the half that is a fact about the creature
 * rather than about the gun.
 *
 * <p>Carried by every chasing unit and switched on per strain, exactly as {@link Leap},
 * {@link Wander} and {@link Avoidance} are. A strain that does not shoot reports {@code enabled}
 * false and costs one comparison; nothing adds or removes a component to make a unit a shooter,
 * because changing an entity's component set mid-tick silently skips entities.
 *
 * <p>The cone is held as half its width in radians rather than as the whole thing in degrees,
 * because that is what the firing system wants and because converting degrees in a per-entity loop
 * would mean a transcendental in a hot path, which is banned here. The conversion happens once, at
 * spawn, in {@code RangedDef.halfSpreadRadians()}.
 */
public final class Ranged implements Component, Pool.Poolable {

    public boolean enabled;

    /** How close what it is hunting must be before it fires, in world units. */
    public float range;

    /** Half the cone a shot may leave in, in radians. Zero fires dead straight. */
    public float halfSpreadRadians;

    /** Whether it plants itself while its target is in range rather than continuing to close. */
    public boolean haltsToFire;

    /** Sets how this unit shoots and returns this component, for fluent spawning. */
    public Ranged set(boolean newEnabled, float newRange, float newHalfSpreadRadians, boolean newHaltsToFire) {
        this.enabled = newEnabled;
        this.range = newRange;
        this.halfSpreadRadians = newHalfSpreadRadians;
        this.haltsToFire = newHaltsToFire;
        return this;
    }

    @Override
    public void reset() {
        enabled = false;
        range = 0f;
        halfSpreadRadians = 0f;
        haltsToFire = false;
    }
}
