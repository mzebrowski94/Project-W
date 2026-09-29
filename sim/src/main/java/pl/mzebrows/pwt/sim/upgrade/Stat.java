package pl.mzebrows.pwt.sim.upgrade;

/**
 * Every number an upgrade may move. Each is read in exactly one place: one {@link Reads} method of
 * {@link Modifiers}, with one caller. A stat nobody reads fails the build, rather than being a number
 * somebody tunes to no effect (ADR 0017).
 */
public enum Stat {
    /** How long a gun waits between pulls. Read where every shooter's pull is charged. */
    FIRE_COOLDOWN,
    /** What each shot of one pull does. Read once per volley. */
    GUN_DAMAGE,
    /** How many men a crate of reinforcements is worth. Read where the crate is spent. */
    MEN_PER_CRATE,
    /** How many men past its starting size the squad may hold. Read where a man is landed. */
    SQUAD_HEADROOM,
    /** How long a thrower waits between grenades. Read where a grenade leaves the hand. */
    GRENADE_COOLDOWN,
    /** How often an occasional behaviour happens, one per {@code AiChance}. Read where the dice are asked. */
    CHANCE
}
