package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * Something lying on the field waiting to be taken, and whether anything has taken it yet.
 *
 * <p>Non-solid and immovable; a pickup that walks is solid. {@code claimed} gives it to exactly one
 * taker, in the grid's fixed order. {@code PickupSystem} marks it dead after the grant.
 */
public final class Pickup implements Component, Pool.Poolable {

    /**
     * Which kind it is; everything it does is looked up from this. Reset returns it to the first kind
     * rather than to nothing, so the field is never null; every spawn sets it before anything reads it.
     */
    public PickupKind kind = PickupKind.MUSHROOM;

    /** True once something has claimed it this tick. It is spent, whatever happens next. */
    public boolean claimed;

    /**
     * True if this is a thing to steer around rather than to walk over (G0022).
     *
     * <p>Copied at spawn: avoidance asks it per hazard per tick. Also left out of the hunt for food.
     */
    public boolean shunned;

    /**
     * True if whatever claimed it took it whole rather than setting it off.
     *
     * <p>Recorded at the claim, read later (G0022).
     */
    public boolean swallowed;

    /** Sets the kind and whether it is given a wide berth, for fluent spawning. */
    public Pickup set(PickupKind newKind, boolean newShunned) {
        this.kind = newKind;
        this.shunned = newShunned;
        this.claimed = false;
        this.swallowed = false;
        return this;
    }

    @Override
    public void reset() {
        kind = PickupKind.MUSHROOM;
        claimed = false;
        shunned = false;
        swallowed = false;
    }
}
