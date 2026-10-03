package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A death that takes time: the entity's body stays on the field for a while before it is removed.
 *
 * <p>Without this a killed unit vanishes on the tick it ran out of health, which reads as a
 * disappearance rather than a death. The window this opens is where the dying animation will play;
 * until there is art, it is a frozen body in the colour the renderer gives a corpse.
 *
 * <p>It does not remove anything. When the throes run out {@code DyingSystem} marks the entity
 * {@code Dead} and the reaper takes it at the end of that tick, so the ordinary death reactions -
 * respawning among them - still run exactly once and in their usual place (G0004).
 *
 * <p>Carried permanently by anything that dies visibly, and asked of {@link pl.mzebrows.pwt.sim.Life#dying}: adding it at
 * the moment of death would mutate a family mid-iteration, which silently skips entities.
 */
public final class Dying implements Component, Pool.Poolable {

    /** How long the body lingers once the killing blow lands. Configuration, not state. */
    public int throesTicks;

    public boolean begun;

    public int remainingTicks;

    /** Sets how long the body will linger, and returns this component, for fluent spawning. */
    public Dying set(int newThroesTicks) {
        this.throesTicks = newThroesTicks;
        this.begun = false;
        this.remainingTicks = 0;
        return this;
    }

    /** Starts the throes. Ignored if they are already running, so a second blow cannot restart them. */
    public void begin() {
        if (begun) {
            return;
        }
        begun = true;
        remainingTicks = throesTicks;
    }

    /** True while the body is on the field but the entity is no longer alive. */
    public boolean active() {
        return begun && remainingTicks > 0;
    }

    /** True once the body has lain there long enough to be taken away. */
    public boolean spent() {
        return begun && remainingTicks <= 0;
    }

    @Override
    public void reset() {
        throesTicks = 0;
        begun = false;
        remainingTicks = 0;
    }
}
