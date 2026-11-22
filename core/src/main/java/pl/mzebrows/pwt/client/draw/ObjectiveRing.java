package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * The <strong>objective ring</strong> (G0111): a striped ring, white and yellow, turning slowly and shining, drawn round
 * every <strong>site</strong> not yet taken - a control point, a mainframe, whatever site a finale or a relic carries -
 * so every player sees at a glance what the level wants taken. Gone once it is taken.
 *
 * <p>Read off the site's own state, which every machine holds alike, so it is the same on every screen; its turn and
 * its shine run on the client's clock, times {@code graphic.ui.motionScale}, so zero holds it still and steady.
 *
 * @param first the colour of every other stripe, from the first
 * @param second the colour of the stripes between them
 * @param stripes how many stripes run round it, both colours together; even, so they alternate all the way round
 * @param pieces how many straight pieces each stripe is drawn in; more is a rounder ring
 * @param width how thick the ring is, in world units
 * @param margin how far outside the site's edge it runs, in world units
 * @param spinDegrees how far it turns a second, anticlockwise
 * @param pulseHz how many times a second it brightens and dims
 * @param pulseDepth how far it dims at the bottom of a pulse, as a share of its brightness; zero never dims
 */
public record ObjectiveRing(
        Color first,
        Color second,
        int stripes,
        int pieces,
        float width,
        float margin,
        float spinDegrees,
        float pulseHz,
        float pulseDepth) {

    public ObjectiveRing {
        if (stripes < 2 || stripes % 2 != 0) {
            throw new IllegalArgumentException(
                    "an objective ring's stripes alternate, so an even count, was " + stripes);
        }
        if (pieces < 1) {
            throw new IllegalArgumentException("a stripe is at least one piece, was " + pieces);
        }
        if (width <= 0f || margin < 0f) {
            throw new IllegalArgumentException("an objective ring's width is positive and its margin not negative");
        }
        if (!(pulseHz >= 0f && pulseHz <= GraphicConfig.MAX_PULSE_HZ)) {
            throw new IllegalArgumentException("an objective ring may pulse at most " + GraphicConfig.MAX_PULSE_HZ
                    + " times a second, not " + pulseHz);
        }
        if (pulseDepth < 0f || pulseDepth > 1f) {
            throw new IllegalArgumentException("an objective ring's pulse depth is a share, was " + pulseDepth);
        }
    }

    /** Where the ring runs round a site of this radius: its middle line, just outside the edge. */
    public float ringFor(float radius) {
        return radius + margin + width * 0.5f;
    }

    /** The colour of stripe {@code stripe}, white and yellow by turns. */
    public Color colorOf(int stripe) {
        return stripe % 2 == 0 ? first : second;
    }

    /** How many straight pieces the whole ring is drawn in. */
    public int segments() {
        return stripes * pieces;
    }

    /**
     * Where piece edge {@code at} - zero to {@link #segments}, round the ring - stands this many seconds in, in degrees:
     * turned by the spin, and still at no motion.
     */
    public float angleAt(int at, float seconds, float motionScale) {
        return at * 360f / segments() + spinDegrees * seconds * motionScale;
    }

    /**
     * How bright the ring is this many seconds in, as a share of its colours: one at the top of the pulse, one less the
     * depth at its bottom; steady at one with no motion.
     */
    public float brightnessAt(float seconds, float motionScale) {
        if (motionScale <= 0f || pulseDepth <= 0f || pulseHz <= 0f) {
            return 1f;
        }
        double wave = 0.5 + 0.5 * Math.cos(2.0 * Math.PI * pulseHz * seconds);
        return 1f - pulseDepth * motionScale * (float) (1.0 - wave);
    }
}
