package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * Damage this entity is currently ignoring.
 *
 * <p>Two sources, one component. {@code remainingSeconds} counts down and covers the moments after
 * being hit and after respawning. {@code permanent} is the harness god-mode toggle and never expires
 * - it is a dev tool, not a mechanic, and pillar 2 says the commander can be killed, so nothing in a
 * scenario or in {@code game.properties} may switch it on.
 *
 * <p>Carried permanently by anything that can be hit, rather than added and removed. Adding or
 * removing a component mid-tick mutates the families being iterated, which silently skips entities.
 */
public final class Invulnerable implements Component, Pool.Poolable {

    public int remainingTicks;
    public boolean permanent;

    /** True if damage should be ignored right now. */
    public boolean active() {
        return permanent || remainingTicks > 0;
    }

    /** Extends protection to at least this long, never shortening what is already running. */
    public void grant(int ticks) {
        if (ticks > remainingTicks) {
            remainingTicks = ticks;
        }
    }

    @Override
    public void reset() {
        remainingTicks = 0;
        permanent = false;
    }
}
