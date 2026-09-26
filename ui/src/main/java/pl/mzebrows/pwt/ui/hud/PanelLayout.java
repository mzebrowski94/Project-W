package pl.mzebrows.pwt.ui.hud;

/**
 * Where each player's panel goes in the bottom bar: four fixed quarters of the view's span, one per
 * local seat, in seat order. Fixed rather than divided by how many are playing, because plugging a pad
 * in <b>is</b> joining (G0028) and can happen mid-fight - a divided bar would move and resize everybody's
 * panel at that moment. So nothing here takes a player count.
 *
 * <p>The one degradation: a quarter too narrow for the portrait and a full text column drops the
 * portrait. Label, status, weapon and tally are never dropped.
 *
 * @param padding UI pixels between a quarter's edge and what is in it, and between portrait and text
 * @param portrait the portrait's side in UI pixels
 * @param advance the font's advance: one column of text
 */
public record PanelLayout(int padding, int portrait, int advance) {

    /** Panels in the bar: one per local seat. */
    public static final int QUARTERS = 4;

    /**
     * Columns the text needs beside the portrait: the widest row, three tally counts with their icons,
     * rounded up - {@code SIDEARM} and a heat bar, {@code P8 KIA 1.2} and a bleed-out bar fit inside it.
     */
    public static final int TEXT_COLUMNS = 19;

    /** Left edge of a seat's quarter. */
    public int quarterX(int seat, int viewX, int viewWidth) {
        return viewX + seat * viewWidth / QUARTERS;
    }

    /** Width of a seat's quarter: the view's span in four, the odd pixels spread between them. */
    public int quarterWidth(int seat, int viewWidth) {
        return (seat + 1) * viewWidth / QUARTERS - seat * viewWidth / QUARTERS;
    }

    /** The narrowest quarter that still holds a portrait beside a full text column. */
    public int widthForPortrait() {
        return 2 * padding + portrait + padding + TEXT_COLUMNS * advance;
    }

    public boolean showsPortrait(int quarterWidth) {
        return quarterWidth >= widthForPortrait();
    }

    /** Where a quarter's text starts, relative to its left edge. */
    public int textOffset(int quarterWidth) {
        return showsPortrait(quarterWidth) ? padding + portrait + padding : padding;
    }

    /** How many characters of text a quarter holds. */
    public int textColumns(int quarterWidth) {
        return TextFit.columns(quarterWidth - textOffset(quarterWidth) - padding, advance);
    }
}
