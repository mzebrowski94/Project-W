package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A fired entity that despawns when it hits something or leaves the arena.
 *
 * <p>It carries <strong>where it was fired from</strong>, not who fired it: a place to walk at
 * (G0016).
 */
public final class Projectile implements Component, Pool.Poolable {

    /** Where this shot was fired from, in world units. */
    public float originX;

    public float originY;

    /**
     * How far it may fly before it expires, in world units. Zero is until it hits something or
     * leaves the level, which is what almost every shot in the game declares.
     *
     * <p>A property of the shot, not of the aim.
     */
    public float range;

    /**
     * Whether it passes through anything alive rather than stopping at the first of it.
     *
     * <p>Scenery still stops it (G0019).
     */
    public boolean piercing;

    /**
     * How far back along its own path this shot is drawn, in world units. Zero is a dot, which is
     * what every gun but the beam declares.
     *
     * <p>A drawing number (pillar 5); nothing in the simulation reads it.
     */
    public float trailLength;

    /** Records the muzzle, and returns this component, for fluent spawning. */
    public Projectile from(float x, float y) {
        this.originX = x;
        this.originY = y;
        return this;
    }

    /** Draws this as a line back along its own path rather than as a dot. */
    public Projectile trailing(float newTrailLength) {
        this.trailLength = newTrailLength;
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
        trailLength = 0f;
        originX = 0f;
        originY = 0f;
        range = 0f;
        piercing = false;
    }
}
