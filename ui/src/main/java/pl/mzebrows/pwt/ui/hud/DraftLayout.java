package pl.mzebrows.pwt.ui.hud;

/**
 * Where the draft goes in the top bar (G0049): the chooser - his portrait and his label - on the left,
 * then the cards side by side across the rest of the view's span. Pure arithmetic, in UI pixels.
 *
 * @param padding between the bar's edge and its content, and between cards
 * @param portrait the chooser's portrait's side
 * @param advance one character's width
 */
public record DraftLayout(int padding, int portrait, int advance) {

    /** Characters beside the portrait: {@code P8} over {@code 1/2}. */
    private static final int LABEL_COLUMNS = 4;

    /** Width of the chooser's block, padding included. */
    public int chooserWidth() {
        return padding + portrait + padding + LABEL_COLUMNS * advance + padding;
    }

    /** Width of each of {@code cards} cards in a span this wide. */
    public int cardWidth(int span, int cards) {
        return cards <= 0 ? 0 : (span - chooserWidth() - padding) / cards;
    }

    /** Left edge of card {@code index}, from the span's left edge. */
    public int cardX(int span, int cards, int index) {
        return chooserWidth() + index * cardWidth(span, cards);
    }

    /** Characters a card's text may use. */
    public int cardColumns(int span, int cards) {
        return TextFit.columns(cardWidth(span, cards) - 2 * padding, advance);
    }
}
