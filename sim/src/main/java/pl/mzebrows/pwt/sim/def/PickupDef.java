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
 * @param shineSeconds how long one pulse of its glow takes, in seconds. <strong>Zero means it does
 *     not shine</strong>, which is what most things report - the codebase's usual way of saying "not
 *     for me", so a kind opts in by naming a number rather than by a flag somewhere else. A small
 *     thing on a large map has to be findable, and a pulse is the cheapest way of saying "worth
 *     walking to" without drawing a marker the player then has to learn
 * @param armoury its part in the armoury deck (G0056): a card for a gun's cache or a medkit, a crate for
 *     a crate, and {@link ArmouryDef#off()} - no block at all - for everything else
 */
@With
public record PickupDef(
        float radius, Look look, Grant playerGrant, Grant alienGrant, float shineSeconds, ArmouryDef armoury) {

    public PickupDef {
        Require.positive("pickup radius", radius);
        Require.notNegative("pickup shineSeconds", shineSeconds);
        if (armoury.crate() && (Grant.isSomething(playerGrant) || Grant.isSomething(alienGrant))) {
            throw new IllegalArgumentException(
                    "a crate offers nothing to whoever walks onto it (G0056): it is opened, not taken");
        }
    }

    /** Anything that is not in the armoury deck and is no crate. */
    public PickupDef(float radius, Look look, Grant playerGrant, Grant alienGrant, float shineSeconds) {
        this(radius, look, playerGrant, alienGrant, shineSeconds, ArmouryDef.off());
    }

    /** True if this is a thing that catches the eye rather than a thing lying in the grass. */
    public boolean shines() {
        return shineSeconds > 0f;
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

    /**
     * True if this is a thing to steer around rather than a thing to walk over.
     *
     * <p>Derived from what it offers rather than declared separately, because there is exactly one
     * reason to give a pickup a wide berth and it is already written down: what it hands whoever
     * takes it is an explosion. A second flag saying the same thing would be a second thing to keep
     * consistent, and the first time the two disagreed the barrel would be walked into cheerfully.
     *
     * <p>It is why the invariant "nothing steers around a pickup" now reads "nothing steers around a
     * pickup it wants" (G0022). Everything edible is still walked straight over - a hungry alien
     * must not circle its own dinner - and only what goes off is given room.
     */
    public boolean shunned() {
        return playerGrant instanceof Grant.Detonate || alienGrant instanceof Grant.Detonate;
    }
}
