package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * What this creature does to a man it takes hold of rather than bites, and who it has hold of (G0045, G0083).
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

    /**
     * How hard a man is thrown clear when it dies with him in its jaws, in world units per second, and
     * for how many ticks (G0053). Configuration.
     */
    public float escapeKnockbackSpeed;

    public int escapeShoveTicks;

    /** What getting out of its jaws costs him (G0053, G0057). Configuration; {@link Harm#NONE} when off. */
    public Harm escapeHarm = Harm.NONE;

    /**
     * Whether the man it has hold of is held still. One that does not pin <strong>clings</strong> (G0083): he goes
     * where he likes, and it is carried on his edge. Configuration.
     */
    public boolean pins = true;

    /** Whether it takes hold of a man it merely touches, rather than only one its leap lands on. Configuration. */
    public boolean byTouch;

    /** Whether finishing him spends it: it dies with its blow (G0083). Configuration. */
    public boolean spentOnFinish;

    /** The handle of the man in its jaws, or {@link Handle#NOBODY}. */
    public long load = Handle.NOBODY;

    /** How long he has left, in ticks. Meaningless unless {@link #holding()}. */
    public int remainingTicks;

    /** Sets what its leap does and returns this component, for fluent spawning. Lets go of nobody. */
    public Grip set(
            boolean newEnabled,
            float newCarrySpeed,
            int newHoldTicks,
            float newEscapeKnockbackSpeed,
            int newEscapeShoveTicks,
            Harm newEscapeHarm,
            boolean newPins,
            boolean newByTouch,
            boolean newSpentOnFinish) {
        this.enabled = newEnabled;
        this.carrySpeed = newCarrySpeed;
        this.holdTicks = newHoldTicks;
        this.escapeKnockbackSpeed = newEscapeKnockbackSpeed;
        this.escapeShoveTicks = newEscapeShoveTicks;
        this.escapeHarm = newEscapeHarm;
        this.pins = newPins;
        this.byTouch = newByTouch;
        this.spentOnFinish = newSpentOnFinish;
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
        escapeKnockbackSpeed = 0f;
        escapeShoveTicks = 0;
        escapeHarm = Harm.NONE;
        pins = true;
        byTouch = false;
        spentOnFinish = false;
        load = Handle.NOBODY;
        remainingTicks = 0;
    }
}
