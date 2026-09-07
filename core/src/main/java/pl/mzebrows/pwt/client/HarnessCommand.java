package pl.mzebrows.pwt.client;

/**
 * Something the alpha-phase harness can be asked to do while a scenario is running.
 *
 * <p>Separate from the keys that trigger it and from the code that carries it out, so both sides can
 * be tested on their own. The game's own input - moving, aiming, firing - is not here; that reaches
 * the simulation as a {@code CommanderInput} and never passes through the harness.
 */
public enum HarnessCommand {
    /** Stop and start the clock. */
    PAUSE,
    /** Advance exactly one tick. Ignored unless paused. */
    STEP,
    /** Rebuild the scenario from its first tick. */
    RESTART,
    /** Show or hide the stats overlay. */
    TOGGLE_OVERLAY,
    /** Make the commander invulnerable, or stop. A dev tool - see Invulnerable.permanent. */
    TOGGLE_GOD_MODE,
    /** Leave for the menu. */
    LEAVE
}
