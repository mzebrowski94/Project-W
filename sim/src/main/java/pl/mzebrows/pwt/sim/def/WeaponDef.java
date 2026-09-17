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
 * @param damage what one projectile takes off what it hits
 * @param projectileLook what its shots are drawn as. Pillar 5: a gun the player cannot tell he is
 *     holding is a gun he is guessing about, and the shots are the part of it that is on screen
 * @param blastRadius how wide a blast each shot leaves where it stops, in world units. Zero for every
 *     ordinary gun. A shot that carries one goes off on whatever it hits and at the end of its flight
 *     alike, because it does so <em>by dying</em> through the reaction everything else explodes
 *     through (G0018) - there is no second path to a blast, only a gun that names one
 */
@With
public record WeaponDef(
        float cooldownSeconds,
        float projectileSpeed,
        float projectileRadius,
        float damage,
        Look projectileLook,
        float blastRadius) {

    public WeaponDef {
        Require.notNegative("weapon cooldownSeconds", cooldownSeconds);
        Require.positive("weapon projectileSpeed", projectileSpeed);
        Require.positive("weapon projectileRadius", projectileRadius);
        Require.notNegative("weapon damage", damage);
        Require.notNegative("weapon blastRadius", blastRadius);
    }

    /** True if what it fires goes off where it stops. */
    public boolean explodes() {
        return blastRadius > 0f;
    }
}
