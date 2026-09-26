package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Damage owed to an entity this tick, waiting to be applied.
 *
 * <p><strong>Contact only adds here; {@code DamageSystem} applies.</strong> So two hits in one tick
 * both count, independent of grid order, and the recovery window a hit asks for is granted after it
 * lands, not before. <strong>Only a target that survives is thrown</strong>: the knockback is recorded
 * and applied only if it lives, so a killing blow leaves the body where it was struck.
 *
 * <p>The window doubles as "already staggered this tick": a hit that grants recovery is exclusive;
 * hits that grant none - projectiles - are not. Carried permanently by everything with {@link Health},
 * zeroed after each application.
 */
public final class Damage implements Component, Pool.Poolable {

    public float amount;

    /** The longest recovery window any of this tick's hits asked for. Applied once the damage is. */
    public int recoveryTicks;

    /** Unit direction of the shove owed, pointing away from whoever swung. */
    public float knockbackX;

    public float knockbackY;

    /** How hard, in world units per second. Zero means no shove was asked for. */
    public float knockbackSpeed;

    /**
     * The handle of whoever gets held still if this damage proves fatal - the one who landed the
     * blow - or {@link Handle#NOBODY} when the blow carries no lock (ADR 0016).
     */
    public long locksOnDeath = Handle.NOBODY;

    public int deathLockTicks;

    /** How fast this body finishes its lunge into whoever killed it, if the blow proves fatal. */
    public float clinchCarrySpeed;

    public float clinchCarryDecay;

    /**
     * Where the hit came from, in world units - the muzzle a shot was fired from, or the attacker
     * itself for a blow. Meaningless unless {@link #hasSource}.
     *
     * <p>A place rather than whoever landed it (G0016).
     */
    public float sourceX;

    public float sourceY;

    /** Whether anything this tick said where it came from. */
    public boolean hasSource;

    /**
     * Whether any of this tick's hits was the kind that kills outright rather than leaving a
     * casualty.
     *
     * <p>Fatal wins when hits combine. A property of the <em>source</em>, not of the amount.
     */
    public boolean fatal;

    /**
     * The worst slow any of this tick's hits asked for, and how long it wanted to last.
     *
     * <p>Applied whether or not the victim survives.
     */
    public float slowFactor = 1f;

    public int slowTicks;

    /** Records a slow, keeping the worst asked for this tick. Nothing at all for a blow that has none. */
    public void slow(float factor, int ticks) {
        if (factor >= 1f || ticks <= 0 || factor >= slowFactor) {
            return;
        }
        slowFactor = factor;
        slowTicks = ticks;
    }

    /** True if something asked to rob this entity of speed this tick. */
    public boolean slowed() {
        return slowFactor < 1f && slowTicks > 0;
    }

    /**
     * How much this tick's hits asked to pump this body up by, added rather than kept at the worst.
     *
     * <p>A quantity, not a state: two beams fill it twice as fast.
     */
    public float bloat;

    /** Records a shot's worth of pumping. Nothing at all for a blow that does none. */
    public void pump(float extra) {
        if (extra > 0f) {
            bloat += extra;
        }
    }

    /**
     * Records where a hit came from, keeping the <strong>first</strong> of the tick.
     *
     * <p>First, so the result does not depend on the order the grid visits pairs.
     */
    public void cameFrom(float x, float y) {
        if (hasSource) {
            return;
        }
        hasSource = true;
        sourceX = x;
        sourceY = y;
    }

    /**
     * Adds a blow that kills outright, and to how long the victim recovers afterwards.
     *
     * <p>A source that leaves a casualty says so with the longer call.
     */
    public void add(float extra, int recovery) {
        add(extra, recovery, true);
    }

    /**
     * Adds to what is already owed, saying whether this blow kills outright or leaves a casualty.
     *
     * <p>{@code kills} is a property of the weapon rather than of the damage.
     */
    public void add(float extra, int recovery, boolean kills) {
        amount += extra;
        if (recovery > recoveryTicks) {
            recoveryTicks = recovery;
        }
        fatal |= kills;
    }

    /** Records a shove, keeping the hardest asked for this tick. Applied only if the target lives. */
    public void push(float directionX, float directionY, float speed) {
        if (speed <= knockbackSpeed) {
            return;
        }
        knockbackX = directionX;
        knockbackY = directionY;
        knockbackSpeed = speed;
    }

    /** True once a hit that claims a recovery window has landed this tick. */
    public boolean staggered() {
        return recoveryTicks > 0;
    }

    /** True if something asked to throw this entity this tick. */
    public boolean pushed() {
        return knockbackSpeed > 0f;
    }

    /** Records who to hold still if this kills, keeping the longest lock asked for this tick. */
    public void lockOnDeath(long attacker, int ticks, float carrySpeed, float carryDecay) {
        if (ticks <= deathLockTicks) {
            return;
        }
        locksOnDeath = attacker;
        deathLockTicks = ticks;
        clinchCarrySpeed = carrySpeed;
        clinchCarryDecay = carryDecay;
    }

    @Override
    public void reset() {
        amount = 0f;
        recoveryTicks = 0;
        knockbackX = 0f;
        knockbackY = 0f;
        knockbackSpeed = 0f;
        locksOnDeath = Handle.NOBODY;
        deathLockTicks = 0;
        clinchCarrySpeed = 0f;
        clinchCarryDecay = 0f;
        sourceX = 0f;
        sourceY = 0f;
        hasSource = false;
        fatal = false;
        slowFactor = 1f;
        slowTicks = 0;
        bloat = 0f;
    }
}
