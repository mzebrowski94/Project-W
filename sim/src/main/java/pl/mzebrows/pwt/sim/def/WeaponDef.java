package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * One gun, as numbers.
 *
 * <p>A table like strains: picking one up reads a different definition into the same components,
 * and no branch anywhere asks which gun is being fired. "Zero means not for me" throughout.
 *
 * @param cooldownSeconds minimum time between shots
 * @param projectileSpeed world units per second
 * @param projectileRadius circular body of a fired projectile
 * @param projectileRange how far one of its shots flies before it expires, in world units; zero is
 *     until it hits something or leaves the level. A property of the shot, not of the aim
 * @param damage what one projectile takes off what it hits
 * @param pellets how many shots one pull of the trigger sends
 * @param spreadDegrees the full width of the cone this gun throws its shots into, centred on the aim.
 *     The gun's own; it composes with a shooter's {@link RangedDef#spreadDegrees}
 * @param projectileLook what its shots are drawn as (pillar 5)
 * @param slicks what each shot leaves on the ground where it bursts (G0018); {@link SlickDef#off()}
 *     for none
 * @param blastRadius how wide a blast each shot leaves where it stops, in world units - by dying
 *     (G0018), on a hit or at the end of its flight
 * @param heat how hot it gets and how fast that comes off; {@link HeatDef#none()} for never
 * @param muzzleOffset how far to either side of the line of fire its muzzles sit, in world units;
 *     non-zero fires from alternate sides
 * @param pierceIntervalSeconds how long one of its shots waits between the blows it lands. Zero stops
 *     at the first thing it touches; above zero it passes through anything alive, and scenery stops it
 *     (G0019)
 * @param slowFactor how much of its speed a unit this gun hits keeps, as a fraction; one slows
 *     nothing. It scales the unit's own intent and never turns it
 * @param slowSeconds how long that lasts after the last hit; refreshed, not added
 * @param selfSlowFactor how much of his own speed the shooter keeps while firing this; one costs
 *     nothing
 * @param selfSlowSeconds how long that lasts after the last shot
 * @param trailLength how far back along its path one of its shots is drawn, in world units; zero is a
 *     dot. A drawing number, deliberately not in {@code assets/sim/*.properties}; the simulation never reads it
 * @param bloatPerShot how much of a body's capacity one of its shots fills, as a fraction. Builds on
 *     the living too, but only something already down bursts
 */
@With
public record WeaponDef(
        float cooldownSeconds,
        float projectileSpeed,
        float projectileRadius,
        float projectileRange,
        float damage,
        int pellets,
        float spreadDegrees,
        Look projectileLook,
        float blastRadius,
        SlickDef slicks,
        HeatDef heat,
        float muzzleOffset,
        float pierceIntervalSeconds,
        float slowFactor,
        float slowSeconds,
        float selfSlowFactor,
        float selfSlowSeconds,
        float trailLength,
        float bloatPerShot) {

    /** The widest a cone may be before "roughly where it was pointed" stops meaning anything. */
    private static final float WIDEST_SPREAD = 180f;

    public WeaponDef {
        Require.notNegative("weapon cooldownSeconds", cooldownSeconds);
        Require.positive("weapon projectileSpeed", projectileSpeed);
        Require.positive("weapon projectileRadius", projectileRadius);
        Require.notNegative("weapon projectileRange", projectileRange);
        Require.notNegative("weapon damage", damage);
        Require.atLeastOne("weapon pellets", pellets);
        Require.notNegative("weapon spreadDegrees", spreadDegrees);
        if (spreadDegrees > WIDEST_SPREAD) {
            throw new IllegalArgumentException("weapon spreadDegrees must be at most 180, was " + spreadDegrees);
        }
        Require.notNegative("weapon blastRadius", blastRadius);
        Require.notNegative("weapon muzzleOffset", muzzleOffset);
        Require.notNegative("weapon pierceIntervalSeconds", pierceIntervalSeconds);
        Require.positive("weapon slowFactor", slowFactor);
        Require.notNegative("weapon slowSeconds", slowSeconds);
        Require.positive("weapon selfSlowFactor", selfSlowFactor);
        Require.notNegative("weapon selfSlowSeconds", selfSlowSeconds);
        Require.notNegative("weapon trailLength", trailLength);
        Require.notNegative("weapon bloatPerShot", bloatPerShot);
        if (selfSlowFactor > 1f) {
            throw new IllegalArgumentException("a gun may slow its own shooter and may not speed him up");
        }
        if (slowFactor > 1f) {
            throw new IllegalArgumentException("a gun may slow what it hits and may not speed it up");
        }
    }

    /** True if what this gun hits keeps less of its speed for a while afterwards. */
    public boolean slows() {
        return slowFactor < 1f && slowSeconds > 0f;
    }

    /** True if holding this down costs the shooter some of his own pace. */
    public boolean slowsItsShooter() {
        return selfSlowFactor < 1f && selfSlowSeconds > 0f;
    }

    /** True if its shots are drawn as a line rather than as a dot. */
    public boolean streaks() {
        return trailLength > 0f;
    }

    /** True if sustained fire from this pumps a body up. */
    public boolean bloats() {
        return bloatPerShot > 0f;
    }

    /** True if firing this gun faster than it likes costs anything. */
    public boolean overheats() {
        return heat.matters();
    }

    /** True if its shots pass through anything alive rather than stopping at the first of it. */
    public boolean pierces() {
        return pierceIntervalSeconds > 0f;
    }

    /** True if what it fires goes off where it stops. */
    public boolean explodes() {
        return blastRadius > 0f;
    }

    /** True if what it fires ruins the ground where it stops. */
    public boolean spatters() {
        return slicks.leavesAnything();
    }

    /** True if one pull of the trigger covers ground rather than a point. */
    public boolean scatters() {
        return pellets > 1 || spreadDegrees > 0f;
    }

    /**
     * Half the cone, in radians, which is what a firing system actually wants: a pellet is nudged by
     * somewhere in {@code [-this, +this]}.
     *
     * <p>Converted once, when the gun is read into a {@code Weapon}.
     */
    public float halfSpreadRadians() {
        return (float) StrictMath.toRadians(spreadDegrees * 0.5);
    }
}
