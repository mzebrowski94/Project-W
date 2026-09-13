package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a strain uses a gun: how close it gets before it fires, how loosely it shoots, and whether it
 * plants itself to do it.
 *
 * <p><strong>Which gun it carries is here; what the gun does is not.</strong> Damage, rate of fire
 * and how a shot is drawn all come from the {@link WeaponDef} named by {@link #gun}, through exactly
 * the table the commander's own guns come from - so an alien that shoots and a trooper that shoots
 * are firing the same machinery, and a new alien gun is a definition rather than any code.
 *
 * <p><strong>{@link #range} deliberately belongs to the unit and not to the gun.</strong> An enforced
 * invariant says aim is a direction and never a distance, so a gun with a reach would be a number
 * somebody could later hand to the commander and quietly cap him with. This is not that: it is how
 * close a creature chooses to be before it opens up, which is a fact about the creature.
 *
 * <p>{@link #spreadDegrees} sits here for now for the same reason and a weaker one - nothing else in
 * the game shoots crookedly. The day a scattergun exists it belongs on the gun instead, and moving
 * it is a field and two call sites.
 *
 * @param enabled whether this strain shoots at all. Off for everything that closes and claws
 * @param range how close the thing it is hunting must be before it fires, in world units. Short
 *     against its detection range on purpose: it notices you long before it can reach you, so the
 *     ground between the two is ground you cross while it watches
 * @param spreadDegrees the full width of the cone a shot may leave in, centred on the aim. Zero
 *     fires dead straight. It is what makes a shooter a threat to the men <em>beside</em> the one it
 *     is aiming at, which is most of what makes a firing line feel like one
 * @param haltsToFire whether it plants itself while its target is in range rather than closing.
 *     True makes a creature that holds ground and shoots - a different problem from anything that
 *     rushes you, and one the player has to go and solve
 * @param gun which entry in the weapon table it fires
 */
@With
public record RangedDef(boolean enabled, float range, float spreadDegrees, boolean haltsToFire, WeaponKind gun) {

    /** The widest a cone may be before "roughly at you" stops meaning anything. */
    private static final float WIDEST_SPREAD = 180f;

    public RangedDef {
        Require.notNegative("ranged range", range);
        Require.notNegative("ranged spreadDegrees", spreadDegrees);
        if (spreadDegrees > WIDEST_SPREAD) {
            throw new IllegalArgumentException("ranged spreadDegrees must be at most 180, was " + spreadDegrees);
        }
        if (enabled && range <= 0f) {
            throw new IllegalArgumentException("a strain that shoots needs a range to shoot at");
        }
    }

    /** A strain that does not shoot. What every strain that closes and claws declares. */
    public static RangedDef off() {
        return new RangedDef(false, 0f, 0f, false, WeaponKind.SIDEARM);
    }

    /**
     * Half the cone, in radians, which is what a firing system actually wants: a shot is nudged by
     * somewhere in {@code [-this, +this]}.
     *
     * <p>Converted once, at spawn, exactly as {@code PerceptionDef.arcCosine()} is - a transcendental
     * in a per-entity loop is banned here, and this way there is never one.
     */
    public float halfSpreadRadians() {
        return (float) StrictMath.toRadians(spreadDegrees * 0.5);
    }
}
