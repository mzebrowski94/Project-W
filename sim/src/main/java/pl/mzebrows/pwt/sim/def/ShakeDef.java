package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How hard something shakes the ground where it happens - a gun's trigger pulled, a leap landing on a door - as
 * numbers: the simulation's half of a screen shake ({@code Shake}). What the view does about it is the client's.
 *
 * @param strength how hard the ground moves at the shooter, in world units; zero shakes nothing, which is
 *     what every gun but the bazooka declares
 * @param radius how far from him it is felt at all, in world units
 * @param seconds how long it lasts
 */
@With
public record ShakeDef(float strength, float radius, float seconds) {

    public ShakeDef {
        Require.notNegative("shake strength", strength);
        Require.notNegative("shake radius", radius);
        Require.notNegative("shake seconds", seconds);
    }

    /** A gun that shakes nothing. */
    public static ShakeDef off() {
        return new ShakeDef(0f, 0f, 0f);
    }

    /** True if firing it moves the ground at all. */
    public boolean shakes() {
        return strength > 0f && radius > 0f && seconds > 0f;
    }
}
