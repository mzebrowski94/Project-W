package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A fired entity that despawns when it hits something or leaves the arena.
 *
 * <p>It carries <strong>where it was fired from</strong>, and deliberately not <em>who</em> fired it.
 * Something shot at wants to go and deal with the shooter, and a place answers that without a
 * reference: components are pooled, so an owner kept here could outlive the shooter and come back
 * pointing at whatever was built from the recycled instance (G0010). A muzzle flash is a place, and
 * walking at it is what the player would expect to see (G0016).
 *
 * <p>The origin is where the shot started, not where it landed. The impact point is a couple of
 * units from whoever was hit and is no use as somewhere to go.
 */
public final class Projectile implements Component, Pool.Poolable {

    /** Where this shot was fired from, in world units. */
    public float originX;

    public float originY;

    /**
     * How far it may fly before it expires, in world units. Zero is until it hits something or
     * leaves the level, which is what almost every shot in the game declares.
     *
     * <p>A property of the shot rather than of the aim. The enforced invariant that aim is a
     * direction and never a distance is about where a trigger pull is pointed; a grenade has carried
     * a flight length since G0019 without troubling it, and this is the same number on a bullet - it
     * is what lets a gun be short-ranged rather than what lets an aim reach further.
     */
    public float range;

    /**
     * Whether it passes through anything alive rather than stopping at the first of it.
     *
     * <p>A flag on the shot rather than a question about which gun fired it, for the reason every
     * other difference between guns is data: the collision system asks what this shot does and never
     * what it came out of. What it does <em>not</em> pass through is scenery - rock, an obstacle, the
     * arena edge - which is the same test a thrown grenade already makes (G0019), so a wall stops a
     * slug exactly as it always did.
     */
    public boolean piercing;

    /** Records the muzzle, and returns this component, for fluent spawning. */
    public Projectile from(float x, float y) {
        this.originX = x;
        this.originY = y;
        return this;
    }

    /** Gives it a flight length, and returns this component, for fluent spawning. */
    public Projectile lasting(float newRange) {
        this.range = newRange;
        return this;
    }

    /** Makes it a shot that goes through things, and returns this component, for fluent spawning. */
    public Projectile piercing() {
        this.piercing = true;
        return this;
    }

    /** True if this shot has flown as far as it was ever going to. */
    public boolean spent(float x, float y) {
        if (range <= 0f) {
            return false;
        }
        float flownX = x - originX;
        float flownY = y - originY;
        return flownX * flownX + flownY * flownY >= range * range;
    }

    @Override
    public void reset() {
        originX = 0f;
        originY = 0f;
        range = 0f;
        piercing = false;
    }
}
