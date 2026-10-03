package pl.mzebrows.pwt.client.play;

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
    /**
     * Draw this machine's own commanders facing their players' aim at once, or as the world has them.
     * Online, the difference is the input delay (ADR 0012); the switch is there to feel it.
     */
    TOGGLE_COSMETIC_LEAD,
    /** A joining copy: step the simulated lag to the next of {@code net.conditions.latencySteps}. */
    NEXT_LAG,
    /** Leave for the menu - what the pause menu's LEAVE carries out, once confirmed. */
    LEAVE,
    /**
     * Take the first, second or third card of the keyboard seat's oldest draft. Scaffolding kept beside the
     * draft bar; online the pick crosses the wire like any other (G0049).
     */
    TAKE_FIRST_CARD,
    TAKE_SECOND_CARD,
    TAKE_THIRD_CARD
}
