package pl.mzebrows.pwt.ui.hud;

/**
 * The marks drawn on a player panel. The tally row's three (plan 7.2): a skull for kills, a flag for
 * goals, a plus for men stabilised - a <strong>plus</strong>, never a red cross, which is a protected
 * emblem. And a wound (G0053): a narrow diamond, a slit, filled for a wound he carries and hollow for
 * one he could still take. Drawn as data, a cap height tall, until there is an atlas; {@code #} is lit.
 * Every lit column of the wound sits inside one font advance, so a row of them never touches.
 */
public enum TallyIcon {
    KILLS(".#####.", "#######", "#..#..#", "#######", ".#####.", ".#.#.#.", "......."),
    GOALS("##.....", "####...", "######.", "####...", "##.....", "#......", "#......"),
    STABILISED("..###..", "..###..", "#######", "#######", "#######", "..###..", "..###.."),
    WOUND("...#...", "..###..", "..###..", ".#####.", "..###..", "..###..", "...#..."),
    WOUND_SPARE("...#...", "..#.#..", "..#.#..", ".#...#.", "..#.#..", "..#.#..", "...#...");

    /** Side of every icon, in UI pixels: the font's cap height, so an icon sits on a line like a capital. */
    public static final int SIZE = 7;

    /** The rows, top first, run together: immutable, as an enum's fields must be. */
    private final String texels;

    TallyIcon(String... rows) {
        this.texels = String.join("", rows);
        if (texels.length() != SIZE * SIZE) {
            throw new IllegalStateException(name() + " is not " + SIZE + " by " + SIZE);
        }
    }

    /** Whether the texel at column {@code x}, row {@code y} (from the top) is lit. */
    public boolean lit(int x, int y) {
        return texels.charAt(y * SIZE + x) == '#';
    }

    /** How a count is shown beside its icon: the number, and {@code 999+} above that - never wider than four. */
    public static String count(int value) {
        return value > 999 ? "999+" : Integer.toString(Math.max(0, value));
    }
}
