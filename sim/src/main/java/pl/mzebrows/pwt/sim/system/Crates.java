package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.system.query.Reach;

/**
 * Opening a crate and finding the one a slot has open (G0056), for {@code ActionSystem}, which owns the key,
 * and {@code CrateSystem}, which owns everything after - the way {@link Crews} serves the gun. Finding one
 * within arm's length is {@link Reach}'s.
 */
final class Crates {

    private static final ComponentMapper<Crate> CRATE = ComponentMapper.getFor(Crate.class);

    private Crates() {}

    /** The crate this slot has open, or null. A slot has at most one: he cannot open another meanwhile. */
    static @Nullable Entity openBy(ImmutableArray<Entity> crates, int slot) {
        for (int i = 0; i < crates.size(); i++) {
            Crate crate = CRATE.get(crates.get(i));
            if (crate.openedBy == slot) {
                return crates.get(i);
            }
        }
        return null;
    }

    /**
     * Opens a crate for a slot, if nobody has it (G0056). {@code ActionSystem} opens in slot order, so two
     * presses on one tick go to the lower slot, and the higher one to the next crate in his reach.
     */
    static void open(Entity entity, int slot, long tick) {
        Crate crate = CRATE.get(entity);
        if (crate.open()) {
            return;
        }
        crate.openedBy = slot;
        crate.openedOn = tick;
        crate.openerWounds = 0;
    }
}
