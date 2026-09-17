package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A pickup this entity has laid claim to and not yet been given.
 *
 * <p>This is the pickup half of the pattern {@code ContactDamage} and {@code Damage} already use, and
 * for the same reason: <strong>contact records what is owed, and a later system decides what it
 * costs.</strong> Collision resolution knows two circles overlapped; it does not know whether the
 * taker is about to die this tick, what the grant means, or whether the level has room for what it
 * grants. Writing the claim here and letting {@code PickupSystem} resolve it keeps all of that out of
 * the geometry.
 *
 * <p>One claim per tick, deliberately. A unit that walks across two mushrooms in a single tick takes
 * one of them and leaves the other standing, which is both the fair reading and the one that cannot
 * surprise anybody.
 *
 * <p>Carried permanently by anything that can take a pickup, rather than added and removed. Adding or
 * removing a component mid-tick mutates the families being iterated, which silently skips entities.
 */
public final class Taking implements Component, Pool.Poolable {

    /** What was claimed, or null for nothing this tick. */
    public @Nullable PickupKind claimed;

    /**
     * How readily this unit bites into something it ought to be wary of, per second of touching one.
     *
     * <p>{@code AiChance.BITE_A_BARREL} for its strain, copied here at spawn like every other
     * per-strain number, so the one place that asks the question needs no definition table. It is
     * what makes a barrel a hazard the aliens mostly avoid and occasionally set off in their own
     * faces - and, turned well up for a strain that shoots, what makes one worth standing next to.
     *
     * <p>Zero for anything a shunned pickup offers nothing to, which is everything on the player's
     * side: the commander walks over a barrel and leaves it standing, as he always did.
     */
    public float biteChance;

    /**
     * Whether it takes an explosive whole rather than setting it off.
     *
     * <p>True for anything with a gun a barrel can charge. It is the difference between a barrel
     * being a gift and a barrel being a problem: a creature that closes and claws bites into one and
     * goes up with it, and a shooter swallows it and comes away with artillery (G0022).
     */
    public boolean swallows;

    /** Sets how this unit treats an explosive it touches, for fluent spawning. */
    public Taking set(float newBiteChance, boolean newSwallows) {
        this.biteChance = newBiteChance;
        this.swallows = newSwallows;
        this.claimed = null;
        return this;
    }

    /** True if this entity is owed a pickup that has not been applied yet. */
    public boolean hasClaim() {
        return claimed != null;
    }

    /**
     * Lays claim to a pickup. Returns false if this entity already claimed something this tick, so
     * the caller knows not to spend the pickup.
     */
    public boolean claim(PickupKind kind) {
        if (claimed != null) {
            return false;
        }
        claimed = kind;
        return true;
    }

    @Override
    public void reset() {
        claimed = null;
        biteChance = 0f;
        swallows = false;
    }
}
