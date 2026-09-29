package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Which form a unit currently has.
 *
 * <p>An enum, not a marker per form, so evolving changes no component set mid-tick. Behaviour that
 * does not care selects on {@link Enemy}. Every number lives in the {@code UnitDef};
 * {@code Spawner.applyStrain} is the single place that writes it into components.
 */
public final class Strain implements Component, Pool.Poolable {

    /**
     * The form. Never null: reset returns it to the first strain.
     */
    public UnitKind kind = UnitKind.GRUNT;

    /**
     * The wave that sent it, counting from one; zero for one a level placed, a nest bred or a lair
     * raised. Stamped at spawn and never changed - an evolution keeps it - so a wave is known to be
     * cleared once nothing it sent is still on its feet.
     */
    public int wave;

    /** Sets the strain and returns this component, for fluent spawning. */
    public Strain set(UnitKind newKind) {
        this.kind = newKind;
        return this;
    }

    @Override
    public void reset() {
        kind = UnitKind.GRUNT;
        wave = 0;
    }
}
