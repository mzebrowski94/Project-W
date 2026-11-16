package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a strain that burrows moves and attacks (G0096): the desert worm's.
 *
 * <p>Under the ground it is a <strong>shadow</strong> nothing can hurt, always on the move: at the strain's own speed
 * after the nearest man in its biome, turning onto him at {@code huntTurnDegrees}, beneath bodies, scenery, rock and
 * shut doors; with nobody there it bends about its home as a snake does, at {@code idleSpeed}. Within {@code
 * surfaceReach} of him, off its rest, on open ground with room for its body, it <strong>surfaces</strong> where he stood:
 * its maw opens at {@code mawScale} times its body and closes over {@code tellSeconds}, the <strong>snap</strong>
 * landing its {@code harm} on every man still inside; it stands in its hole {@code standSeconds}, dives, and rests a
 * time drawn between {@code restMinSeconds} and {@code restMaxSeconds} before it may surface again.
 *
 * @param teeth how many teeth its maw has when whole - its health shared out between them, rounded up, the last tooth
 *     going with the last point. Zero never burrows, which is every strain but one
 * @param idleSpeed how fast it goes with nobody in its biome to come for, in world units a second
 * @param huntTurnDegrees how fast it turns onto the man it comes for, in degrees a second
 * @param turnDegrees how fast it bends with nobody to come for, in degrees a second - small, as a snake's
 * @param roam how far from where it was put down it bends about before it bends back, in world units
 * @param bendMinSeconds the shortest one bend lasts before it picks another
 * @param bendMaxSeconds the longest
 * @param surfaceReach how near the man it comes for its middle must be for it to surface under him, in world units
 * @param tellSeconds how long its maw takes to close, from coming up to the snap: the dodge's window
 * @param mawScale how wide the maw opens, as a multiple of its body: what it shrinks from
 * @param standSeconds how long it stands in its hole after the snap before it dives
 * @param restMinSeconds the shortest it rests after it dives before it may surface again
 * @param restMaxSeconds the longest
 * @param harm what the snap does to a man inside the maw
 */
@With
public record BurrowDef(
        int teeth,
        float idleSpeed,
        float huntTurnDegrees,
        float turnDegrees,
        float roam,
        float bendMinSeconds,
        float bendMaxSeconds,
        float surfaceReach,
        float tellSeconds,
        float mawScale,
        float standSeconds,
        float restMinSeconds,
        float restMaxSeconds,
        Harm harm) {

    public BurrowDef {
        Require.notNegative("burrow teeth", teeth);
        Require.notNegative("burrow idleSpeed", idleSpeed);
        Require.notNegative("burrow huntTurnDegrees", huntTurnDegrees);
        Require.notNegative("burrow turnDegrees", turnDegrees);
        Require.notNegative("burrow roam", roam);
        Require.notNegative("burrow bendMinSeconds", bendMinSeconds);
        Require.atLeast(
                "burrow bendMaxSeconds", bendMaxSeconds, bendMinSeconds, "a bend cannot last longest below shortest");
        Require.notNegative("burrow surfaceReach", surfaceReach);
        Require.notNegative("burrow tellSeconds", tellSeconds);
        Require.atLeast("burrow mawScale", mawScale, 1f, "the maw closes onto its body, never opens inside it");
        Require.notNegative("burrow standSeconds", standSeconds);
        Require.notNegative("burrow restMinSeconds", restMinSeconds);
        Require.atLeast(
                "burrow restMaxSeconds", restMaxSeconds, restMinSeconds, "a rest cannot last longest below shortest");
    }

    /** Never burrows. What every strain but the worm declares. */
    public static BurrowDef off() {
        return new BurrowDef(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, Harm.WOUND);
    }

    /** Whether it burrows at all. */
    public boolean enabled() {
        return teeth > 0;
    }
}
