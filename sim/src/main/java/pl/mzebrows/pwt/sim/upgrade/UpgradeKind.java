package pl.mzebrows.pwt.sim.upgrade;

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
    FIRST_AID
}
