package pl.mzebrows.pwt.sim.system.upgrade;

/**
 * Every upgrade and adaptation there is. A new one is a constant here, a definition and a block in
 * {@code assets/sim/upgrades.properties} under {@code sim.upgrade.<name>} - never a class (ADR 0008).
 */
public enum UpgradeKind {
    /** More men to a crate, and room in the squad for them. */
    LARGER_ARMY,
    /** A shorter wait between shots, for the squad and every commander. */
    WEAPON_HANDLING,
    /** Harder-hitting shots from the squad, whatever gun each man holds. */
    MARKSMEN,
    /** A squad readier to go and fetch a man who is down, and readier still for the commander. */
    FIELD_MEDICINE,
    /** A shorter wait between grenades for the commander of the slot that took it - personal. */
    GRENADIER,
    /** A faster squad. The commander's own pace is his, and not this. */
    FORCED_MARCH,
    /** Quicker work putting a man back on his feet, for the squad and every commander. */
    FIRST_AID,
    /** One more wound on his feet per rank, for the squad and every commander (G0053). */
    THICK_SKIN,
    /** The chooser's wounds clear on their own some seconds after his last one (G0053). Personal. */
    SELF_TREATMENT,
    /** A flamer in every crate opened from now on, and harder-hitting flames for whoever holds one (G0056). */
    FIREPOWER,
    /** Troopers who throw grenades into packs - only where the blast is the enemy's (G0051). */
    EXPLOSIVES,
    /** Every crate opened from now on holds as many pickups as a crate can (G0056). */
    FULL_CRATES,
    /** An adaptation: every alien spits more often. */
    RAPID_SPIT,
    /** An adaptation: the swarm walks faster - the speed stat the squad's Forced March moves. */
    SWIFT_MOVEMENT,
    /** An adaptation: a leaper commits from farther, and flies faster and farther. */
    LONG_LEAP,
    /** An adaptation: one more leap strung onto every sequence at a man. */
    CHAIN_LEAP,
    /** An adaptation: the swarm notices from farther, gives up later and ambles wider while idle. */
    BLOODHOUND,
    /** An adaptation: every brood a nest sends holds more. */
    LARGER_BROOD,
    /** An adaptation: every alien leaves a spatter of venom where it dies, which a man goes round only on a chance. */
    TOXIC_BLOOD,
    /** The chooser carries one more grenade per rank, and is handed it when he takes it (G0077). Personal. */
    BANDOLIER,
    /** The chooser's every dash stuns the aliens round him as it launches - its surge, wider a rank (G0108). Personal. */
    ROCKETMAN,
    /** The chooser holds one more dash a rank, and each comes back sooner (G0108). Personal. */
    PANTERA
}
