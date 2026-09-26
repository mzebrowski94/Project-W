package pl.mzebrows.pwt.sim.def;

/**
 * Every decision the AI leaves to the dice, named in one place.
 *
 * <p><strong>This enum is the index.</strong> Occasional behaviour is scattered by nature - a trooper
 * swinging at a leaper lives in one system, a creature biting into a barrel in another, a man going
 * to pick up a comrade in a third - and until this existed the only way to find them all was to read
 * every system looking for a call into the {@code Rng}. Anything a unit does <em>sometimes</em> has a
 * constant here, so the whole list can be read at a glance and nothing conditional can hide.
 *
 * <p><strong>Every number is a chance per second of opportunity</strong>, never per tick. A per-tick
 * number means nothing a person can reason about and changes silently if the tick rate ever does; a
 * per-second one survives both. "Of opportunity" is the other half: the clock runs only while the
 * thing is actually possible, so 0.5 means a unit with the chance in front of it for two seconds
 * takes it about once.
 *
 * <p>The value here is the fallback. {@code game.properties} overrides it under
 * {@code sim.ai.chance.<name>}, and a strain that needs its own number overrides that again under
 * {@code sim.unit.<kind>.chances.<name>} - which is what makes a shooter far hungrier for a barrel
 * than a grunt without either of them being a special case in code.
 *
 * <p>Zero switches a behaviour off and draws no random number at all, so turning one off in a
 * scenario cannot reshuffle what every other behaviour rolls (pillar 6).
 */
public enum AiChance {

    /**
     * How often a trooper tries a bare-handed sweep at something already committed to a leap.
     *
     * <p><strong>Very small on purpose, and that is a design constraint rather than tuning.</strong>
     * Answering a leap is the commander's moment and the whole reason the clinch rule exists (G0013);
     * a squad that reliably swatted leapers out of the air would quietly take it away from him.
     */
    SWING_AT_A_LEAP(0.5f),

    /**
     * How often a creature standing against an explosive barrel bites into it.
     *
     * <p>The other half of "it steers clear of one". Avoidance is an opinion about a course rather
     * than a wall, so a creature in a crowd or halfway through a charge still ends up touching one;
     * this is what decides whether touching it is the end of the barrel. Low for anything that
     * chases, and far higher for anything that shoots, which is what a strain override is for.
     */
    BITE_A_BARREL(0.8f),

    /** How often a trooper with nothing better to do goes to pick up a comrade who is down. */
    AID_A_TROOPER(0.4f),

    /**
     * How often a trooper standing near a free gun decides to get on it rather than get on with what
     * it was doing.
     *
     * <p>High, and deliberately: a turret nobody is on is a turret the level might as well not have
     * put down. What keeps it from being constant is that it is only ever rolled with something
     * already in the gun's reach - a man does not sit down at a gun for the view - and that the gun
     * puts him off again once there has been nothing to shoot at for a while.
     */
    MAN_A_TURRET(2f),

    /**
     * How often a trooper goes to pick up the commander.
     *
     * <p>Well above {@link #AID_A_TROOPER}: the squad exists because of him, and a man who has to
     * choose between two bodies on the ground should be seen to choose. It is also the whole of the
     * answer to "who picks the commander up" - drop it to zero and he is unrescuable again.
     */
    AID_THE_COMMANDER(1.5f);

    private final float standard;

    AiChance(float standard) {
        this.standard = standard;
    }

    /** What this is worth with no configuration at all, as a chance per second of opportunity. */
    public float standard() {
        return standard;
    }
}
