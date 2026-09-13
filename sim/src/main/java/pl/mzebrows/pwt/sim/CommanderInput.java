package pl.mzebrows.pwt.sim;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

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
 * @param striking whether the melee input is held this tick
 * @param throwing whether the grenade input is held this tick
 * @param order the squad order issued this tick, or null for none. A one-tick event rather than
 *     a held state: an order is <em>given</em> once and then stands on whoever heard it (G0017)
 */
public record CommanderInput(
        float moveX,
        float moveY,
        float aimX,
        float aimY,
        boolean firing,
        boolean striking,
        boolean throwing,
        @Nullable Order order) {

    /**
     * The same tick of input with no order given, which is almost every tick.
     *
     * <p>An order is a rare, deliberate event; movement and aim happen sixty times a second. Making
     * the common case the shorter call keeps the two visibly different at every call site, rather
     * than burying a {@code null} on the end of every one of them.
     */
    public CommanderInput(float moveX, float moveY, float aimX, float aimY, boolean firing, boolean striking) {
        this(moveX, moveY, aimX, aimY, firing, striking, false, null);
    }

    /** No movement, no firing, no swinging. What a simulation steps with when nothing is driving it. */
    public static final CommanderInput IDLE = new CommanderInput(0f, 0f, 0f, 0f, false, false, false, null);

    /** The same input with the fire button released - convenient in tests. */
    public CommanderInput notFiring() {
        return new CommanderInput(moveX, moveY, aimX, aimY, false, striking, throwing, order);
    }

    /** The same input with the melee button held - convenient in tests. */
    public CommanderInput swinging() {
        return new CommanderInput(moveX, moveY, aimX, aimY, firing, true, throwing, order);
    }

    /** The same input carrying a squad order, or none. */
    public CommanderInput ordering(@Nullable Order newOrder) {
        return new CommanderInput(moveX, moveY, aimX, aimY, firing, striking, throwing, newOrder);
    }
}
