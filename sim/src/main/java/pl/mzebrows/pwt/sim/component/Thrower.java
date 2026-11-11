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
 * <p><strong>A count, kept as what was spent</strong> (G0077): what he has left is his most less what he has
 * thrown, and his most is folded where it is read ({@code Throws}). So a new body comes in full, and an upgrade
 * that raises the most hands him the grenade it adds, with nothing else to write. Zero {@link #most} is
 * uncounted - a trooper's throws (G0051).
 */
public final class Thrower implements Component, Pool.Poolable {

    public int cooldownTicks;

    public int remainingTicks;

    /** How fast what it throws travels, in world units per second. */
    public float speed;

    /** How far that travels in total, in world units, however many things it comes off on the way. */
    public float range;

    /** The circular body of the thrown thing itself. Small: it has to slip through what it bounces off. */
    public float radius;

    /** How wide the blast it leaves is. What makes the range a decision rather than a button. */
    public float blastRadius;

    /** How many it carries at most before upgrades; zero is not counted at all. */
    public int most;

    /** How many it has thrown since it was last full. */
    public int spent;

    /** Sets the throw ready to be used immediately and full, and returns this component, for fluent spawning. */
    public Thrower set(
            int newCooldownTicks, float newSpeed, float newRange, float newRadius, float newBlastRadius, int newMost) {
        this.cooldownTicks = newCooldownTicks;
        this.remainingTicks = 0;
        this.speed = newSpeed;
        this.range = newRange;
        this.radius = newRadius;
        this.blastRadius = newBlastRadius;
        this.most = newMost;
        this.spent = 0;
        return this;
    }

    /** Whether the wait since the last throw has run out. Whether he has one to throw is {@code Throws}'. */
    public boolean ready() {
        return remainingTicks <= 0;
    }

    /** Whether its throws are counted at all. */
    public boolean counted() {
        return most > 0;
    }

    /**
     * Whether it carries its most, whatever upgrades make that: nothing thrown since it was last full. True of a
     * thrower whose throws are not counted.
     */
    public boolean full() {
        return spent == 0;
    }

    @Override
    public void reset() {
        cooldownTicks = 0;
        remainingTicks = 0;
        speed = 0f;
        range = 0f;
        radius = 0f;
        blastRadius = 0f;
        most = 0;
        spent = 0;
    }
}
