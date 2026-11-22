package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * One kind of **emplacement** (G0113): a standing shooter nobody crews. Solid, immovable and unbreakable whatever its
 * kind - no health - and on the aliens' side, so its shots hurt only the men; but never an enemy - it carries no
 * {@code Enemy}, so nothing counts it, aims at it or hunts it - so nothing here says otherwise.
 *
 * @param radius its body, in world units: what blocks men, shots and blasts
 * @param look what a renderer draws it as
 * @param gun what it fires: one shot of it along each bearing of a step
 * @param periodSeconds how long between one step of its cycle and the next
 * @param wakeRadius how near a man on his feet must be for it to fire; zero is never by that - something else wakes it
 *     (ticket 09's wardens)
 * @param cycle the shape of its cycle: dealt once when it is put down, or a fixed volley
 */
@With
public record EmplacementDef(
        float radius, Look look, WeaponKind gun, float periodSeconds, float wakeRadius, CycleDef cycle) {

    /** Half a square's diagonal: the north-east point's share of north and of east. */
    private static final float DIAGONAL = 0.70710677f;

    /** Each compass point clockwise from north, as a unit vector: no transcendental in the simulation. */
    private static final float[] BEARING_X = {0f, DIAGONAL, 1f, DIAGONAL, 0f, -DIAGONAL, -1f, -DIAGONAL};

    private static final float[] BEARING_Y = {1f, DIAGONAL, 0f, -DIAGONAL, -1f, -DIAGONAL, 0f, DIAGONAL};

    public EmplacementDef {
        Require.positive("emplacement radius", radius);
        Require.positive("emplacement periodSeconds", periodSeconds);
        Require.notNegative("emplacement wakeRadius", wakeRadius);
    }

    /** East-west of compass point {@code point}, north being 0 and counting clockwise. */
    public static float bearingX(int point) {
        return BEARING_X[point];
    }

    /** North-south of compass point {@code point}. */
    public static float bearingY(int point) {
        return BEARING_Y[point];
    }
}
