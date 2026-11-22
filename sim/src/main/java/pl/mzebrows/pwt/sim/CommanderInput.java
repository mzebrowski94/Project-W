package pl.mzebrows.pwt.sim;

/**
 * One tick's worth of what one player is <em>holding</em>. The simulation is handed one of these per
 * slot and copies each into that commander's {@code Intent}; no system ever polls a device, which is
 * the input invariant {@code ArchitectureTest} enforces.
 *
 * <p><strong>Held state only.</strong> Everything here is safe to repeat: a stick still pushed, a
 * trigger still down. A one-off - an order given, a player joining - is an <em>input event</em> and
 * travels beside this on {@link PlayerInputs}, because the two behave differently when a tick's input
 * arrives late: held state can be repeated from the tick before, and an event must be applied exactly
 * once (ADR 0012).
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
 * @param aimRelative whether the aim above is an offset from the commander rather than a place on
 *     the field. A stick sets it; a mouse never does
 * @param dashing whether the dash input is held this tick (G0107). Held, like everything here; the dash goes on
 *     the press, which the simulation derives
 * @param redeploying whether the redeploy input is held this tick (G0109). Held, like the dash; only a press while he
 *     is down and his window is open does anything, which the simulation derives
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
        boolean aimRelative,
        boolean dashing,
        boolean redeploying) {

    /** Movement, aim and the two main buttons, with everything else released - the common case in tests. */
    public CommanderInput(float moveX, float moveY, float aimX, float aimY, boolean firing, boolean striking) {
        this(moveX, moveY, aimX, aimY, firing, striking, false, false, false, false, false, false);
    }

    /** Everything but the dash and redeploy, which are released. */
    public CommanderInput(
            float moveX,
            float moveY,
            float aimX,
            float aimY,
            boolean firing,
            boolean striking,
            boolean throwing,
            boolean acting,
            boolean dragging,
            boolean aimRelative) {
        this(moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, aimRelative, false, false);
    }

    /** Everything but redeploy, which is released. */
    public CommanderInput(
            float moveX,
            float moveY,
            float aimX,
            float aimY,
            boolean firing,
            boolean striking,
            boolean throwing,
            boolean acting,
            boolean dragging,
            boolean aimRelative,
            boolean dashing) {
        this(moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, aimRelative, dashing, false);
    }

    /** No movement, no firing, no swinging. What a simulation steps with when nothing is driving it. */
    public static final CommanderInput IDLE =
            new CommanderInput(0f, 0f, 0f, 0f, false, false, false, false, false, false, false, false);

    /** The same input with the fire button released - convenient in tests. */
    public CommanderInput notFiring() {
        return new CommanderInput(
                moveX,
                moveY,
                aimX,
                aimY,
                false,
                striking,
                throwing,
                acting,
                dragging,
                aimRelative,
                dashing,
                redeploying);
    }

    /** The same input with the melee button held - convenient in tests. */
    public CommanderInput swinging() {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, true, throwing, acting, dragging, aimRelative, dashing, redeploying);
    }

    /** The same input with the action key held, or released - convenient in tests. */
    public CommanderInput acting(boolean held) {
        return new CommanderInput(
                moveX,
                moveY,
                aimX,
                aimY,
                firing,
                striking,
                throwing,
                held,
                dragging,
                aimRelative,
                dashing,
                redeploying);
    }

    /** The same input with the drag key held, or released - convenient in tests. */
    public CommanderInput dragging(boolean held) {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, acting, held, aimRelative, dashing, redeploying);
    }

    /** The same input with the dash key held, or released - convenient in tests. */
    public CommanderInput dashing(boolean held) {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, aimRelative, held, redeploying);
    }

    /** The same input with the redeploy key held, or released - convenient in tests. */
    public CommanderInput redeploying(boolean held) {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, aimRelative, dashing, held);
    }

    /** The same input with its aim read as an offset from the commander rather than as a place. */
    public CommanderInput aimingRelatively() {
        return new CommanderInput(
                moveX, moveY, aimX, aimY, firing, striking, throwing, acting, dragging, true, dashing, redeploying);
    }
}
