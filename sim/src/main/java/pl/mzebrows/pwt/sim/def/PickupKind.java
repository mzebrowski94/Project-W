package pl.mzebrows.pwt.sim.def;

/**
 * Every kind of thing that can be lying on the field waiting to be taken.
 *
 * <p>What each one does is in its {@link PickupDef} and differs per side, so a kind here names a
 * thing rather than an effect: the same growth feeds an alien and does nothing for a commander, and
 * a supply crate is worth having to both of them for different reasons.
 */
public enum PickupKind {
    /** Alien food. One of these is a mouthful, not a meal. */
    MUSHROOM,
    /** Alien food that is not food at all: whatever eats one turns into something else on the spot. */
    FLOWER,
    /** A trooper in stasis. Alpha-phase scaffolding - the squad does not exist yet. */
    FROZEN_TROOPER,
    /** A weapons cache: a better gun to the commander, and a mouthful to whatever reaches it first. */
    ARMOURY,
    /**
     * Alien food that runs away. A pickup like any other as far as taking it goes - it is claimed on
     * contact and feeds whatever ate it - but it wanders the field on its own legs and sidesteps a
     * shot it sees coming. The commander is offered nothing by it, so his only interest in one is
     * denying it.
     */
    RABBIT
}
