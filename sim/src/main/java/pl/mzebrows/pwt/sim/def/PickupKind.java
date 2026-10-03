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
    /**
     * A crate of men. Worth three troopers to whoever on the player's side reaches it, and nothing at
     * all to the other side - the aliens walk over one and leave it standing.
     */
    SUPPLY_DROP,
    /**
     * A weapons cache: a better gun to the commander, and nothing at all to the other side.
     *
     * <p><strong>One kind per gun</strong>, so which gun is in a crate is legible before he has
     * walked to it (pillar 5). It is the ordinary way this codebase adds a kind of thing - a
     * constant, a definition and a block in {@code assets/sim/*.properties}, never a branch (G0008) - and it
     * means a fourth gun needs no new idea, only a fourth crate.
     */
    ARMOURY_RAILGUN,
    ARMOURY_FLAMER,
    ARMOURY_SHOTGUN,
    ARMOURY_LASER,
    ARMOURY_BAZOOKA,
    ARMOURY_PULSE,
    /**
     * A <em>crate</em> (G0056): nothing is taken from it by walking onto it - a commander opens it with the
     * action key and chooses what to take from the cards shown in his top bar. What it holds - two to four
     * pickups, guns' caches and medkits - is dealt from the armoury deck the first time anybody opens it,
     * and stays what it is from then on; what is taken is gone from it, and an empty crate is gone from
     * the field (G0056).
     */
    CRATE,
    /**
     * An explosive barrel. A pickup with nothing on offer to anybody: the commander is offered
     * nothing and leaves it standing, and what an alien gets for biting into one is the explosion.
     *
     * <p>It is a pickup rather than an obstacle because avoidance steers a unit around anything solid
     * <em>except</em> a pickup - as an obstacle it would be a thing the aliens carefully walked
     * around, which is the opposite of the mechanic.
     */
    BARREL,
    /**
     * Alien food that runs away. A pickup like any other as far as taking it goes - it is claimed on
     * contact and feeds whatever ate it - but it wanders the field on its own legs and sidesteps a
     * shot it sees coming. The commander is offered nothing by it, so his only interest in one is
     * denying it.
     */
    RABBIT,
    /**
     * A medkit (G0053): every wound cleared, to a man who has one - and to nobody else. A man without a
     * wound walks over it and leaves it standing; the aliens have nothing to gain from it and no weapon
     * can touch it.
     */
    MEDKIT
}
