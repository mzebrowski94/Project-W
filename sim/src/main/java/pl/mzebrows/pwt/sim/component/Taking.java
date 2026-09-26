package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A pickup this entity has laid claim to and not yet been given.
 *
 * <p><strong>Contact records what is owed; {@code PickupSystem} decides.</strong> One claim per tick.
 * Carried permanently.
 */
public final class Taking implements Component, Pool.Poolable {

    /** What was claimed, or null for nothing this tick. */
    public @Nullable PickupKind claimed;

    /**
     * How readily this unit bites into something it ought to be wary of, per second of touching one.
     *
     * <p>{@code AiChance.BITE_A_BARREL} for its strain, copied at spawn. Zero on the player's side.
     */
    public float biteChance;

    /**
     * What it is still due before it next bites into something it is touching: {@code Odds}' budget
     * for {@link #biteChance}. Zero until the first time it touches one.
     */
    public float biteDue;

    /**
     * Whether it takes an explosive whole rather than setting it off.
     *
     * <p>True for anything with a gun a barrel can charge (G0022).
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
        biteDue = 0f;
        swallows = false;
    }
}
