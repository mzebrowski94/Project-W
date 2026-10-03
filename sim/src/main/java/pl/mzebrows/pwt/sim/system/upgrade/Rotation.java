package pl.mzebrows.pwt.sim.system.upgrade;

/**
 * Whose turn a draft is (G0049): the next present slot after the last chooser, in ascending slot
 * order, wrapping round. A slot is present when it has a commander - standing, down or on his way
 * back - and is not away (G0052). Pure.
 */
final class Rotation {

    private Rotation() {}

    /**
     * The slot after {@code after} whose turn it is. If nobody is present, the lowest slot with a
     * commander, whose draft will pass on when somebody returns; if nobody has one at all, slot zero.
     *
     * @param after the last chooser, or -1 before the first draft
     * @param seated by slot, whether it has a commander
     * @param away by slot, whether it is away
     */
    static int next(int after, boolean[] seated, boolean[] away) {
        int slots = seated.length;
        for (int i = 1; i <= slots; i++) {
            int slot = Math.floorMod(after + i, slots);
            if (seated[slot] && !away[slot]) {
                return slot;
            }
        }
        for (int slot = 0; slot < slots; slot++) {
            if (seated[slot]) {
                return slot;
            }
        }
        return 0;
    }
}
