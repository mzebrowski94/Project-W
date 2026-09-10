package pl.mzebrows.pwt.client;

/**
 * How to draw a unit that is acting on an order rather than on its own judgement (3.f).
 *
 * <p>A ring around the body, drawn as an outline so it never hides the unit inside it. The simulation
 * publishes a flag and says nothing about how it should look (G0003).
 *
 * <p>Pillar 5 applied to command. A player who cannot see which of his men heard him has no way to
 * learn how far his voice carries, and would be guessing every time he gave an order - which is
 * exactly the sort of invisible number the pillar exists to rule out.
 *
 * @param scale how far out the ring sits, as a multiple of the body's radius
 * @param tint how far the ring is shifted toward white, in {@code [0, 1]}
 */
public record OrderMark(float scale, float tint) {

    public OrderMark {
        if (scale <= 0f) {
            throw new IllegalArgumentException("order mark scale must be positive, was " + scale);
        }
        if (tint < 0f || tint > 1f) {
            throw new IllegalArgumentException("order mark tint must be in [0, 1], was " + tint);
        }
    }

    /** The radius of the ring for a body of the given radius. */
    public float radiusFor(float bodyRadius) {
        return bodyRadius * scale;
    }
}
