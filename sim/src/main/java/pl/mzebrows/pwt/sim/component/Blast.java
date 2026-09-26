package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A circle of ground that is lethal for a moment.
 *
 * <p><strong>It holds no radius of its own.</strong> The blast's {@code Body} is the lethal circle,
 * and that same body is what the renderer draws, so the ring the player sees cannot drift from the
 * ring that kills - there is one number rather than two kept in step. Pillar 5 by construction rather
 * than by discipline.
 *
 * <p>It is not a collider either. A body this wide in the pairwise path would set the collision
 * grid's cell size for the whole level to serve an effect that lasts a quarter of a second, so the
 * radius test is its own - exactly the trade a control point already makes for the same reason.
 *
 * <p>{@code spent} is what makes a blast an <em>event</em> rather than a place. It catches whatever is
 * standing in it on the one tick it goes off and nothing after that, so the rest of its life is only
 * the flash. Running through the tail of one has to be safe, or every explosion would leave a trap
 * behind it whose edge the player cannot see.
 */
public final class Blast implements Component, Pool.Poolable {

    /**
     * What it takes off whatever it catches. Enough to kill anything on the field: a blast is fatal
     * or it is nothing, which is the whole of what makes it readable at a glance.
     */
    public float damage;

    /** How long the flash lasts. Nothing to do with how long it is dangerous, which is one tick. */
    public int durationTicks;

    public int remainingTicks;

    /** True once it has caught what was standing in it. */
    public boolean spent;

    /** Sets the blast and starts its flash, returning this component, for fluent spawning. */
    public Blast set(float newDamage, int newDurationTicks) {
        this.damage = newDamage;
        this.durationTicks = newDurationTicks;
        this.remainingTicks = newDurationTicks;
        this.spent = false;
        return this;
    }

    /** How much of the flash is left, in {@code [0, 1]}, for the renderer to fade it out by. */
    public float fade() {
        return durationTicks <= 0 ? 0f : remainingTicks / (float) durationTicks;
    }

    @Override
    public void reset() {
        damage = 0f;
        durationTicks = 0;
        remainingTicks = 0;
        spent = false;
    }
}
