package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a slinger's riders on its back (G0074).
 *
 * <p>The riders are small discs in the rider's own colour, side by side across its back behind its facing, one fewer
 * for every sling - so how many are left is counted at a glance (pillar 5). A rider in the air is drawn over the
 * {@link ShadowMark} a thrown grenade has too. The simulation publishes the count and the state (G0003).
 *
 * @param riderScale a rider's radius, as a fraction of the carrier's
 * @param riderOffset how far behind the carrier's middle the row of riders sits, as a fraction of its radius
 * @param riderSpread how far apart two riders' middles are, as a fraction of the carrier's radius
 */
public record SlingMark(float riderScale, float riderOffset, float riderSpread) {

    public SlingMark {
        if (riderScale <= 0f || riderScale >= 1f) {
            throw new IllegalArgumentException("sling riderScale must be in (0, 1), was " + riderScale);
        }
        if (riderOffset < 0f || riderOffset + riderScale > 1f) {
            throw new IllegalArgumentException("sling riders must sit inside the carrier: riderOffset " + riderOffset
                    + " plus riderScale " + riderScale + " passes one");
        }
        if (riderSpread < 0f) {
            throw new IllegalArgumentException("sling riderSpread must not be negative, was " + riderSpread);
        }
    }

    /** A rider's radius on a carrier of this radius. */
    public float riderFor(float carrier) {
        return carrier * riderScale;
    }

    /**
     * How far to the side of the carrier's back line rider {@code index} of {@code riders} sits, in world units: the
     * row centred on the line.
     */
    public float sideFor(float carrier, int index, int riders) {
        return (index - (riders - 1) * 0.5f) * riderSpread * carrier;
    }
}
