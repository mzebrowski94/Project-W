package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A gun on a mount: where it is pointing, how fast it comes round, and whether anybody is on it.
 *
 * <p>What makes it a turret rather than a gun somebody is carrying is that it has to be brought to
 * bear. A carried gun fires where its owner is pointing on the tick he points there; this one swings
 * onto a bearing at a rate, and swings slower while it is firing - so getting round the side of one
 * is a real move, and holding the trigger costs you the ability to track.
 *
 * <p>A man aboard names it by its {@link Handle}, never by a reference to it (G0010). {@link #crewed}
 * is the other direction and is
 * <em>derived</em>: {@code TurretSystem} clears it and re-establishes it from whoever is actually
 * aboard, every tick, so nothing that dies can leave a gun looking manned.
 *
 * <p>The aim is a unit vector rather than an angle for the reason every direction in this module is:
 * an angle would have to be turned with {@code Math.sin} and {@code Math.cos}, which are banned here
 * because they are specified only to within an ulp and may differ between JVMs (pillar 6).
 */
public final class Turret implements Component, Pool.Poolable {

    /** Whether anybody is on it. Recomputed every tick from the riders; never trusted between ticks. */
    public boolean crewed;

    /** Where the gun points, as a unit vector. */
    public float aimX = 1f;

    public float aimY;

    /**
     * How fast it comes round with the trigger off, as the fraction of the angle left it covers per
     * second. Zero means it is always already pointing at you.
     */
    public float turnRate;

    /** What is left of that rate while it is firing, in {@code (0, 1]}. */
    public float firingTurnFactor;

    /**
     * How far it will shoot, in world units.
     *
     * <p>Much further than a man can see for himself, which is most of what a turret is for: a
     * trooper on one answers things that are still crossing the ground towards the squad.
     */
    public float range;

    /** How long with nothing in that range before a crew of its own judgement gets off it. */
    public float quietSeconds;

    /** Sets what this gun is and returns this component, for fluent spawning. */
    public Turret set(float newTurnRate, float newFiringTurnFactor, float newRange, float newQuiet) {
        this.turnRate = newTurnRate;
        this.firingTurnFactor = newFiringTurnFactor;
        this.range = newRange;
        this.quietSeconds = newQuiet;
        this.crewed = false;
        this.aimX = 1f;
        this.aimY = 0f;
        return this;
    }

    /** Points the gun along a direction that is already a unit vector. */
    public void aim(float x, float y) {
        this.aimX = x;
        this.aimY = y;
    }

    /** How fast it may come round this tick. Firing is what costs it the ability to track. */
    public float turnRateFor(boolean firing) {
        return firing ? turnRate * firingTurnFactor : turnRate;
    }

    @Override
    public void reset() {
        crewed = false;
        aimX = 1f;
        aimY = 0f;
        turnRate = 0f;
        firingTurnFactor = 0f;
        range = 0f;
        quietSeconds = 0f;
    }
}
