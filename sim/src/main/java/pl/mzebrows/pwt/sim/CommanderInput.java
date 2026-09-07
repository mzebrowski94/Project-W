package pl.mzebrows.pwt.sim;

/**
 * One tick's worth of what the player is asking for. The simulation is handed this and copies it
 * into the commander's {@code Intent}; no system ever polls a device, which is the input invariant
 * {@code ArchitectureTest} enforces.
 *
 * <p>Aim is a <em>point in the world</em>, not a direction: a mouse only knows where it is pointing,
 * and turning that into a facing needs the commander's position, which is the simulation's business.
 * A gamepad's right stick would be converted to a point by the client for the same reason.
 *
 * @param moveX desired movement along x, in {@code [-1, 1]}
 * @param moveY desired movement along y, in {@code [-1, 1]}
 * @param aimX world x the commander is aiming at
 * @param aimY world y the commander is aiming at
 * @param firing whether the fire input is held this tick
 */
public record CommanderInput(float moveX, float moveY, float aimX, float aimY, boolean firing) {

    /** No movement, no firing. What a simulation steps with when nothing is driving it. */
    public static final CommanderInput IDLE = new CommanderInput(0f, 0f, 0f, 0f, false);

    /** The same input with the fire button released - convenient in tests. */
    public CommanderInput notFiring() {
        return new CommanderInput(moveX, moveY, aimX, aimY, false);
    }
}
