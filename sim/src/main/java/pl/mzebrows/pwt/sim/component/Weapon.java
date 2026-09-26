package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The gun a unit is carrying: which one, how often it may fire, and what it fires.
 *
 * <p>Picking a gun up writes a different {@code WeaponDef} in; nothing branches on which gun it is.
 * The cooldown and the muzzle side are its runtime state; a new gun arrives ready.
 */
public final class Weapon implements Component, Pool.Poolable {

    /** Which gun. Reset returns it to the starting one, so the field is never null. */
    public WeaponKind kind = WeaponKind.SIDEARM;

    public int cooldownTicks;
    public int remainingTicks;
    public float projectileSpeed;
    public float projectileRadius;

    /** How far one of its shots flies before it expires. Zero is until it hits something. */
    public float projectileRange;

    public float damage;

    /** How many shots one pull sends. One for every gun that fires a single round. */
    public int pellets = 1;

    /** Half the cone the gun throws them into, in radians. Zero fires dead straight. */
    public float halfSpreadRadians;

    /** What its shots are drawn as, so which gun is in hand is visible in what comes out of it. */
    public Look projectileLook = Look.PROJECTILE;

    /** How wide a blast each shot leaves where it stops. Zero for every ordinary gun. */
    public float blastRadius;

    /**
     * What each shot leaves lying on the ground where it bursts. {@link SlickDef#off()} for every
     * gun that leaves nothing, which is all of them but one.
     *
     * <p>The immutable record itself: safe on a pooled component, unlike an {@code Entity}.
     */
    public SlickDef slicks = SlickDef.off();

    /** How far to either side of the line of fire its muzzles sit. Zero for a gun with one. */
    public float muzzleOffset;

    /**
     * How long one of its shots waits between the blows it lands. Zero is a shot that stops at the
     * first thing it touches, which is what every gun but one declares.
     */
    public float pierceIntervalSeconds;

    /**
     * How much of its speed a unit one of its shots lands on keeps, and for how long. One and zero
     * for every gun that robs nothing, which is all of them but the beam.
     */
    public float slowFactor = 1f;

    public int slowTicks;

    /** How much of his own speed the shooter keeps while he is firing this, and for how long. */
    public float selfSlowFactor = 1f;

    public int selfSlowTicks;

    /** How far back along its path one of its shots is drawn. Zero is a dot, which is almost every gun. */
    public float trailLength;

    /** How much of a body one of its shots pumps up. Zero for every gun that does nothing to one. */
    public float bloatPerShot;

    /** What one shot adds to whatever is holding this, as a fraction. Zero for a gun that stays cold. */
    public float heatPerShot;

    /** How much of that comes off per second, and how far it must fall before the gun comes back. */
    public float heatCoolPerSecond;

    public float heatResumeBelow;

    /**
     * Which of the two it fires from next. Runtime state, like the cooldown, and meaningless on a
     * gun with one muzzle - where the offset is zero and both sides are the same point.
     */
    public boolean nextMuzzleRight = true;

    /** Reads a gun's definition in, ready to fire, and returns this component, for fluent spawning. */
    public Weapon set(WeaponKind newKind, WeaponDef def, float tickSeconds) {
        this.kind = newKind;
        this.cooldownTicks = Ticks.of(def.cooldownSeconds(), tickSeconds);
        this.remainingTicks = 0;
        this.projectileSpeed = def.projectileSpeed();
        this.projectileRadius = def.projectileRadius();
        this.projectileRange = def.projectileRange();
        this.damage = def.damage();
        this.pellets = def.pellets();
        this.halfSpreadRadians = def.halfSpreadRadians();
        this.projectileLook = def.projectileLook();
        this.blastRadius = def.blastRadius();
        this.slicks = def.slicks();
        this.muzzleOffset = def.muzzleOffset();
        this.pierceIntervalSeconds = def.pierceIntervalSeconds();
        this.slowFactor = def.slowFactor();
        this.slowTicks = Ticks.of(def.slowSeconds(), tickSeconds);
        this.selfSlowFactor = def.selfSlowFactor();
        this.selfSlowTicks = Ticks.of(def.selfSlowSeconds(), tickSeconds);
        this.trailLength = def.trailLength();
        this.bloatPerShot = def.bloatPerShot();
        this.heatPerShot = def.heat().perShot();
        this.heatCoolPerSecond = def.heat().coolPerSecond();
        this.heatResumeBelow = def.heat().resumeBelow();
        this.nextMuzzleRight = true;
        return this;
    }

    public boolean ready() {
        return remainingTicks <= 0;
    }

    @Override
    public void reset() {
        kind = WeaponKind.SIDEARM;
        cooldownTicks = 0;
        remainingTicks = 0;
        projectileSpeed = 0f;
        projectileRadius = 0f;
        projectileRange = 0f;
        damage = 0f;
        pellets = 1;
        halfSpreadRadians = 0f;
        projectileLook = Look.PROJECTILE;
        blastRadius = 0f;
        slicks = SlickDef.off();
        muzzleOffset = 0f;
        pierceIntervalSeconds = 0f;
        slowFactor = 1f;
        slowTicks = 0;
        selfSlowFactor = 1f;
        selfSlowTicks = 0;
        trailLength = 0f;
        bloatPerShot = 0f;
        heatPerShot = 0f;
        heatCoolPerSecond = 0f;
        heatResumeBelow = 0f;
        nextMuzzleRight = true;
    }
}
