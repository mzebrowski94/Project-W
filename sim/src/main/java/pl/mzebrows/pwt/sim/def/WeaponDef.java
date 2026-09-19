package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * One gun, as numbers.
 *
 * <p>Guns are a table for the same reason strains are: picking one up is reading a different
 * definition into the components the commander already carries, and adding one is a definition
 * rather than any code. There is no branch anywhere that asks which gun is being fired.
 *
 * @param cooldownSeconds minimum time between shots
 * @param projectileSpeed world units per second
 * @param projectileRadius circular body of a fired projectile
 * @param projectileRange how far one of its shots flies before it expires, in world units. Zero is
 *     "until it hits something or leaves the level", which is what every gun with a useful reach
 *     declares. <strong>This is a property of the shot, not of the aim</strong> - the enforced
 *     invariant that aim is a direction and never a distance is about where a trigger pull is
 *     pointed, and a grenade has had a flight length since G0019 without troubling it. It is what
 *     lets a gun be short-ranged: the flame goes out
 * @param damage what one projectile takes off what it hits
 * @param pellets how many shots one pull of the trigger sends. One for every gun that fires a single
 *     round. More than one makes a gun that covers ground rather than a point, and it is a number
 *     here rather than a second firing path because every shooter in the game goes through one place
 * @param spreadDegrees the full width of the cone this gun throws its shots into, centred on where
 *     it was aimed. Zero fires dead straight, which is what a gun that fires one round declares.
 *     <strong>The gun's own, and separate from the shooter's</strong>: a spitter's cone is how badly
 *     the creature aims and narrows as it settles, and this is how loosely the weapon throws, which
 *     nothing improves. The two compose - the shooter turns the aim once and the gun scatters each
 *     pellet around it
 * @param projectileLook what its shots are drawn as. Pillar 5: a gun the player cannot tell he is
 *     holding is a gun he is guessing about, and the shots are the part of it that is on screen
 * @param blastRadius how wide a blast each shot leaves where it stops, in world units. Zero for every
 *     ordinary gun. A shot that carries one goes off on whatever it hits and at the end of its flight
 *     alike, because it does so <em>by dying</em> through the reaction everything else explodes
 *     through (G0018) - there is no second path to a blast, only a gun that names one
 * @param heat how hot it gets and how fast that comes off. {@link HeatDef#none()} for a gun that can
 *     be fired as fast as its cooldown allows, for ever, which is most of them
 * @param muzzleOffset how far to either side of the line of fire its muzzles sit, in world units.
 *     Zero for a gun with one muzzle, which is every gun a man carries. A gun that names one fires
 *     from alternate sides, so a twin mount is a number here rather than a second firing path
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
        HeatDef heat,
        float muzzleOffset) {

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
    }

    /** True if firing this gun faster than it likes costs anything. */
    public boolean overheats() {
        return heat.matters();
    }

    /** True if what it fires goes off where it stops. */
    public boolean explodes() {
        return blastRadius > 0f;
    }

    /** True if one pull of the trigger covers ground rather than a point. */
    public boolean scatters() {
        return pellets > 1 || spreadDegrees > 0f;
    }

    /**
     * Half the cone, in radians, which is what a firing system actually wants: a pellet is nudged by
     * somewhere in {@code [-this, +this]}.
     *
     * <p>Converted once, when the gun is read into a {@code Weapon}, for the reason
     * {@code RangedDef.halfSpreadRadians()} converts once at spawn: a transcendental in a firing loop
     * is one nobody should be paying for per pellet.
     */
    public float halfSpreadRadians() {
        return (float) StrictMath.toRadians(spreadDegrees * 0.5);
    }
}
