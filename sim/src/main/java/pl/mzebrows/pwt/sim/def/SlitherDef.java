package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a strain that slithers moves and what it is made of (G0095): the desert snake's.
 *
 * <p>A <strong>slither</strong> never stops of itself - only a declared stun holds it. It goes on along its heading, bending in a wide arc one
 * way or the other for a while, then another, and turns hard only where the way ahead is shut - rock, the edge of the
 * field, or something it has run up against. Too far from where it was put down, it bends back towards it. It passes
 * through every body but scenery, and what its head touches on the way it bites as its claws would.
 * It never hunts as the swarm does - nothing latches.
 *
 * <p>From time to time it <strong>picks a man</strong> - the nearest on the player's side in its own biome within
 * {@code huntRange} - and turns onto him to go through him once, then goes back to bending about until it picks
 * again; nothing slows it if it is {@code unslowed}.
 *
 * <p>It is in <strong>parts</strong>, the head first and the rest trailing behind it, each a little smaller, and its
 * health is shared out between them: every part's worth lost takes the last part off, so the head is the last to go.
 * Only the head is a body; the parts behind it are drawn, never hit (provisional, for review).
 *
 * @param parts how many parts it is, the head among them. Zero never slithers, which is every strain but one; at
 *     most {@link #MOST}
 * @param partScale how big each part behind the head is beside the one in front of it, as a fraction
 * @param spacing how far apart two parts' middles are, as a fraction of the two radii added together
 * @param turnDegrees how fast it bends in the open, in degrees a second - small, so it goes round in wide arcs
 * @param tightTurnDegrees how fast it turns where the way ahead is shut, in degrees a second
 * @param lookAhead how far ahead of its head it looks for the way being shut, in world units
 * @param stuck of how far it was going on a tick, the share its head must cover not to have run up against
 *     something it does not see coming - a stone, a boulder, a trunk - and turn hard. Above zero, at most one
 * @param roam how far from where it was put down it goes before it bends back, in world units
 * @param bendMinSeconds the shortest one bend lasts before it picks another
 * @param bendMaxSeconds the longest
 * @param harm what its head does to a man it passes through
 * @param unslowed whether nothing slows it - no beam, no glue, no mud: a slither keeps its pace
 * @param huntMinSeconds the shortest it goes between picking a man to go through, once it has gone through one or
 *     given up on him. Zero never picks one
 * @param huntMaxSeconds the longest
 * @param huntRange how far off a man may be, in its own biome, for it to pick him, in world units
 * @param huntTurnDegrees how fast it turns onto the man it picked, in degrees a second
 * @param huntSeconds how long it goes for him before it gives up and goes back to bending about
 */
@With
public record SlitherDef(
        int parts,
        float partScale,
        float spacing,
        float turnDegrees,
        float tightTurnDegrees,
        float lookAhead,
        float stuck,
        float roam,
        float bendMinSeconds,
        float bendMaxSeconds,
        Harm harm,
        boolean unslowed,
        float huntMinSeconds,
        float huntMaxSeconds,
        float huntRange,
        float huntTurnDegrees,
        float huntSeconds) {

    /** The most parts a strain may be in - a snapshot carries each part as a field of its own. */
    public static final int MOST = 7;

    public SlitherDef {
        Require.notNegative("slither parts", parts);
        if (parts > MOST) {
            throw new IllegalArgumentException("a strain slithers in at most " + MOST + " parts, was " + parts);
        }
        Require.positive("slither partScale", partScale);
        Require.positive("slither spacing", spacing);
        Require.notNegative("slither turnDegrees", turnDegrees);
        Require.atLeast(
                "slither tightTurnDegrees", tightTurnDegrees, turnDegrees, "it turns no slower where it has no room");
        Require.notNegative("slither lookAhead", lookAhead);
        Require.positive("slither stuck", stuck);
        if (stuck > 1f) {
            throw new IllegalArgumentException(
                    "slither stuck is a share of the way it was going, at most 1, was " + stuck);
        }
        Require.notNegative("slither roam", roam);
        Require.notNegative("slither bendMinSeconds", bendMinSeconds);
        Require.atLeast(
                "slither bendMaxSeconds", bendMaxSeconds, bendMinSeconds, "a bend cannot last longest below shortest");
        Require.notNegative("slither huntMinSeconds", huntMinSeconds);
        Require.atLeast(
                "slither huntMaxSeconds", huntMaxSeconds, huntMinSeconds, "a wait cannot last longest below shortest");
        Require.notNegative("slither huntRange", huntRange);
        Require.notNegative("slither huntTurnDegrees", huntTurnDegrees);
        Require.notNegative("slither huntSeconds", huntSeconds);
    }

    /** Whether it picks a man to go through from time to time. */
    public boolean hunts() {
        return huntMinSeconds > 0f && huntRange > 0f && huntSeconds > 0f;
    }

    /** Never slithers. What every strain but the snake declares. */
    public static SlitherDef off() {
        return new SlitherDef(0, 1f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, Harm.KILL, false, 0f, 0f, 0f, 0f, 0f);
    }

    /** Whether it slithers at all. */
    public boolean enabled() {
        return parts > 0;
    }
}
