package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a hole in the ground.
 *
 * <p>Everything else on the field is a body: one filled circle, and what it is doing is a shift in
 * its colour. A nest is not a body - it is dug ground - and drawing it as one more circle would have
 * made it read as an enormous slow creature. Rings darkening inwards read as depth, which is the one
 * thing the placeholder renderer can say about a hole without a texture.
 *
 * <p>It is the second thing in this class's care that a look decides, after which ones are drawn
 * square. Both are the client answering "how is this role drawn", which is exactly the half of G0003
 * that lives on this side of the line: the simulation names a role and never a shape.
 *
 * @param shells how many rings are drawn, including the outermost. One is a plain filled circle
 * @param darken how much of the way to black the innermost ring is, in {@code [0, 1)}. The rings in
 *     between are spaced evenly along it, so raising this deepens the hole rather than adding to it
 */
public record NestMark(int shells, float darken) {

    public NestMark {
        if (shells < 1) {
            throw new IllegalArgumentException("nest shells must be at least one, was " + shells);
        }
        if (darken < 0f || darken >= 1f) {
            throw new IllegalArgumentException("nest darken must be in [0, 1), was " + darken);
        }
    }

    /** The radius of one ring, from the outermost at zero inwards. */
    public float radiusAt(int shell, float bodyRadius) {
        return bodyRadius * (1f - (float) shell / shells);
    }

    /** How far toward black that ring is drawn. Zero for the outermost, {@link #darken} at the core. */
    public float darkenAt(int shell) {
        return shells <= 1 ? 0f : darken * shell / (shells - 1);
    }
}
