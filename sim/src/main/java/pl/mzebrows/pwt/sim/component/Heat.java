package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * How hot the gun this entity is holding has become, and whether it has stopped itself.
 *
 * <p><strong>Runtime state only.</strong> What a shot costs and how fast it comes off are facts
 * about the gun, read from the {@code Weapon} where they are needed - so a man who swaps guns is
 * carrying the new gun's rules immediately and a gun nobody is holding still obeys its own.
 *
 * <p>It lives on the thing that holds the gun rather than on the gun, and for a turret that is the
 * turret itself. That is the whole of why a man cannot cool one by stepping off it: the heat never
 * belonged to him.
 */
public final class Heat implements Component, Pool.Poolable {

    /** How full it is, in {@code [0, 1]}. */
    public float value;

    /** Whether the gun has stopped itself and is waiting to cool. */
    public boolean locked;

    /** What one shot costs, and the stop if that fills it. */
    public void spend(float perShot) {
        if (perShot <= 0f) {
            return;
        }
        value += perShot;
        if (value >= 1f) {
            value = 1f;
            locked = true;
        }
    }

    /**
     * A tick's worth of cooling, and the gun back in hand once it has fallen far enough.
     *
     * <p>Unlocking lives here rather than wherever firing is decided, because it is the other half of
     * the same rule: one place owns the bar, and nothing that fires has to know how the stop ends.
     */
    public void cool(float perSecond, float resumeBelow, float deltaTime) {
        value -= perSecond * deltaTime;
        if (value <= 0f) {
            value = 0f;
        }
        if (locked && value <= resumeBelow) {
            locked = false;
        }
    }

    @Override
    public void reset() {
        value = 0f;
        locked = false;
    }
}
