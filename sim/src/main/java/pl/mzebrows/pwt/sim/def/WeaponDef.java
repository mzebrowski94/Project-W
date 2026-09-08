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
 */
@With
public record WeaponDef(
        float cooldownSeconds, float projectileSpeed, float projectileRadius, float damage, Look projectileLook) {

    public WeaponDef {
        Require.notNegative("weapon cooldownSeconds", cooldownSeconds);
        Require.positive("weapon projectileSpeed", projectileSpeed);
        Require.positive("weapon projectileRadius", projectileRadius);
        Require.notNegative("weapon damage", damage);
    }
}
