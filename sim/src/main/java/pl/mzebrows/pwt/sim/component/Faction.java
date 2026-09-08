package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Which side an entity is on. Damage only ever crosses sides, never stays within one.
 *
 * <p>The sides themselves are {@link Side}, over with the definitions, because definitions need to
 * answer questions about them - what a pickup offers each side - and a definition may not depend on a
 * component. This component is only the entity's membership.
 */
public final class Faction implements Component, Pool.Poolable {

    public Side side = Side.NEUTRAL;

    /** Sets the side and returns this component, for fluent spawning. */
    public Faction set(Side newSide) {
        this.side = newSide;
        return this;
    }

    @Override
    public void reset() {
        side = Side.NEUTRAL;
    }
}
