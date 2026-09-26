package pl.mzebrows.pwt.sim.def;

import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * How a strain uses a gun: what it can see, how close it lets you get, how loosely it shoots, and
 * what happens if it eats something explosive.
 *
 * <p><strong>Which gun it carries is here; what the gun does is not</strong> - that is the
 * {@link WeaponDef} named by {@link #gun}. <strong>{@link #range} belongs to the unit</strong>: how
 * close a creature chooses to be, not a reach anyone could hand the commander.
 *
 * <p><strong>A shooter moves for somewhere to be, never for somebody to follow</strong>
 * ({@link #holdsGround}): it answers a provocation (a place, G0016), walks once to the distance it
 * shoots best from, and holds that ground.
 *
 * @param enabled whether this strain shoots at all. Off for everything that closes and claws
 * @param range how close the thing it is hunting must be before it fires, in world units
 * @param spreadDegrees the full width of the cone its <em>first</em> shot may leave in, centred on
 *     the aim - how badly the creature aims. Zero fires dead straight
 * @param haltsToFire whether it plants itself while its target is in range rather than closing
 * @param gun which entry in the weapon table it fires
 * @param holdsGround whether it takes a position and keeps it rather than following what it hunts
 * @param arcDegrees how wide the field of view it will fire into is, centred on where it is looking.
 *     {@link PerceptionDef#ALL_ROUND} sees all round
 * @param turnRate how fast its aim swings onto what it is hunting, as a fraction of the angle still
 *     to cover per second. Zero means it is always already looking at you
 * @param closeRange the distance it is best at, and walks to, in world units; comfortably inside
 *     {@code range}. Zero switches both halves off
 * @param closeFactor what being close multiplies both its cooldown and its cone by, in {@code (0, 1]}
 * @param settleShots how many shots it takes to walk its aim in. Zero means every shot is as loose as
 *     the first
 * @param settledSpreadFactor what the cone has narrowed to by then, as a fraction of
 *     {@code spreadDegrees}, in {@code (0, 1]}
 * @param chargedGun what it carries instead after eating something explosive, or null (G0022)
 */
@With
public record RangedDef(
        boolean enabled,
        float range,
        float spreadDegrees,
        boolean haltsToFire,
        WeaponKind gun,
        boolean holdsGround,
        float arcDegrees,
        float turnRate,
        float closeRange,
        float closeFactor,
        int settleShots,
        float settledSpreadFactor,
        @Nullable WeaponKind chargedGun) {

    /** The widest a cone may be before "roughly at you" stops meaning anything. */
    private static final float WIDEST_SPREAD = 180f;

    public RangedDef {
        Require.notNegative("ranged range", range);
        Require.notNegative("ranged spreadDegrees", spreadDegrees);
        Require.notNegative("ranged turnRate", turnRate);
        Require.notNegative("ranged closeRange", closeRange);
        Require.notNegative("ranged settleShots", settleShots);
        if (spreadDegrees > WIDEST_SPREAD) {
            throw new IllegalArgumentException("ranged spreadDegrees must be at most 180, was " + spreadDegrees);
        }
        if (enabled && range <= 0f) {
            throw new IllegalArgumentException("a strain that shoots needs a range to shoot at");
        }
        if (arcDegrees <= 0f || arcDegrees > PerceptionDef.ALL_ROUND) {
            throw new IllegalArgumentException("ranged arcDegrees must be in (0, 360], was " + arcDegrees);
        }
        if (closeFactor <= 0f || closeFactor > 1f) {
            throw new IllegalArgumentException("ranged closeFactor must be in (0, 1], was " + closeFactor);
        }
        if (settledSpreadFactor <= 0f || settledSpreadFactor > 1f) {
            throw new IllegalArgumentException(
                    "ranged settledSpreadFactor must be in (0, 1], was " + settledSpreadFactor);
        }
    }

    /** A strain that does not shoot. What every strain that closes and claws declares. */
    public static RangedDef off() {
        return new RangedDef(
                false, 0f, 0f, false, WeaponKind.SIDEARM, false, PerceptionDef.ALL_ROUND, 0f, 0f, 1f, 0, 1f, null);
    }

    /**
     * A shooter with eyes all round, no settling and nothing to gain from a barrel. For fixtures.
     */
    public static RangedDef plain(float range, float spreadDegrees, boolean haltsToFire, WeaponKind gun) {
        return new RangedDef(
                true, range, spreadDegrees, haltsToFire, gun, false, PerceptionDef.ALL_ROUND, 0f, 0f, 1f, 0, 1f, null);
    }

    /**
     * Half the cone, in radians, which is what a firing system actually wants: a shot is nudged by
     * somewhere in {@code [-this, +this]}.
     *
     * <p>Converted once, at spawn.
     */
    public float halfSpreadRadians() {
        return (float) StrictMath.toRadians(spreadDegrees * 0.5);
    }

    /**
     * The smallest dot product between where it is looking and the direction to something for that
     * thing to be inside its field of view.
     *
     * <p>Computed once at spawn, as {@code PerceptionDef.arcCosine()}.
     */
    public float arcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(arcDegrees * 0.5));
    }
}
