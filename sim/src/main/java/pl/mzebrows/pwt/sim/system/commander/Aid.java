package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Putting a man back on his feet, in one place, for everybody who can do it.
 *
 * <p>The commander and a trooper <strong>must do exactly the same thing</strong>: every rule is about
 * the man on the ground, not who reached him. Who is being worked on is re-resolved every tick (G0010).
 */
final class Aid {

    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private Aid() {}

    /**
     * A tick's worth of work on him, and puts him back on his feet once there has been enough of it.
     *
     * <p>He comes up with every wound cleared and briefly protected (G0053). Returns whether this was the tick he came up - the
     * stabilise a commander's tally counts (G0044). How much a tick of work is worth is the one place
     * the {@code STABILISE_SPEED} stat is read, for whoever is doing it.
     */
    static boolean work(
            Modifiers modifiers, Entity helper, Entity subject, float deltaTime, float invulnerableSeconds) {
        Vitality casualty = VITALITY.get(subject);
        casualty.stabilised += modifiers.stabiliseWork(helper, Vitality.TICK);
        if (!casualty.stabilisedEnough(hauled(subject))) {
            return false;
        }
        // Up with every wound cleared: the one way anything heals a man (G0053).
        casualty.recover();
        if (INVULNERABLE.has(subject)) {
            INVULNERABLE.get(subject).grant(Ticks.of(invulnerableSeconds, deltaTime));
        }
        return true;
    }

    /**
     * Loses the work done so far.
     *
     * <p>A commitment, not an accumulation.
     */
    static void letGo(Entity subject) {
        VITALITY.get(subject).stabilised = 0;
    }

    /** How far through being put back on his feet he is, in {@code [0, 1]}, for the interface. */
    static float progress(Entity subject) {
        return VITALITY.get(subject).stabiliseProgress(hauled(subject));
    }

    /** Whether he was carried here, which is what makes the work at this end so much longer. */
    private static boolean hauled(Entity subject) {
        return DRAGGED.has(subject) && DRAGGED.get(subject).hauled;
    }
}
