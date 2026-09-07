package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How an entity takes part in collision, as data rather than as a class of entity.
 *
 * <p>This is what keeps the constraint solver purely geometric: it never asks whether something is a
 * projectile or a wall, only whether it is solid and whether it can be pushed. A trigger volume, a
 * pickup, a destructible crate and a moving hazard are all combinations of these two flags rather
 * than new branches in the solver.
 *
 * <p>{@code solid} means two of these push each other out of the way; a pair where either is
 * non-solid still reports a contact, which is how a projectile hits without shoving. {@code movable}
 * means the solver may write to its position - a false here is what makes an obstacle immovable and
 * what keeps a projectile from being clamped inside the arena instead of leaving it.
 */
public final class Collider implements Component, Pool.Poolable {

    public boolean solid;
    public boolean movable;

    /** Sets both flags and returns this component, for fluent spawning. */
    public Collider set(boolean newSolid, boolean newMovable) {
        this.solid = newSolid;
        this.movable = newMovable;
        return this;
    }

    @Override
    public void reset() {
        solid = false;
        movable = false;
    }
}
