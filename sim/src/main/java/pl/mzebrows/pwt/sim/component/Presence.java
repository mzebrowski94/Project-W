package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/**
 * The slots that are away (G0052): their link has dropped, so their commanders stand idle and the
 * draft rotation passes them by. Level state on the level entity, written by {@code Simulation.step}
 * from the tick's input and nothing else. Offline nothing is ever away.
 */
public final class Presence implements Component, Pool.Poolable {

    /** One slot that is away. */
    public record Away(int slot) {}

    public final Array<Away> away = new Array<>();

    /** Whether this slot is away. */
    public boolean isAway(int slot) {
        for (int i = 0; i < away.size; i++) {
            if (away.get(i).slot() == slot) {
                return true;
            }
        }
        return false;
    }

    /** Marks a slot away. Allocates, once per link that drops. */
    public void leave(int slot) {
        if (!isAway(slot)) {
            away.add(new Away(slot));
        }
    }

    /** Marks a slot present again. */
    public void comeBack(int slot) {
        for (int i = away.size - 1; i >= 0; i--) {
            if (away.get(i).slot() == slot) {
                away.removeIndex(i);
            }
        }
    }

    @Override
    public void reset() {
        away.clear();
    }
}
