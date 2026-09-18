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

    /**
     * Whether it takes part in collision at all.
     *
     * <p>The third flag, and the one that says "not on the field right now" - a man aboard a turret
     * is inside the thing rather than standing beside it, so nothing may shove him, shoot him or
     * claw him while he is on it. Everything else that is out of the pairwise path is out of it by
     * carrying no collider at all; a man who is sometimes aboard cannot be, because adding and
     * removing a component mid-tick mutates the families being iterated.
     *
     * <p>It is still a fact about how this entity collides rather than a question about what it is,
     * which is the whole point of this component: the solver reads the flag and asks nothing.
     */
    public boolean present;

    /** Sets both flags and returns this component, for fluent spawning. */
    public Collider set(boolean newSolid, boolean newMovable) {
        this.solid = newSolid;
        this.movable = newMovable;
        this.present = true;
        return this;
    }

    @Override
    public void reset() {
        solid = false;
        movable = false;
        present = false;
    }
}
