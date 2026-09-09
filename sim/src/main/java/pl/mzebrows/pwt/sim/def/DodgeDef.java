package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a creature answers a shot it can see coming.
 *
 * <p><strong>The dodge is deterministic on purpose, and that is a design decision rather than an
 * implementation convenience.</strong> The same shot from the same place gets the same answer, every
 * time: it breaks perpendicular to the shot, to the side it is already on. Nothing here is drawn from
 * the {@code Rng}. A player who watches a rabbit for a minute can learn to lead it, and learning it is
 * the whole point - a random sidestep would make missing feel like bad luck rather than a bad shot.
 *
 * <p>{@code commitSeconds} is what keeps that from making it trivially killable. Once it has committed
 * to a sidestep it cannot answer anything else until the window closes, so the counter is a second
 * shot rather than a perfectly led first one.
 *
 * @param enabled whether this creature dodges at all
 * @param reactionRange how close a shot must be before it is noticed, in world units. What decides
 *     whether a rabbit shot at from across the arena has time to think about it
 * @param speed how fast the sidestep is, in world units per second
 * @param durationSeconds how long the sidestep lasts
 * @param commitSeconds how long after starting one before it can start another. The bounded window
 *     that makes a second shot the answer to a dodge
 * @param margin how much wider than the two bodies a shot's closest approach may be and still count
 *     as coming at it, in world units. Zero would mean it only dodges shots that were going to hit
 *     exactly, which reads as clairvoyance rather than as nerves
 */
@With
public record DodgeDef(
        boolean enabled, float reactionRange, float speed, float durationSeconds, float commitSeconds, float margin) {

    public DodgeDef {
        Require.notNegative("dodge reactionRange", reactionRange);
        Require.notNegative("dodge speed", speed);
        Require.notNegative("dodge durationSeconds", durationSeconds);
        Require.notNegative("dodge commitSeconds", commitSeconds);
        Require.notNegative("dodge margin", margin);
    }

    /** Something that stands there and takes it. */
    public static DodgeDef off() {
        return new DodgeDef(false, 0f, 0f, 0f, 0f, 0f);
    }
}
