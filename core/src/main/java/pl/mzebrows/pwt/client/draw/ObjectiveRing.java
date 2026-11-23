package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * The <strong>objective ring</strong> (G0111): a ring of faint white stripes with gaps between, turning slowly and its
 * faintness breathing, drawn round every <strong>site</strong> not yet taken - a control point, a mainframe, whatever
 * site a finale or a relic carries - so every player sees at a glance what the level wants taken without it shouting
 * over the fight. Gone once it is taken.
 *
 * <p>Read off the site's own state, which every machine holds alike, so it is the same on every screen; its turn and
 * its pulse run on the client's clock, times {@code graphic.ui.motionScale}, so zero holds it still and steady.
 *
 * @param color the stripes' colour; its alpha is how see-through they are at the top of the pulse
 * @param stripes how many stripes run round it, each followed by a gap as long as itself
 * @param pieces how many straight pieces each stripe is drawn in; more is a rounder ring
 * @param width how thick the ring is, in world units
 * @param margin how far outside the site's edge it runs, in world units
 * @param spinDegrees how far it turns a second, anticlockwise
 * @param pulseHz how many times a second it fades and returns
 * @param pulseDepth how far it fades at the bottom of a pulse, as a share of its alpha; zero never fades
 */
public record ObjectiveRing(
        Color color,
        int stripes,
        int pieces,
        float width,
        float margin,
        float spinDegrees,
        float pulseHz,
        float pulseDepth) {

    public ObjectiveRing {
        if (stripes < 1) {
            throw new IllegalArgumentException("an objective ring has at least one stripe, was " + stripes);
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

    /** Where piece {@code piece} of stripe {@code stripe} starts: its index round the ring, gaps counted. */
    public int pieceAt(int stripe, int piece) {
        return stripe * 2 * pieces + piece;
    }

    /** How many equal pieces the whole ring, stripes and gaps, divides into. */
    public int segments() {
        return stripes * 2 * pieces;
    }

    /**
     * Where piece edge {@code at} - zero to {@link #segments}, round the ring - stands this many seconds in, in degrees:
     * turned by the spin, and still at no motion.
     */
    public float angleAt(int at, float seconds, float motionScale) {
        return at * 360f / segments() + spinDegrees * seconds * motionScale;
    }

    /**
     * The stripes' alpha this many seconds in: the colour's own at the top of the pulse, less the depth's share of it at
     * the bottom; steady at the colour's own with no motion.
     */
    public float alphaAt(float seconds, float motionScale) {
        if (motionScale <= 0f || pulseDepth <= 0f || pulseHz <= 0f) {
            return color.a;
        }
        double wave = 0.5 + 0.5 * Math.cos(2.0 * Math.PI * pulseHz * seconds);
        return color.a * (1f - pulseDepth * motionScale * (float) (1.0 - wave));
    }
}
