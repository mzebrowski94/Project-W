package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Which form a unit currently has.
 *
 * <p>This replaced a marker component per form, and the reason is evolution. Turning a grunt into a
 * brute by adding a {@code Brute} marker would mean changing an entity's component set in the middle
 * of a tick, which mutates the families being iterated and silently skips entities - the same trap
 * that {@code Damage}, {@code Dying} and {@code Stunned} are all carried permanently to avoid.
 * Writing an enum here changes nothing about which families the entity belongs to.
 *
 * <p>Everything that used to ask "is this a brute?" now asks what its strain is, and everything that
 * did not care - chasing, leaping, taking a bullet - still selects on {@link Enemy} and never learns
 * that strains exist.
 *
 * <p>The look and every number a strain has live in its {@code UnitDef}. Nothing copies them here;
 * {@code Spawner.applyStrain} is the single place that reads a definition into an entity's
 * components, so the strain and what is drawn cannot drift apart.
 */
public final class Strain implements Component, Pool.Poolable {

    /**
     * The form. Reset returns it to the first strain rather than to nothing, so the field is never
     * null and no hot path has to check; every spawn sets it before anything reads it.
     */
    public UnitKind kind = UnitKind.GRUNT;

    /** Sets the strain and returns this component, for fluent spawning. */
    public Strain set(UnitKind newKind) {
        this.kind = newKind;
        return this;
    }

    @Override
    public void reset() {
        kind = UnitKind.GRUNT;
    }
}
