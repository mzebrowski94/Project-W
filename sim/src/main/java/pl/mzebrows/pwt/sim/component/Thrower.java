package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What a unit can throw, and how often.
 *
 * <p>A sibling to {@link Melee} rather than another {@link Weapon}. A gun is found on the field and
 * lost with the body that carried it; this is something the commander can always do, like his hands,
 * and every field here is what would differ between one throwable and another.
 *
 * <p>Ammunition is deliberately absent. Unlimited grenades are alpha-phase scaffolding, and a count
 * would be one more field here rather than anything structural, so leaving it out settles nothing.
 */
public final class Thrower implements Component, Pool.Poolable {

    public float cooldownSeconds;

    public float remainingSeconds;

    /** How fast what it throws travels, in world units per second. */
    public float speed;

    /** How far that travels in total, in world units, however many things it comes off on the way. */
    public float range;

    /** The circular body of the thrown thing itself. Small: it has to slip through what it bounces off. */
    public float radius;

    /** How wide the blast it leaves is. What makes the range a decision rather than a button. */
    public float blastRadius;

    /** Sets the throw ready to be used immediately, and returns this component, for fluent spawning. */
    public Thrower set(
            float newCooldownSeconds, float newSpeed, float newRange, float newRadius, float newBlastRadius) {
        this.cooldownSeconds = newCooldownSeconds;
        this.remainingSeconds = 0f;
        this.speed = newSpeed;
        this.range = newRange;
        this.radius = newRadius;
        this.blastRadius = newBlastRadius;
        return this;
    }

    public boolean ready() {
        return remainingSeconds <= 0f;
    }

    @Override
    public void reset() {
        cooldownSeconds = 0f;
        remainingSeconds = 0f;
        speed = 0f;
        range = 0f;
        radius = 0f;
        blastRadius = 0f;
    }
}
