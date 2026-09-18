package pl.mzebrows.pwt.sim;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * One tick's worth of what one player is asking for. The simulation is handed one of these per slot
 * and copies each into that commander's {@code Intent}; no system ever polls a device, which is the
 * input invariant {@code ArchitectureTest} enforces.
 *
 * <p>Aim is a <em>place</em>, not a direction: a mouse only knows where it is pointing, and turning
 * that into a facing needs the commander's position, which is the simulation's business. A stick
 * knows the opposite - a direction and nothing about where the commander is standing - so it sets
 * {@link #aimRelative} and sends an offset, and {@link Simulation#step(PlayerInputs)} adds the
 * commander's own position to it before any system runs. That is the one place the two ever differ;
 * every system downstream reads the same absolute point it always did.
 *
 * @param moveX desired movement along x, in {@code [-1, 1]}
 * @param moveY desired movement along y, in {@code [-1, 1]}
 * @param aimX world x the commander is aiming at, or the x offset from him when {@code aimRelative}
 * @param aimY world y the commander is aiming at, or the y offset from him when {@code aimRelative}
 * @param firing whether the fire input is held this tick
 * @param striking whether the melee input is held this tick
 * @param throwing whether the grenade input is held this tick
 * @param acting whether the action input is held this tick. Held rather than pressed, because what
 *     it does takes time - a stabilise is a second and a half of standing still, and letting go is
 *     how you stop
 * @param dragging whether the drag input is held this tick
 * @param order the squad order issued this tick, or null for none. A one-tick event rather than
 *     a held state: an order is <em>given</em> once and then stands on whoever heard it (G0017)
 * @param aimRelative whether the aim above is an offset from the commander rather than a place on
 *     the field. A stick sets it; a mouse never does
 */
public record CommanderInput(
        float moveX,
        float moveY,
        float aimX,
        float aimY,
        boolean firing,
        boolean striking,
        boolean throwing,
        boolean acting,
        boolean dragging,
        @Nullable Order order,
        boolean aimRelative) {

    /**
     * The same tick of input with no order given, which is almost every tick.
     *
     * <p>An order is a rare, deliberate event; movement and aim happen sixty times a second. Making
     * the common case the shorter call keeps the two visibly different at every call site, rather
     * than burying a {@code null} on the end of every one of them.
     */
    public CommanderInput(float moveX, float moveY, float aimX, float aimY, boolean firing, boolean striking) {
        this(moveX, moveY, aimX, aimY, firing, striking, false, false, false, null, false);
    }

    /** No movement, no firing, no swinging. What a simulation steps with when nothing is driving it. */
    public static final CommanderInput IDLE =
            new CommanderInput(0f, 0f, 0f, 0f, false, false, false, false, false, null, false);

    /** The same input with the fire button released - convenient in tests. */
    public CommanderInput notFiring() {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, false, striking, throwing, acting, dragging, order, aimRelative);
    }

    /** The same input with the melee button held - convenient in tests. */
    public CommanderInput swinging() {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, true, throwing, acting, dragging, order, aimRelative);
    }

    /** The same input carrying a squad order, or none. */
    public CommanderInput ordering(@Nullable Order newOrder) {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, newOrder, aimRelative);
    }

    /** The same input with the action key held, or released - convenient in tests. */
    public CommanderInput acting(boolean held) {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, held, dragging, order, aimRelative);
    }

    /** The same input with the drag key held, or released - convenient in tests. */
    public CommanderInput dragging(boolean held) {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, acting, held, order, aimRelative);
    }

    /** The same input with its aim read as an offset from the commander rather than as a place. */
    public CommanderInput aimingRelatively() {
        return new CommanderInput(moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, order, true);
    }
}
