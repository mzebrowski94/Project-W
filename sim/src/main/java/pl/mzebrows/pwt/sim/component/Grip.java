package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What this creature's leap does to a man it lands on, and who it has in its jaws (G0045).
 *
 * <p>Carried by every strain and switched off for all but one, like every other ability.
 *
 * <p><strong>The man is named by his handle, never by a reference</strong> (ADR 0016): a carry lasts
 * seconds, and a pooled component holding an {@code Entity} that long comes back aimed at whatever was
 * built from the recycled instance. The man carries the other half of the pair on {@link Gripped}, and
 * {@code GripSystem} lets go the moment the two stop agreeing.
 */
public final class Grip implements Component, Pool.Poolable {

    /** Whether its leap takes hold at all. Configuration. */
    public boolean enabled;

    /** How fast it goes with a man in its jaws, in world units per second. Configuration. */
    public float carrySpeed;

    /** How long it carries a man before it finishes him, in ticks. Configuration. */
    public int holdTicks;

    /** The handle of the man in its jaws, or {@link Handle#NOBODY}. */
    public long load = Handle.NOBODY;

    /** How long he has left, in ticks. Meaningless unless {@link #holding()}. */
    public int remainingTicks;

    /** Sets what its leap does and returns this component, for fluent spawning. Lets go of nobody. */
    public Grip set(boolean newEnabled, float newCarrySpeed, int newHoldTicks) {
        this.enabled = newEnabled;
        this.carrySpeed = newCarrySpeed;
        this.holdTicks = newHoldTicks;
        return this;
    }

    /** True while it has somebody in its jaws. */
    public boolean holding() {
        return load != Handle.NOBODY;
    }

    /** Takes hold of the man with this handle, and starts his clock. */
    public void take(long man) {
        load = man;
        remainingTicks = holdTicks;
    }

    /** Lets go, keeping the configuration. */
    public void release() {
        load = Handle.NOBODY;
        remainingTicks = 0;
    }

    /** How much of his time he has left, in {@code [0, 1]}, read backwards like a casualty's. */
    public float fraction() {
        return holdTicks <= 0 ? 0f : Math.max(0f, remainingTicks / (float) holdTicks);
    }

    @Override
    public void reset() {
        enabled = false;
        carrySpeed = 0f;
        holdTicks = 0;
        load = Handle.NOBODY;
        remainingTicks = 0;
    }
}
