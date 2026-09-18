package pl.mzebrows.pwt.sim.system;

/**
 * Bringing a gun round onto a bearing, in one place, for everything that cannot simply point.
 *
 * <p>Two things swing rather than snap: a creature with a field of view, and a gun on a mount. They
 * must do it the same way, and the hard part of doing it is not the rate - it is the case where what
 * you want to shoot is exactly behind you, which a naive lerp never turns away from at all.
 *
 * <p><strong>An exponential approach rather than a fixed number of degrees per second.</strong> It
 * turns quickly when it is badly out and settles rather than overshooting, and - the part that
 * matters here - it is a lerp between two unit vectors, so it needs no trigonometry at all.
 * Rotating by an angle would, and {@code Math.sin} and {@code Math.cos} are banned in this module
 * because they are specified only to within an ulp and may differ between JVMs (pillar 6).
 */
final class Aim {

    private static final float EPSILON = 1e-4f;

    /** Close enough to dead behind that there is no shorter way round to lerp along. */
    private static final float ANTIPARALLEL = -0.9999f;

    private Aim() {}

    /**
     * Where a gun pointing one way is pointing after a tick of coming round onto another, written
     * into {@code out} as a unit vector. Both directions must already be unit vectors.
     *
     * <p>A turn rate of zero means it is already looking wherever it was asked to, which is what
     * everything that shot before there were fields of view declares.
     */
    static void swing(
            float fromX, float fromY, float ontoX, float ontoY, float turnRate, float deltaTime, float[] out) {
        float blend = turnRate * deltaTime;
        if (turnRate <= 0f || blend >= 1f) {
            out[0] = ontoX;
            out[1] = ontoY;
            return;
        }
        float swungX;
        float swungY;
        if (fromX * ontoX + fromY * ontoY <= ANTIPARALLEL) {
            // Dead behind it. The two directions cancel, so lerping between them only shortens the
            // aim along its own line and renormalising puts it straight back - a gun with somebody
            // exactly at its six would turn for ever. Nudge off the line by the same amount it would
            // have turned, and from the next tick there is a shorter way round to take.
            swungX = fromX - ontoY * blend;
            swungY = fromY + ontoX * blend;
        } else {
            swungX = fromX + (ontoX - fromX) * blend;
            swungY = fromY + (ontoY - fromY) * blend;
        }
        float length = (float) Math.sqrt(swungX * swungX + swungY * swungY);
        if (length <= EPSILON) {
            out[0] = ontoX;
            out[1] = ontoY;
            return;
        }
        out[0] = swungX / length;
        out[1] = swungY / length;
    }
}
