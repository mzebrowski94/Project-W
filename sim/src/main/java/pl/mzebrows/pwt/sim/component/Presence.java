package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/**
 * The slots that are away (G0052): their link has dropped, so their commanders stand idle and no
 * solo pause is theirs - and which machine each slot sits at, which the leash is reckoned by
 * (G0056). Level state on the level entity, written by {@code Simulation.step} from the tick's input and
 * nothing else. Offline nothing is ever away, and every slot sits at machine 0.
 */
public final class Presence implements Component, Pool.Poolable {

    /** One slot that is away. */
    public record Away(int slot) {}

    public final Array<Away> away = new Array<>();

    /** Which machine one slot's player sits at (G0056). */
    public record Seat(int slot, int machine) {}

    /**
     * Which machine each slot's player sits at, as the last join said. A slot nobody has joined sits at
     * machine 0, which is every slot offline.
     */
    public final Array<Seat> seats = new Array<>();

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

    /** Seats a slot at a machine. Allocates, once per join. */
    public void seat(int slot, int machine) {
        for (int i = 0; i < seats.size; i++) {
            if (seats.get(i).slot() == slot) {
                if (seats.get(i).machine() != machine) {
                    seats.set(i, new Seat(slot, machine));
                }
                return;
            }
        }
        seats.add(new Seat(slot, machine));
    }

    /** The machine this slot's player sits at: 0 for a slot nobody has joined. */
    public int machineOf(int slot) {
        for (int i = 0; i < seats.size; i++) {
            if (seats.get(i).slot() == slot) {
                return seats.get(i).machine();
            }
        }
        return 0;
    }

    @Override
    public void reset() {
        away.clear();
        seats.clear();
    }
}
