package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How long an entity is unable to act, and whether what is holding it is a clinch or a shove.
 *
 * <p>Stun blocks <em>deciding</em>, not <em>moving</em>. A stunned unit runs no control, no chase and
 * no leap, and lands no blow; it is still integrated by the movement system and still shoved by the
 * constraint solver. That split is deliberate and load-bearing: knockback is a velocity, so an entity
 * frozen in place could not be thrown anywhere, and a stunned unit that stopped colliding would let a
 * crowd walk through it.
 *
 * <p>{@code grappling} is the difference between the two things a landed blow can be. A blow that
 * kills locks both parties together and neither is going anywhere for a moment - that is a clinch, and
 * both of them are marked with it. A blow that is survived only staggers the one that took it; the one
 * that landed it shoved something away and is free to keep fighting. The renderer reads this to decide
 * whether to draw a grapple or a stagger, and it is the only thing that tells those two apart.
 *
 * <p>{@code knockbackDecay} is carried here rather than read from config by the system that applies
 * it, because how fast a throw bleeds off is a property of the blow that landed - a heavier melee
 * weapon shoves harder and for longer.
 *
 * <p>Carried permanently by anything that can be stunned, rather than added and removed. Adding or
 * removing a component mid-tick mutates the families being iterated, which silently skips entities.
 */
public final class Stunned implements Component, Pool.Poolable {

    public int remainingTicks;

    /** World units per second per second bled off the knockback while the stun runs. */
    public float knockbackDecay;

    /** True when this hold is half of a lethal exchange rather than the recoil from a shove. */
    public boolean grappling;

    /**
     * True when this hold is a stun a source declared rather than the recoil of a blow - the one the
     * renderer draws as its own state (G0053). Cleared when the hold runs out.
     */
    public boolean dazed;

    /** How long the declared stun running now was when it landed, in ticks: what the view shrinks its mark over. */
    public int dazeTicks;

    /**
     * True for a body no blow holds still - a slither (G0095): no stagger, kick or clinch lands on it, and only a
     * stun a source declared ({@link #daze}) stops it. Configuration, set with its strain.
     */
    public boolean immune;

    /** Says whether only a declared stun may hold it still, returning this component, for fluent spawning. */
    public Stunned immuneIf(boolean onlyAStunHoldsIt) {
        this.immune = onlyAStunHoldsIt;
        if (onlyAStunHoldsIt) {
            remainingTicks = 0;
        }
        return this;
    }

    /** True if this entity may not act right now. */
    public boolean active() {
        return remainingTicks > 0;
    }

    /** Holds it still for at least this long, never shortening a stun already running. A blow's hold: none on the immune. */
    public void apply(int ticks, float decay) {
        if (immune) {
            return;
        }
        hold(ticks);
        if (decay > 0f) {
            knockbackDecay = decay;
        }
    }

    /**
     * Holds it still for at least this long, as a stun rather than a recoil - the one hold that lands on a body
     * immune to a blow's (G0095).
     */
    public void daze(int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (ticks >= remainingTicks) {
            dazeTicks = ticks;
        }
        hold(ticks);
        dazed = true;
    }

    /** At least this long, whatever is holding it. */
    private void hold(int ticks) {
        if (ticks > remainingTicks) {
            remainingTicks = ticks;
        }
    }

    /** How much of a declared stun is left, in {@code [0, 1]}; zero with none running. */
    public float dazeLeft() {
        if (!dazed || dazeTicks <= 0 || remainingTicks <= 0) {
            return 0f;
        }
        return Math.min(1f, remainingTicks / (float) dazeTicks);
    }

    /**
     * Turns a hold into a clinch, and stops whoever it holds dead.
     *
     * <p>Called once the blow is known to have killed - never undone, because a clinch that downgraded
     * to a shove partway through would flicker on screen.
     *
     * <p><strong>A clinch clears the knockback decay rather than carrying one.</strong> A decay
     * describes a throw bleeding off, and a killing blow throws nothing: the two of them are tangled
     * together, not shoved apart. Carrying the weapon's decay in here left the survivor coasting on
     * whatever he was doing when he swung, so a commander who killed a leaper while backing away slid
     * out from under the body he had just been clinched to - the gap reopening under a freeze that was
     * supposed to have closed it.
     */
    public void clinch(int ticks) {
        if (immune) {
            return;
        }
        hold(ticks);
        knockbackDecay = 0f;
        grappling = true;
    }

    @Override
    public void reset() {
        remainingTicks = 0;
        knockbackDecay = 0f;
        grappling = false;
        dazed = false;
        dazeTicks = 0;
        immune = false;
    }
}
