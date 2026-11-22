package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * What a commander is trying to do this tick. Written from the per-tick input value object before
 * systems run; read by systems, which never see the input object itself.
 *
 * <p>{@code moveX}/{@code moveY} is a direction, not a speed - length above one is normalised by the
 * system that consumes it, so holding two keys is not faster than holding one. {@code aimX}/{@code
 * aimY} is a point in the world, not a direction, because the device only knows where the pointer
 * is; turning that into a facing is the simulation's job (G0002).
 */
public final class Intent implements Component, Pool.Poolable {

    public float moveX;
    public float moveY;
    public float aimX;
    public float aimY;
    public boolean firing;

    /**
     * Whether the fire input went down this tick: held now and not held the tick before. What a gun that acts once a
     * press reads - the detonator throws on one and sets its limpet off on the next (G0100) - so a trigger still held
     * from the throw sets nothing off. Derived where the tick's input is copied in, from the {@link #firing} it
     * replaces.
     */
    public boolean firePressed;

    /** Whether the melee input is held this tick. The swing itself is rate-limited by {@link Melee}. */
    public boolean striking;

    /** Whether the grenade input is held this tick. The throw is rate-limited by {@link Thrower}. */
    public boolean throwing;

    /**
     * Whether the action input is held this tick. What action that is - if any is available at all -
     * is decided by {@code ActionSystem} from what he is standing next to, never by the player
     * choosing from a list. One key, and the field tells him what it will do.
     */
    public boolean acting;

    /** Whether the drag input is held this tick. */
    public boolean dragging;

    /** Whether the dash input is held this tick (G0107). */
    public boolean dashing;

    /**
     * Whether the dash input went down this tick: held now and not held the tick before. A dash is launched on the
     * press, never on the hold, so a key held through a refusal launches nothing once it ends. Derived as {@link
     * #firePressed} is.
     */
    public boolean dashPressed;

    /**
     * The squad order given this tick, or null.
     *
     * <p>One tick only, like everything else here. An order is <em>given</em> once and then stands on
     * whichever troopers were near enough to hear it, so holding the key does not re-issue it every
     * tick to whoever has since wandered into range.
     */
    public @Nullable Order order;

    /**
     * The pickup he takes from the crate he has open this tick, or null (G0056). An event, by kind rather
     * than by place, so a crate that changed between his press and the tick cannot hand him something he
     * never saw.
     */
    public @Nullable PickupKind crateTake;

    /** Whether he closes the crate he has open this tick (G0056). An event. */
    public boolean crateClose;

    /** Copies a tick's worth of intent in, and returns this component, for fluent spawning. */
    public Intent set(
            float newMoveX,
            float newMoveY,
            float newAimX,
            float newAimY,
            boolean newFiring,
            boolean newStriking,
            boolean newThrowing,
            boolean newActing,
            boolean newDragging,
            boolean newDashing,
            @Nullable Order newOrder) {
        this.moveX = newMoveX;
        this.moveY = newMoveY;
        this.aimX = newAimX;
        this.aimY = newAimY;
        this.firePressed = newFiring && !this.firing;
        this.firing = newFiring;
        this.striking = newStriking;
        this.throwing = newThrowing;
        this.acting = newActing;
        this.dragging = newDragging;
        this.dashPressed = newDashing && !this.dashing;
        this.dashing = newDashing;
        this.order = newOrder;
        this.crateTake = null;
        this.crateClose = false;
        return this;
    }

    /** Copies a tick's crate events in, after {@link #set}, and returns this. */
    public Intent crate(@Nullable PickupKind take, boolean close) {
        this.crateTake = take;
        this.crateClose = close;
        return this;
    }

    @Override
    public void reset() {
        moveX = 0f;
        moveY = 0f;
        aimX = 0f;
        aimY = 0f;
        firing = false;
        firePressed = false;
        striking = false;
        throwing = false;
        acting = false;
        dragging = false;
        dashing = false;
        dashPressed = false;
        order = null;
        crateTake = null;
        crateClose = false;
    }
}
