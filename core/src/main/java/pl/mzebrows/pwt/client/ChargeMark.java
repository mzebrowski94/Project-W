package pl.mzebrows.pwt.client;

/**
 * How to draw an entity that is filling up towards something.
 *
 * <p>The simulation publishes one number - {@code Appearance.charge}, a fraction - and says nothing
 * about how it should look (G0003). This is the whole of the client's answer: a brighter core inside
 * the body, growing as the fraction does. An enemy two mouthfuls into a three-mouthful evolution is
 * therefore visibly two-thirds of the way there, which is what pillar 5 asks for.
 *
 * <p>One mark for every mechanic that fills up, deliberately. A weapon's remaining charge drawn the
 * same way as an evolution in progress is a tell the player learns once.
 *
 * @param scale how much of the body the mark covers at full charge, as a fraction of its radius
 * @param tint how far the mark is shifted toward white, in {@code [0, 1]}, so it reads as a core
 *     rather than as a second unit standing inside the first
 */
public record ChargeMark(float scale, float tint) {

    public ChargeMark {
        if (scale < 0f || scale > 1f) {
            throw new IllegalArgumentException("charge scale must be in [0, 1], was " + scale);
        }
        if (tint < 0f || tint > 1f) {
            throw new IllegalArgumentException("charge tint must be in [0, 1], was " + tint);
        }
    }

    /** The radius of the mark for a body of the given radius at the given charge. */
    public float radiusFor(float bodyRadius, float charge) {
        return bodyRadius * scale * charge;
    }
}
