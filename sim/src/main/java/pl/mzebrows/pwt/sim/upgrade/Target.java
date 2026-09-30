package pl.mzebrows.pwt.sim.upgrade;

import pl.mzebrows.pwt.sim.def.Side;

/**
 * Whom an effect reaches (G0049). Declared per effect, so one upgrade may reach the squad with one
 * number and the side with another. A turret is never reached: its gun belongs to the place (G0024).
 */
public enum Target {
    /** Every trooper. */
    SQUAD,
    /** Every commander. */
    COMMANDERS,
    /** The commander of the slot that took it: a personal upgrade, whose ranks count per slot. */
    CHOOSER,
    /** The squad and the commanders. */
    TEAM,
    /** The side's own numbers, which no one unit reads - what a crate is worth, the squad's headroom. */
    SIDE,
    /** Every alien: an adaptation. */
    ALIENS;

    /** The side whose upgrades may name this target. */
    public Side side() {
        return this == ALIENS ? Side.ALIEN : Side.PLAYER;
    }
}
