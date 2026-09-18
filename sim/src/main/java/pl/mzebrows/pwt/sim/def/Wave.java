package pl.mzebrows.pwt.sim.def;

import pl.mzebrows.pwt.map.TileRect;

/**
 * One lot of aliens, and when and where it arrives.
 *
 * <p>A level names a list of these and they arrive in order, which is the whole of what makes a
 * defence a defence rather than one long fight: the ground in front of you empties, and then it does
 * not. A wave that arrives with the last one still standing is a level's business rather than this
 * record's - it simply says how long after the one before it comes.
 *
 * @param afterSeconds how long after the previous wave - or after the level starts, for the first
 * @param kind which strain arrives
 * @param count how many
 * @param where the tiles they are put down in, jittered inside them like everything else (G0009)
 */
public record Wave(float afterSeconds, UnitKind kind, int count, TileRect where) {

    public Wave {
        Require.notNegative("wave afterSeconds", afterSeconds);
        Require.notNegative("wave count", count);
    }
}
