package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * Something lying on the field waiting to be taken, and whether anything has taken it yet.
 *
 * <p>A pickup lying on the ground is an ordinary entity with a body: non-solid, so units walk over it
 * rather than round it, and immovable, so nothing shoves it away. The constraint solver reports it as
 * a contact for exactly the same reason it reports a projectile, and knows no more about it than that.
 *
 * <p><strong>A pickup that walks is solid instead</strong>, because those two flags are also what keep
 * a body out of rock and inside the arena, and something that moves under its own power needs both.
 * Nothing else changes: it is claimed on contact like any other, and avoidance is told to ignore
 * pickups outright, so a hungry alien still walks over its dinner rather than politely round it.
 *
 * <p>{@code claimed} is what makes a pickup go to exactly one taker. Two units can touch the same
 * mushroom on the same tick; the first pair the collision grid visits sets this, and the second finds
 * it set and walks on. The grid's order is fixed and hashless, so which of them wins is the same on
 * every replay of the same run.
 *
 * <p>Being claimed is not the same as being gone. The entity survives to the end of the tick and is
 * marked {@code Dead} by {@code PickupSystem} after the grant has been applied, so the reaper takes
 * it in its usual place and nothing has to remove an entity mid-tick.
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
     * <p>Copied from the definition at spawn rather than looked up, because avoidance asks it of
     * every hazard near every steering unit every tick and a table lookup there would be a cost paid
     * for one pickup kind out of six.
     *
     * <p>An idle unit does not go for one either: what is shunned is left out of the hunt for food
     * as well as steered around, or a creature would walk across the arena to a thing it is
     * supposed to be wary of.
     */
    public boolean shunned;

    /**
     * True if whatever claimed it took it whole rather than setting it off.
     *
     * <p>Only an explosive is worth telling apart this way, and only for something that can do
     * anything with one: a creature that swallows a barrel turns it into ammunition, and a creature
     * that merely bites into one sets it off in its own face (G0022). The difference is entirely in
     * what claimed it, which is why it is recorded here at the moment of the claim and read later,
     * exactly as {@link #claimed} is.
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
