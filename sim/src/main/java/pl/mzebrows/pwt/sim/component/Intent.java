package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

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

    /** Whether the melee input is held this tick. The swing itself is rate-limited by {@link Melee}. */
    public boolean striking;

    /**
     * The squad order given this tick, or null.
     *
     * <p>One tick only, like everything else here. An order is <em>given</em> once and then stands on
     * whichever troopers were near enough to hear it, so holding the key does not re-issue it every
     * tick to whoever has since wandered into range.
     */
    public @Nullable Order order;

    /** Copies a tick's worth of intent in, and returns this component, for fluent spawning. */
    public Intent set(
            float newMoveX,
            float newMoveY,
            float newAimX,
            float newAimY,
            boolean newFiring,
            boolean newStriking,
            @Nullable Order newOrder) {
        this.moveX = newMoveX;
        this.moveY = newMoveY;
        this.aimX = newAimX;
        this.aimY = newAimY;
        this.firing = newFiring;
        this.striking = newStriking;
        this.order = newOrder;
        return this;
    }

    @Override
    public void reset() {
        moveX = 0f;
        moveY = 0f;
        aimX = 0f;
        aimY = 0f;
        firing = false;
        striking = false;
        order = null;
    }
}
