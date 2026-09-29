package pl.mzebrows.pwt.sim.def;

/**
 * Every decision the AI leaves to the dice, named in one place.
 *
 * <p><strong>This enum is the index</strong>: anything a unit does <em>sometimes</em> has a constant
 * here. <strong>Every number is a chance per second of opportunity</strong>, never per tick: the clock
 * runs only while the thing is possible, so 0.5 with two seconds of chance is about once.
 *
 * <p>Numbers are in {@code assets/sim/*.properties} under {@code sim.ai.chance.<name>}, overridden per strain
 * under {@code sim.unit.<kind>.chances.<name>}. Zero switches a behaviour off and draws nothing, so it
 * cannot reshuffle other rolls (pillar 6).
 */
public enum AiChance {

    /**
     * How often a trooper tries a bare-handed sweep at something already committed to a leap.
     *
     * <p><strong>Very small on purpose</strong>: answering a leap is the commander's moment (G0013).
     */
    SWING_AT_A_LEAP,

    /**
     * How often a creature standing against an explosive barrel bites into it.
     *
     * <p>Rolled only while touching one (G0022).
     */
    BITE_A_BARREL,

    /** How often a trooper with nothing better to do goes to pick up a comrade who is down. */
    AID_A_TROOPER,

    /**
     * How often a trooper standing near a free gun decides to get on it rather than get on with what
     * it was doing.
     *
     * <p>Rolled only with something already in the gun's reach.
     */
    MAN_A_TURRET,

    /**
     * How often a trooper goes to pick up the commander.
     *
     * <p>Zero makes him unrescuable.
     */
    AID_THE_COMMANDER,

    /**
     * How often a trooper in something's jaws swings at it (G0045).
     *
     * <p><strong>Slow on purpose</strong>: a man who freed himself every time would make being taken a
     * formality, and the commander coming for him is the point. Rolled only while he is held.
     */
    STRUGGLE,

    /**
     * How often a trooper throws a grenade into a pack, per second of opportunity (G0051).
     *
     * <p><strong>Zero until an upgrade turns it up</strong>, and rolled only while a throw is safe and
     * worth it: a pack where it would land, nobody on the player's side near it, a clear path, and the
     * squad's gap run out.
     */
    THROW_A_GRENADE
}
