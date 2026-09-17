package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The gun a unit is carrying: which one, how often it may fire, and what it fires.
 *
 * <p>Picking a gun up writes a different {@code WeaponDef} into these fields; nothing swaps a
 * component and nothing branches on which gun it is. {@code remainingSeconds} is the whole of its
 * runtime state, so a snapshot of it replays exactly.
 *
 * <p>The cooldown is deliberately <em>not</em> carried across a swap: a new gun arrives ready. A
 * commander who has just paid the walk to a weapons cache should not then be told to wait.
 */
public final class Weapon implements Component, Pool.Poolable {

    /** Which gun. Reset returns it to the starting one, so the field is never null. */
    public WeaponKind kind = WeaponKind.SIDEARM;

    public float cooldownSeconds;
    public float remainingSeconds;
    public float projectileSpeed;
    public float projectileRadius;
    public float damage;

    /** What its shots are drawn as, so which gun is in hand is visible in what comes out of it. */
    public Look projectileLook = Look.PROJECTILE;

    /** How wide a blast each shot leaves where it stops. Zero for every ordinary gun. */
    public float blastRadius;

    /** Reads a gun's definition in, ready to fire, and returns this component, for fluent spawning. */
    public Weapon set(WeaponKind newKind, WeaponDef def) {
        this.kind = newKind;
        this.cooldownSeconds = def.cooldownSeconds();
        this.remainingSeconds = 0f;
        this.projectileSpeed = def.projectileSpeed();
        this.projectileRadius = def.projectileRadius();
        this.damage = def.damage();
        this.projectileLook = def.projectileLook();
        this.blastRadius = def.blastRadius();
        return this;
    }

    public boolean ready() {
        return remainingSeconds <= 0f;
    }

    @Override
    public void reset() {
        kind = WeaponKind.SIDEARM;
        cooldownSeconds = 0f;
        remainingSeconds = 0f;
        projectileSpeed = 0f;
        projectileRadius = 0f;
        damage = 0f;
        projectileLook = Look.PROJECTILE;
        blastRadius = 0f;
    }
}
