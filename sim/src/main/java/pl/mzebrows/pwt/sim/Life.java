package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Vitality;

/**
 * A unit's <strong>life stage</strong>: on its feet, a casualty, or in its throes - the one place that
 * reads the two flags saying so.
 *
 * <p>{@code Vitality} says a man is on the ground and {@code Dying} that a unit has been killed, and a man
 * killed while on the ground carries both. Every question about the stage is one of the three below; a
 * system asking a fourth way is how one of them comes to count a corpse as a casualty.
 */
public final class Life {

    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    private Life() {}

    /** Off his feet: a casualty or in his throes. Neither drives, and neither is a target. */
    public static boolean down(Entity entity) {
        return (VITALITY.has(entity) && VITALITY.get(entity).begun) || dying(entity);
    }

    /**
     * On the ground bleeding and not yet killed: somebody can still put him back on his feet (G0053). Only a
     * man can be one.
     */
    public static boolean casualty(Entity entity) {
        return VITALITY.has(entity) && VITALITY.get(entity).begun && !dying(entity);
    }

    /** Killed, and its body still on the field: scenery, and past helping. */
    public static boolean dying(Entity entity) {
        return DYING.has(entity) && DYING.get(entity).begun;
    }
}
