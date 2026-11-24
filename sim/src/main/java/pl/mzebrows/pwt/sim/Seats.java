package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Respawning;

/**
 * Which slots are seated: have a commander, standing, down or on his way back (G0052). Whether the slot's machine
 * is away is a separate question - a seated slot may be away.
 *
 * <p>One answer for everything that deals per player - every seated slot's drafts (G0118), and the grenade caches a taken point lays
 * (G0078). In the {@code sim} root rather than {@code query}, as {@code Life} is, because the upgrade package asks it
 * and calls no other (ADR 0017).
 */
public final class Seats {

    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Respawning> RESPAWNING = ComponentMapper.getFor(Respawning.class);

    private Seats() {}

    /**
     * Fills {@code seated}, by slot, from the commanders on the field and the ones on their way back; a slot
     * outside the table is ignored.
     */
    public static void mark(boolean[] seated, ImmutableArray<Entity> commanders, ImmutableArray<Entity> returning) {
        for (int slot = 0; slot < seated.length; slot++) {
            seated[slot] = false;
        }
        for (int i = 0; i < commanders.size(); i++) {
            sit(seated, COMMANDER.get(commanders.get(i)).slot);
        }
        for (int i = 0; i < returning.size(); i++) {
            sit(seated, RESPAWNING.get(returning.get(i)).slot);
        }
    }

    /** How many slots are seated, through {@link #mark} into {@code scratch}. */
    public static int count(boolean[] scratch, ImmutableArray<Entity> commanders, ImmutableArray<Entity> returning) {
        mark(scratch, commanders, returning);
        int seated = 0;
        for (boolean sitting : scratch) {
            if (sitting) {
                seated++;
            }
        }
        return seated;
    }

    private static void sit(boolean[] seated, int slot) {
        if (slot >= 0 && slot < seated.length) {
            seated[slot] = true;
        }
    }
}
