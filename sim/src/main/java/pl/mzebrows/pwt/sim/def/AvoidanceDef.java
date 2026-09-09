package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How far ahead a unit looks for things in its way, and how hard it leans off them.
 *
 * <p><strong>Proactive, not reactive.</strong> The point is that a unit changes course before it
 * touches anything: the constraint solver already handles two bodies that have collided, and a game
 * whose units only ever discover obstacles by walking into them reads as a game whose units cannot
 * see. Every number here exists to make the turn happen early.
 *
 * @param enabled whether this strain steers around things at all
 * @param scanRange how close a hazard has to be, surface to surface, before it is worth avoiding, in
 *     world units
 * @param lookaheadSeconds how far forward both parties are projected before the gap is measured. This
 *     is the whole of what makes the avoidance predictive: a hazard that is beside you but moving into
 *     your path is a hazard, and one directly ahead but moving away is not
 * @param weight how heavily danger counts against a direction compared with how much the unit wanted
 *     to go that way. Below one and it walks into things politely; far above one and it flinches away
 *     from anything on the field and never arrives
 */
@With
public record AvoidanceDef(boolean enabled, float scanRange, float lookaheadSeconds, float weight) {

    public AvoidanceDef {
        Require.notNegative("avoidance scanRange", scanRange);
        Require.notNegative("avoidance lookaheadSeconds", lookaheadSeconds);
        Require.notNegative("avoidance weight", weight);
    }

    /** A strain that discovers what is in its way by walking into it. */
    public static AvoidanceDef off() {
        return new AvoidanceDef(false, 0f, 0f, 0f);
    }
}
