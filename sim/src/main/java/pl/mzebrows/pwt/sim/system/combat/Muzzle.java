package pl.mzebrows.pwt.sim.system.combat;

import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * Where a gun's next shot comes out, in one place, for everything that fires one.
 *
 * <p>Clear of the firer's own body, so nothing shoots itself in the foot, and - for a gun that names
 * an offset - out of alternate muzzles, so a twin mount is a number in a {@code WeaponDef} rather
 * than a second firing path. Every gun goes through this, which is what stops the offset being a
 * property only the thing it was written for honours.
 *
 * <p>The gap is the caller's, not the gun's: how much clearance a shot needs is a fact about the
 * thing holding it rather than about the gun, and the two that existed before this did were already
 * different from each other.
 */
final class Muzzle {

    private Muzzle() {}

    /**
     * The point the next shot leaves from, written into {@code out}, and the side flipped ready for
     * the shot after it. The aim must already be a unit vector.
     */
    static void next(
            float x, float y, float bodyRadius, Weapon weapon, float gap, float aimX, float aimY, float[] out) {
        boolean right = weapon.nextMuzzleRight;
        weapon.nextMuzzleRight = !right;
        float forward = bodyRadius + weapon.projectileRadius + gap;
        float lateral = right ? -weapon.muzzleOffset : weapon.muzzleOffset;
        out[0] = x + aimX * forward - aimY * lateral;
        out[1] = y + aimY * forward + aimX * lateral;
    }
}
