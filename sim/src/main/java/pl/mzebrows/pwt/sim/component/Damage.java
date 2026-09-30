package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * The blows that landed on an entity this tick, waiting to be applied: the one place a blow is
 * recorded, and the rules for how the tick's blows combine.
 *
 * <p><strong>Whatever lands a blow says so here, through {@link #land}; {@code DamageSystem}
 * applies.</strong> So two hits in one tick both count, independent of grid order, and the recovery
 * window a hit asks for is granted after it lands, not before. <strong>Only a target that survives is
 * thrown</strong>: the knockback is recorded and applied only if it lives, so a killing blow leaves the
 * body where it was struck.
 *
 * <p><strong>A blow counts because it landed, not because it owes anything</strong> (G0057): one that
 * owes no hit points and harms a man not at all still breaks his shield, still provokes, and still
 * throws. What it owes is {@link #amount} for anything with health and {@link #harm} for a man.
 *
 * <p>The window doubles as "already staggered this tick": a hit that grants recovery is exclusive;
 * hits that grant none - projectiles - are not. Carried permanently by everything with {@link Health}
 * or {@link Vitality}, cleared after every tick whether or not anything landed.
 */
public final class Damage implements Component, Pool.Poolable {

    /** Whether any blow landed this tick. Nothing else here means anything while it is false. */
    public boolean landed;

    /** Whole hit points owed (G0054). */
    public int amount;

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
     * The worst harm any of this tick's hits declared, for a man (G0053).
     *
     * <p>The worst wins when hits combine. A property of the <em>source</em>, not of the amount.
     */
    public Harm harm = Harm.NONE;

    /** How long the shove owed throws him for, where a blow shoved without locking; zero otherwise. */
    public int shoveTicks;

    /** How fast that shove bleeds off, in world units per second per second. */
    public float shoveDecay;

    /** The longest stun any of this tick's hits declared, in ticks; zero for none (G0053). */
    public int stunTicks;

    /** Records a stun, keeping the longest asked for this tick. */
    private void stun(int ticks) {
        if (ticks > stunTicks) {
            stunTicks = ticks;
        }
    }

    /**
     * The worst slow any of this tick's hits asked for, and how long it wanted to last.
     *
     * <p>Applied whether or not the victim survives.
     */
    public float slowFactor = 1f;

    public int slowTicks;

    /** Records a slow, keeping the worst asked for this tick. Nothing at all for a blow that has none. */
    private void slow(float factor, int ticks) {
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
    private void pump(float extra) {
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
     * Records a blow a {@link ContactDamage} describes landing: what it owes, what it does to a man, and
     * the stun, slow, pumping and credit it carries. Where it came from, and what it does to the two
     * bodies, depend on where they stand, and are said with {@link #cameFrom}, {@link #push},
     * {@link #shove} and {@link #lockOnDeath}.
     */
    public void land(ContactDamage blow) {
        land(blow.amount, blow.victimInvulnerableTicks, blow.harm, blow.credit);
        stun(blow.stunTicks);
        slow(blow.slowFactor, blow.slowTicks);
        pump(blow.bloat);
    }

    /**
     * Records a blow no {@link ContactDamage} describes - a blast, getting out of a grip: hit points for
     * anything with health, a harm for a man (G0020, G0053), and the slot a kill by it counts for.
     */
    public void land(int extra, Harm blowHarm, int slot) {
        land(extra, 0, blowHarm, slot);
    }

    /** The same, counting for nobody. */
    public void land(int extra, Harm blowHarm) {
        land(extra, blowHarm, Tally.NOBODY);
    }

    private void land(int extra, int recovery, Harm blowHarm, int slot) {
        landed = true;
        amount += extra;
        if (recovery > recoveryTicks) {
            recoveryTicks = recovery;
        }
        harm = Harm.worse(harm, blowHarm);
        creditTo(slot);
    }

    /**
     * Records a shove that throws without locking, keeping the hardest asked for this tick. Applied only
     * if the target is left on his feet.
     */
    public void shove(float directionX, float directionY, float speed, int ticks, float decay) {
        if (speed <= knockbackSpeed) {
            return;
        }
        push(directionX, directionY, speed);
        shoveTicks = ticks;
        shoveDecay = decay;
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

    /**
     * The slot whose tally a kill by this tick's blows counts for, or {@link Tally#NOBODY}. When two
     * players' blows land on one tick the <strong>higher slot</strong> is credited - the rule orders
     * already follow (G0028) - never whichever the collision grid happened to visit first.
     */
    public int credit = Tally.NOBODY;

    /** Records the player behind a blow, keeping the higher slot of the tick. */
    private void creditTo(int slot) {
        if (slot > credit) {
            credit = slot;
        }
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
        landed = false;
        amount = 0;
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
        harm = Harm.NONE;
        shoveTicks = 0;
        shoveDecay = 0f;
        stunTicks = 0;
        slowFactor = 1f;
        slowTicks = 0;
        bloat = 0f;
        credit = Tally.NOBODY;
    }
}
