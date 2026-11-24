package pl.mzebrows.pwt.sim.def;

import java.util.List;
import pl.mzebrows.pwt.map.TileRect;

/**
 * One lot of aliens, and when and where it arrives.
 *
 * <p>A level names a list of these and they arrive in order, which is the whole of what makes a
 * defence a defence rather than one long fight: the ground in front of you empties, and then it does
 * not. A wave that arrives with the last one still standing is a level's business rather than this
 * record's - it simply says how long after the one before it comes.
 *
 * <p><strong>From one place or several at once</strong> (G0087): a wave is one or more {@link Lot}s, all put down on
 * the tick it arrives and counted as one wave - one stamp, one clearing.
 *
 * @param afterSeconds how long after the previous wave - or after the level starts, for the first
 * @param kind which strain arrives
 * @param lots how many arrive where, at least one
 */
public record Wave(float afterSeconds, UnitKind kind, List<Lot> lots) {

    public Wave {
        Require.notNegative("wave afterSeconds", afterSeconds);
        lots = List.copyOf(lots);
        if (lots.isEmpty()) {
            throw new IllegalArgumentException("a wave arrives somewhere, had no lots");
        }
    }

    /** A wave from one place, as every level but a finale's sends. */
    public Wave(float afterSeconds, UnitKind kind, int count, TileRect where) {
        this(afterSeconds, kind, List.of(new Lot(count, where)));
    }

    /** How many arrive, over every place. */
    public int count() {
        int count = 0;
        for (Lot lot : lots) {
            count += lot.count();
        }
        return count;
    }

    /**
     * Some of a wave, and where it is put down.
     *
     * @param count how many
     * @param where the tiles they are put down in, jittered inside them like everything else (G0009)
     */
    public record Lot(int count, TileRect where) {

        public Lot {
            Require.notNegative("wave count", count);
        }
    }
}
