package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * One kind of pickup: how big it is, what it looks like, and what each side gets for taking it.
 *
 * <p><strong>A grant per side is the whole design.</strong> An item both sides want is a contested
 * object on the field - deny it or take it - and an item only one side wants is the same record with
 * {@link Grant#NOTHING} in the other slot. That is why there is no "who may take this" flag: having
 * nothing on offer already means exactly that, with one fewer thing to keep consistent.
 *
 * @param radius circular body, in world units. Small - a pickup is a dot to walk over, not an
 *     obstacle to walk around
 * @param look what a renderer draws it as
 * @param playerGrant what the commander and, later, troopers get for taking it
 * @param alienGrant what an enemy gets for eating it
 */
@With
public record PickupDef(float radius, Look look, Grant playerGrant, Grant alienGrant) {

    public PickupDef {
        Require.positive("pickup radius", radius);
    }

    /**
     * What this offers a given side.
     *
     * <p>An exhaustive switch, so a new side cannot be added without deciding what every pickup does
     * for it. Neutral gets nothing: scenery does not go shopping.
     */
    public Grant grantFor(Side side) {
        return switch (side) {
            case PLAYER -> playerGrant;
            case ALIEN -> alienGrant;
            case NEUTRAL -> Grant.NOTHING;
        };
    }

    /** True if this side has any reason to consume it. What stops a pickup vanishing for nothing. */
    public boolean offersSomethingTo(Side side) {
        return Grant.isSomething(grantFor(side));
    }
}
