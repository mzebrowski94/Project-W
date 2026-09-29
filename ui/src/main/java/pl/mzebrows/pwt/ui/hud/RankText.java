package pl.mzebrows.pwt.ui.hud;

/** A rank as the interface writes it: roman, the way the upgrade's name wears it ({@code LARGER ARMY II}). */
public final class RankText {

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private RankText() {}

    /** The rank in roman numerals, or in digits past ten; empty below one. Allocates nothing. */
    public static String roman(int rank) {
        if (rank < 1) {
            return "";
        }
        return rank <= ROMAN.length ? ROMAN[rank - 1] : Integer.toString(rank);
    }
}
