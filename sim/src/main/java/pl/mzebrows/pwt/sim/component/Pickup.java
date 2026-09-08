package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * Something lying on the field waiting to be taken, and whether anything has taken it yet.
 *
 * <p>A pickup is an ordinary entity with a body: non-solid, so units walk over it rather than round
 * it, and immovable, so nothing shoves it away. The constraint solver reports it as a contact for
 * exactly the same reason it reports a projectile, and knows no more about it than that.
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

    /** Sets the kind and returns this component, for fluent spawning. */
    public Pickup set(PickupKind newKind) {
        this.kind = newKind;
        this.claimed = false;
        return this;
    }

    @Override
    public void reset() {
        kind = PickupKind.MUSHROOM;
        claimed = false;
    }
}
