package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * A death reaction: this entity leaves a spatter of ruined ground where it fell.
 *
 * <p>The same shape as {@link BlastsOnDeath}, and for the same reason. <strong>Everything that
 * leaves something behind does so by dying</strong> (G0018), so a shot that bursts on a wall, one
 * that bursts on a trooper and one that simply runs out of flight all arrive at the same spatter
 * through the same reaction - there is no second path, and nothing that kills the shot needs to know
 * what it was carrying (G0004).
 */
public final class SlicksOnDeath implements Component, Pool.Poolable {

    /** How many circles it leaves. Zero is a shot that leaves nothing, which never carries this. */
    public int blobs;

    /** How wide each of them is, in world units. */
    public float radius;

    /** How far from here their centres may fall, in world units. */
    public float spread;

    /** How long the ground stays ruined, in seconds. */
    public float seconds;

    /** What touching one takes off. */
    public float damage;

    /** How long a man one of them puts down is protected from the rest of the spatter. */
    public float victimInvulnerableSeconds;

    /** Sets the reaction and returns this component, for fluent spawning. */
    public SlicksOnDeath set(
            int newBlobs,
            float newRadius,
            float newSpread,
            float newSeconds,
            float newDamage,
            float newVictimInvulnerableSeconds) {
        this.blobs = newBlobs;
        this.radius = newRadius;
        this.spread = newSpread;
        this.seconds = newSeconds;
        this.damage = newDamage;
        this.victimInvulnerableSeconds = newVictimInvulnerableSeconds;
        return this;
    }

    @Override
    public void reset() {
        blobs = 0;
        radius = 0f;
        spread = 0f;
        seconds = 0f;
        damage = 0f;
        victimInvulnerableSeconds = 0f;
    }
}
