package pl.mzebrows.pwt.sim.def;

import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * How a strain uses a gun: what it can see, how close it lets you get, how loosely it shoots, and
 * what happens if it eats something explosive.
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
 * <p><strong>A shooter moves for somewhere to be, never for somebody to follow</strong>
 * ({@link #holdsGround}). It comes when it is summoned - a provocation is a place rather than a
 * target (G0016), so a point being taken off it brings it over, which is the only initiative a level
 * has against something that will not come on its own. It crosses the gap between noticing you and
 * being able to fight you, once, walking to the distance it shoots best from. And from the moment it
 * has that ground it holds it, however far you then go: the problem it sets the player is ground to
 * cross rather than a thing to outrun. Everything else
 * here is what makes standing in front of one get worse the longer you do it.
 *
 * @param enabled whether this strain shoots at all. Off for everything that closes and claws
 * @param range how close the thing it is hunting must be before it fires, in world units. Short
 *     against its detection range on purpose: it notices you long before it can reach you, so the
 *     ground between the two is ground you cross while it watches
 * @param spreadDegrees the full width of the cone its <em>first</em> shot may leave in, centred on
 *     the aim. Zero fires dead straight. It is what makes a shooter a threat to the men
 *     <em>beside</em> the one it is aiming at, which is most of what makes a firing line feel like one
 * @param haltsToFire whether it plants itself while its target is in range rather than closing
 * @param gun which entry in the weapon table it fires
 * @param holdsGround whether it takes a position and then keeps it rather than following what it is
 *     hunting. True makes a creature that drives you off rather than chasing you down: it walks up
 *     once, and after that the only way past it is past it. It still answers a provocation, and
 *     still moves up again once it has lost you outright - both are somewhere to be rather than
 *     somebody to chase
 * @param arcDegrees how wide the field of view it will fire into is, centred on where it is looking.
 *     Wider than a trooper's on purpose: a creature built around a gun should be harder to walk round
 *     than a man is, but not impossible, and the blind arc behind it is the whole of what flanking one
 *     means. {@link PerceptionDef#ALL_ROUND} gives it eyes in the back of its head
 * @param turnRate how fast its aim swings onto what it is hunting, as a fraction of the angle still
 *     to cover per second. It is what turns {@code arcDegrees} from a rule into a moment: step round
 *     one and there is a beat before it has you again. Zero means it is always already looking at you
 * @param closeRange the distance it is best at, in world units - and therefore the distance a
 *     shooter that holds ground walks to. Inside it the thing is shooting fast and straight; zero
 *     switches both halves off, leaving one that shoots the same everywhere and stops as soon as it
 *     can reach at all. It must be comfortably inside {@code range}, or the amble it does while
 *     holding the position takes it back out of its own reach
 * @param closeFactor what being close multiplies both its cooldown and its cone by, in {@code (0, 1]}.
 *     One number for both because they are one idea - it has you at point-blank range - and two dials
 *     would only let them disagree
 * @param settleShots how many shots it takes to walk its aim in. The first is the wild one; by this
 *     many it is as tight as it gets. Zero means every shot is as loose as the first
 * @param settledSpreadFactor what the cone has narrowed to by then, as a fraction of
 *     {@code spreadDegrees}, in {@code (0, 1]}. This is the reason to break line of sight rather than
 *     to stand and trade: staying in its sights is what makes it good at shooting you
 * @param chargedGun what it carries instead after it has eaten something explosive, or null for a
 *     strain that gets nothing out of one. The barrel's reward for the other side (G0022)
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
     * A shooter with eyes all round, no settling and nothing to gain from a barrel: what shooting was
     * before any of that existed.
     *
     * <p>For fixtures and for any strain whose gun is beside the point. A test about where a shot
     * goes should not also be a test about whether the thing had turned far enough to take it.
     */
    public static RangedDef plain(float range, float spreadDegrees, boolean haltsToFire, WeaponKind gun) {
        return new RangedDef(
                true, range, spreadDegrees, haltsToFire, gun, false, PerceptionDef.ALL_ROUND, 0f, 0f, 1f, 0, 1f, null);
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

    /**
     * The smallest dot product between where it is looking and the direction to something for that
     * thing to be inside its field of view.
     *
     * <p>The same trade {@code PerceptionDef.arcCosine()} makes, for the same reason: a cosine
     * computed once at spawn means the per-tick test is a dot product and a comparison, with no
     * trigonometry anywhere in the tick.
     */
    public float arcCosine() {
        return (float) StrictMath.cos(StrictMath.toRadians(arcDegrees * 0.5));
    }
}
