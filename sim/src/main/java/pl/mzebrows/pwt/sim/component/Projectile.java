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
     * How many more times it comes off what it hits rather than stopping there. Zero is a shot that stops
     * at the first thing, which is what every gun but the disc gun fires.
     */
    public int bouncesLeft;

    /**
     * Whether it lands its blow on a shut door it flies into (G0069): a heavy gun's shot. Every other shot
     * stops at a door as at rock and leaves it whole - a door is opened, not shot open.
     */
    public boolean breaksDoors;

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

    /** Lets it come off this many things before it stops, and returns this component, for fluent spawning. */
    public Projectile bouncing(int bounces) {
        this.bouncesLeft = bounces;
        return this;
    }

    /** Makes it a shot that breaks doors, and returns this component, for fluent spawning. */
    public Projectile breakingDoors(boolean breaks) {
        this.breaksDoors = breaks;
        return this;
    }

    /** True if the next thing it hits turns it rather than stops it. */
    public boolean bounces() {
        return bouncesLeft > 0;
    }

    /**
     * Spends one bounce, made at {@code (x, y)}. The leg it flew to get there comes off its reach, and the
     * bounce becomes where it was fired from - so {@link #spent} measures the flight along every leg rather
     * than as the crow flies, and a blow it lands next came from the last thing it came off.
     */
    public void bounceAt(float x, float y) {
        bouncesLeft--;
        if (range > 0f) {
            float legX = x - originX;
            float legY = y - originY;
            // Never down to zero, which would be "until it hits something": a reach used up is a reach used up.
            range = Math.max(range - (float) Math.sqrt(legX * legX + legY * legY), Float.MIN_VALUE);
        }
        originX = x;
        originY = y;
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
        bouncesLeft = 0;
        breaksDoors = false;
    }
}
