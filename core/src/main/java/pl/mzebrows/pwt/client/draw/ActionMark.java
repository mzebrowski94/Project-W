package pl.mzebrows.pwt.client.draw;

/**
 * How the action key's **brackets** are drawn, and its **prompt** (G0098), under {@code graphic.action}: four
 * corner marks round the **subject** in {@code graphic.color.actionMark}, fainter ones round what else the key
 * could act on within the **notice distance**, and smaller ticks in {@code graphic.color.pickupMark} round a
 * pickup worth something to a commander near it. Never a ring. World units, but for the prompt's.
 *
 * @param noticeDistance how near, surface to surface, a thing must be to a commander here to be bracketed faintly;
 *     its own number, never a multiple of reach. Zero brackets nothing faintly
 * @param gap how far out from a body or a doorway the brackets stand
 * @param corner how long each corner's arms are, as a share of the bracketed box's side
 * @param width how thick an arm is drawn
 * @param faintAlpha how opaque the faint brackets are
 * @param tickGap, tick, tickWidth the same three for a pickup's ticks - smaller, so they never read as the action's
 * @param chip the prompt over the subject
 */
public record ActionMark(
        float noticeDistance,
        float gap,
        float corner,
        float width,
        float faintAlpha,
        float tickGap,
        float tick,
        float tickWidth,
        Chip chip) {

    public ActionMark {
        if (noticeDistance < 0f || gap < 0f || tickGap < 0f || width <= 0f || tickWidth <= 0f) {
            throw new IllegalArgumentException("action brackets: distances are not negative and widths positive");
        }
        if (!(corner > 0f && corner <= 0.5f && tick > 0f && tick <= 0.5f)) {
            throw new IllegalArgumentException("a bracket's arm is a share of its box's side, at most half");
        }
        if (!(faintAlpha >= 0f && faintAlpha <= 1f)) {
            throw new IllegalArgumentException("faint brackets' alpha is a share: " + faintAlpha);
        }
    }

    /** Whether anything is bracketed faintly at all. */
    public boolean notices() {
        return noticeDistance > 0f;
    }

    /**
     * The prompt: a see-through backing behind the key and the verb, thinner while an alien body is under it.
     *
     * @param alpha the backing's alpha over open ground
     * @param occludedAlpha its alpha while an alien body is under it
     * @param fadeSeconds how long the change between the two takes
     * @param lift how many UI pixels above the brackets' top it sits, and between two stacked
     */
    public record Chip(float alpha, float occludedAlpha, float fadeSeconds, int lift) {

        public Chip {
            if (lift < 0) {
                throw new IllegalArgumentException("a prompt's lift is not negative: " + lift);
            }
        }
    }
}
