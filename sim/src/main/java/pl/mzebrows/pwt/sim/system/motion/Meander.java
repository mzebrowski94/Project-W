package pl.mzebrows.pwt.sim.system.motion;

import pl.mzebrows.pwt.sim.Rng;

/**
 * The bending about a creature does that decides its own course - a snake's slither (G0095), a worm's burrow with
 * nobody to come for (G0096): a bend one way, the other or straight on for a drawn while, a heading turned a tick's
 * worth at a time, and back towards home past its roam. One copy, so the two go about the same way.
 *
 * <p>Primitives in, primitives out: nothing allocated, no component of either kind known here.
 */
final class Meander {

    private Meander() {}

    /** Which side home lies on: above zero to the left of the heading, below to the right. */
    static float towardsHome(float atX, float atY, float homeX, float homeY, float headingX, float headingY) {
        float toX = homeX - atX;
        float toY = homeY - atY;
        return headingX * toY - headingY * toX;
    }

    /** Whether it is further from home than its roam. */
    static boolean pastRoam(float atX, float atY, float homeX, float homeY, float roam) {
        float awayX = atX - homeX;
        float awayY = atY - homeY;
        return awayX * awayX + awayY * awayY > roam * roam;
    }

    /** The next bend: -1 to the right, 0 straight on, 1 to the left, evenly. */
    static int drawBend(Rng rng) {
        return rng.nextInt(3) - 1;
    }

    /** A while drawn evenly between the shortest and the longest, both included, in ticks. */
    static int drawSpan(Rng rng, int shortest, int longest) {
        return shortest + rng.nextInt(longest - shortest + 1);
    }

    /** The heading's x turned a tick's worth towards {@code side}: 1 left, -1 right. Not normalised. */
    static float turnedX(float headingX, float headingY, int side, float cos, float sin) {
        return headingX * cos - headingY * side * sin;
    }

    /** Its y, the same way. */
    static float turnedY(float headingX, float headingY, int side, float cos, float sin) {
        return headingX * side * sin + headingY * cos;
    }

    static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }
}
