package pl.mzebrows.pwt.ui.page;

/**
 * Which rows of a list too long for its panel are on show, and where its scrollbar's thumb goes - the
 * pause menu's list of upgrades (A.1). A wheel moves it a line a notch; a stick glides it, faster the
 * further it is pushed. It never scrolls past either end, and a list that fits never scrolls at all.
 */
public final class ScrollList {

    /** The first row on show. */
    private int first;

    /** A glide's part-line, carried to the next frame so a gentle push still gets somewhere. */
    private float carry;

    /** The first row on show. */
    public int first() {
        return first;
    }

    /** Back to the top, for a list opened afresh. */
    public void home() {
        first = 0;
        carry = 0f;
    }

    /** Keeps the rows on show inside the list, which may have shrunk or grown since the last frame. */
    public void keep(int rows, int room) {
        first = Math.clamp(first, 0, most(rows, room));
    }

    /** Moves it by whole lines, down for positive. */
    public void step(int lines, int rows, int room) {
        first = Math.clamp((long) first + lines, 0, most(rows, room));
    }

    /**
     * Moves it by a stick's push for a frame.
     *
     * @param push how far the stick is pushed, down for positive, in {@code [-1, 1]}
     * @param linesPerSecond how fast a stick pushed all the way moves it
     */
    public void glide(float push, float linesPerSecond, float seconds, int rows, int room) {
        carry += push * linesPerSecond * seconds;
        int whole = (int) carry;
        carry -= whole;
        if (whole != 0) {
            step(whole, rows, room);
        }
        if ((first == 0 && carry < 0f) || (first == most(rows, room) && carry > 0f)) {
            carry = 0f;
        }
    }

    /** Whether the list is longer than its room, so a scrollbar is drawn. */
    public static boolean scrolls(int rows, int room) {
        return rows > room;
    }

    /** How long the thumb is on a track this long: the share of the list on show, at least one pixel. */
    public static int thumb(int track, int rows, int room) {
        if (!scrolls(rows, room)) {
            return track;
        }
        return Math.max(1, track * Math.max(room, 0) / rows);
    }

    /** How far down the track the thumb starts, so it reaches the bottom exactly when the last row shows. */
    public static int thumbOffset(int track, int first, int rows, int room) {
        int most = most(rows, room);
        if (most == 0) {
            return 0;
        }
        return (track - thumb(track, rows, room)) * first / most;
    }

    private static int most(int rows, int room) {
        return Math.max(0, rows - Math.max(room, 0));
    }
}
