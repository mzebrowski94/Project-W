package pl.mzebrows.pwt.client.draw;

/**
 * How to draw which way an entity is pointing.
 *
 * <p>The simulation publishes a unit vector and says nothing about how it should look (G0003). This
 * is the whole of the client's answer: a short spoke from the body's centre, out past its rim, in a
 * brighter shade of the body's own colour. It reads as a nose or a muzzle at any size, it costs one
 * quad, and it needs no art.
 *
 * <p>It exists because facing is now gameplay rather than flavour. A trooper engages what is inside
 * its arc (G0015), and an arc whose direction the player cannot see is a rule that can only be learnt
 * by dying to it - which is the readability objection G0011 raised against line of sight, and the
 * reason this is built before the arc rather than after it.
 *
 * <p>A creature's spoke is a spike instead - a long triangle as long as the spoke, broad at the centre
 * and sharp at the tip - so which way the swarm is pointing reads apart from which way the squad is.
 *
 * <p>Anything with no facing publishes {@code (0, 0)} and gets no spoke, so a bullet is not drawn
 * with a nose and the renderer never asks what it is looking at.
 *
 * @param length how far the spoke reaches from the centre, as a fraction of the body's radius. Above
 *     one on purpose: a spoke that stops at the rim is swallowed by the circle at small sizes
 * @param width how thick it is, as a fraction of the body's radius
 * @param spike how broad a creature's spike is at its base, across the centre, as a fraction of the
 *     body's radius; it tapers to a point at {@code length}
 * @param tint how far the spoke is shifted toward white, in {@code [0, 1]}, so it reads against the
 *     body it sits on
 */
public record FacingMark(float length, float width, float spike, float tint) {

    public FacingMark {
        if (length <= 0f) {
            throw new IllegalArgumentException("facing length must be positive, was " + length);
        }
        if (width <= 0f) {
            throw new IllegalArgumentException("facing width must be positive, was " + width);
        }
        if (spike <= 0f) {
            throw new IllegalArgumentException("facing spike must be positive, was " + spike);
        }
        if (tint < 0f || tint > 1f) {
            throw new IllegalArgumentException("facing tint must be in [0, 1], was " + tint);
        }
    }

    /** How far the spoke reaches for a body of the given radius, in world units. */
    public float lengthFor(float bodyRadius) {
        return bodyRadius * length;
    }

    /** How thick the spoke is for a body of the given radius, in world units. */
    public float widthFor(float bodyRadius) {
        return bodyRadius * width;
    }

    /** How broad a creature's spike is at its base for a body of the given radius, in world units. */
    public float spikeFor(float bodyRadius) {
        return bodyRadius * spike;
    }
}
