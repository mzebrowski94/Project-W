package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a creature in two segments: a body behind and a head in front, along its facing.
 *
 * <p>The simulation's body is still one circle (ADR 0007) - what is hit is what is drawn round - and
 * the segments are the client's answer to the one question the placeholder circle cannot answer: which
 * end is the head. That matters because the head is armoured and the back is not (G0045), and a player
 * who cannot see which way the thing is facing cannot know where to stand (pillar 5). Both segments sit
 * inside the body's circle, so nothing is drawn where a shot would pass through.
 *
 * @param bodyOffset how far behind the centre the rear segment sits, as a fraction of the body's radius
 * @param bodyScale how big the rear segment is, as a fraction of the body's radius
 * @param headOffset how far in front of the centre the head sits, as a fraction of the body's radius
 * @param headScale how big the head is, as a fraction of the body's radius
 * @param headShade how far the head is shifted toward black, in {@code [0, 1]}: the hard end reads darker
 */
public record SegmentMark(float bodyOffset, float bodyScale, float headOffset, float headScale, float headShade) {

    public SegmentMark {
        if (bodyScale <= 0f || headScale <= 0f) {
            throw new IllegalArgumentException("segment scales must be positive");
        }
        if (bodyOffset + bodyScale > 1f || headOffset + headScale > 1f) {
            throw new IllegalArgumentException("both segments must sit inside the body they are drawn for");
        }
        if (headShade < 0f || headShade > 1f) {
            throw new IllegalArgumentException("segment headShade must be in [0, 1], was " + headShade);
        }
    }
}
