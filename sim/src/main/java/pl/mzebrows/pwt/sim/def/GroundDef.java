package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * What standing on a kind of ground costs whatever is standing on it, as numbers.
 *
 * <p>The sim half of the split {@code Terrain} declares: the map says {@code THICKET} and knows
 * nothing else about it, and this says what a thicket is worth. Every terrain has one of these
 * because a table with a hole in it cannot be built, and almost every terrain declares
 * {@link #clear()} - <strong>one means nothing happens</strong>, which is this file's version of the
 * "zero means not for me" {@link RangedDef#off()} and {@link HeatDef#none()} use.
 *
 * <p><strong>The two sides feel it differently, and that is the mechanic rather than an oversight.</strong>
 * A thicket that slowed everybody equally would be scenery: both sides arrive at the fight in the
 * same order they would have on open ground, and the only thing it changes is how long the walk
 * took. Slowing the player's side harder makes it ground he has to decide about - going round costs
 * seconds, going through costs the seconds <em>and</em> the exchange he has to have while wading -
 * and it is the first thing on the field that is worse for him than for them by declaration. That is
 * why it is two numbers rather than one.
 *
 * @param playerFactor how much of its speed something on the player's side keeps here, as a
 *     fraction. One is open ground
 * @param alienFactor the same for everything else - the alien side, and anything on nobody's, since
 *     a rabbit wading is a rabbit the player can still catch
 */
@With
public record GroundDef(float playerFactor, float alienFactor) {

    public GroundDef {
        Require.positive("ground playerFactor", playerFactor);
        Require.positive("ground alienFactor", alienFactor);
        if (playerFactor > 1f || alienFactor > 1f) {
            throw new IllegalArgumentException("ground may slow a unit and may not speed one up");
        }
    }

    /** Ground that does nothing at all. What every terrain but one declares. */
    public static GroundDef clear() {
        return new GroundDef(1f, 1f);
    }

    /** True if crossing this is worth anything to anybody. */
    public boolean matters() {
        return playerFactor < 1f || alienFactor < 1f;
    }

    /** How much of its speed a unit on this side keeps here. */
    public float factorFor(Side side) {
        return side == Side.PLAYER ? playerFactor : alienFactor;
    }
}
