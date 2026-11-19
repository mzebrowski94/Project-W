package pl.mzebrows.pwt.client.draw;

/**
 * How to draw a limpet (G0100): a small rectangle turning as it flies, still once it has stuck; while it calibrates,
 * the door's loading circle on it, smaller (G0069); calibrated, a red dot ticking on and off on the client's clock -
 * its big blast ready, read at a glance and never counted (pillar 5).
 *
 * @param length how long the rectangle is, as a multiple of the limpet's radius
 * @param breadth how broad, as a multiple of its radius
 * @param spinDegreesPerUnit how far it turns for every world unit it flies
 * @param ringPitch the size of the "tile" the door's loading circle is drawn round, as a multiple of its radius
 * @param dot the red dot's radius, as a multiple of its radius
 * @param tickHz how many times a second the dot ticks on and off
 */
public record LimpetMark(
        float length, float breadth, float spinDegreesPerUnit, float ringPitch, float dot, float tickHz) {

    public LimpetMark {
        if (length <= 0f || breadth <= 0f) {
            throw new IllegalArgumentException(
                    "a limpet mark must have a length and a breadth, was " + length + " by " + breadth);
        }
        if (ringPitch <= 0f || dot <= 0f) {
            throw new IllegalArgumentException("a limpet mark's ring and dot must be positive");
        }
        if (!(tickHz > 0f && tickHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException(
                    "a limpet may tick at most " + GraphicConfig.MAX_PULSE_HZ + " times a second, not " + tickHz);
        }
    }

    /** How far it has turned, in degrees, having flown this far. */
    public float angleFor(float flown) {
        return flown * spinDegreesPerUnit;
    }

    /** Whether the dot is lit this many seconds into the client's clock: on for the first half of every tick. */
    public boolean litAt(float seconds) {
        float turns = seconds * tickHz;
        return turns - (float) Math.floor(turns) < 0.5f;
    }
}
