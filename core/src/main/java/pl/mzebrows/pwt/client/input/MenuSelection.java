package pl.mzebrows.pwt.client.input;

/**
 * Which row of a list is highlighted, and how that moves.
 *
 * <p>Small enough to look obviously correct and exactly the kind of thing that is not: wrap-around
 * at both ends and a direct jump that must ignore rather than accept an out-of-range row.
 */
public final class MenuSelection {

    private final int size;

    private int index;

    public MenuSelection(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("a menu needs at least one entry, was given " + size);
        }
        this.size = size;
    }

    /** Moves up one, wrapping to the bottom from the top. */
    public void previous() {
        index = Math.floorMod(index - 1, size);
    }

    /** Moves down one, wrapping to the top from the bottom. */
    public void next() {
        index = Math.floorMod(index + 1, size);
    }

    /** Jumps straight to a row. Out-of-range rows are ignored, so a stray key cannot break the menu. */
    public boolean select(int candidate) {
        if (candidate < 0 || candidate >= size) {
            return false;
        }
        index = candidate;
        return true;
    }

    public int index() {
        return index;
    }
}
