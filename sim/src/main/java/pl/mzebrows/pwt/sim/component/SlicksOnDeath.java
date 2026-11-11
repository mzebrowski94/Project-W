package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.SlickDef;

/**
 * A death reaction: this entity leaves a spatter of ruined ground where it fell.
 *
 * <p>The same shape as {@link BlastsOnDeath}, and for the same reason. <strong>Everything that
 * leaves something behind does so by dying</strong> (G0018), so a shot that bursts on a wall, one
 * that bursts on a trooper and one that simply runs out of flight all arrive at the same spatter
 * through the same reaction - there is no second path, and nothing that kills the shot needs to know
 * what it was carrying (G0004).
 *
 * <p><strong>A blast carries one too</strong> (G0035). An explosion is a thing that dies like any
 * other, so fire is what its death leaves - which is why nothing that explodes had to learn that
 * fire exists, and why the flamethrower, which leaves no blast, leaves no fire either.
 */
public final class SlicksOnDeath implements Component, Pool.Poolable {

    /** How many circles it leaves. Zero is a death that leaves nothing; only an alien's
     * own blood carries this at zero, for the swarm's toxic blood to add to (G0076). */
    public int blobs;

    /** How wide each of them gets at its widest, in world units. */
    public float radius;

    /** How far from here their centres may fall, in world units. */
    public float spread;

    /** The shortest one of them stays on the ground, in seconds. */
    public float seconds;

    /** The longest one of them stays, in seconds. Equal to {@link #seconds} means they all match. */
    public float secondsMax;

    /** What touching one takes off. */
    public int damage;

    /** How long a man one of them puts down is protected from the rest of the spatter. */
    public float victimInvulnerableSeconds;

    /** How much of its life a circle spends swelling, as a fraction. */
    public float growFraction;

    /** How much of its life it spends collapsing again, as a fraction. */
    public float fadeFraction;

    /** What the renderer draws each circle as. */
    public Look look = Look.NONE;

    /** How hard a man one wounds is thrown off it, in world units per second (G0053). */
    public float knockbackSpeed;

    /** What touching one does to a man (G0053). */
    public Harm harm = Harm.NONE;

    /** How much of his speed whoever stands in one keeps; one is no slow (G0073). */
    public float slowFactor = 1f;

    /** How long that lasts after he steps out, in seconds. */
    public float slowSeconds;

    /** The chance a man it could catch gives one spatter of it room (G0076); one is always. */
    public float heeded = 1f;

    /**
     * Whether {@link #blobs} is the dier's own - an alien's blood - rather than its gun's or its blast's, so the
     * swarm's toxic blood adds to it when it dies (G0076). A declaration, so the death reaction never asks what died.
     */
    public boolean bodily;

    /** Sets the reaction from the definition of what this leaves, for fluent spawning. */
    public SlicksOnDeath set(SlickDef def) {
        this.blobs = def.blobs();
        this.radius = def.radius();
        this.spread = def.spread();
        this.seconds = def.seconds();
        this.secondsMax = def.secondsMax();
        this.damage = def.damage();
        this.victimInvulnerableSeconds = def.victimInvulnerableSeconds();
        this.growFraction = def.growFraction();
        this.fadeFraction = def.fadeFraction();
        this.look = def.look();
        this.knockbackSpeed = def.knockbackSpeed();
        this.harm = def.harm();
        this.slowFactor = def.slowFactor();
        this.slowSeconds = def.slowSeconds();
        this.heeded = def.heeded();
        this.bodily = false;
        return this;
    }

    /** Says these are the dier's own circles, {@code own} of them before the swarm's toxic blood (G0076). */
    public SlicksOnDeath bodily(int own) {
        this.blobs = own;
        this.bodily = true;
        return this;
    }

    @Override
    public void reset() {
        blobs = 0;
        radius = 0f;
        spread = 0f;
        seconds = 0f;
        secondsMax = 0f;
        damage = 0;
        victimInvulnerableSeconds = 0f;
        growFraction = 0f;
        fadeFraction = 0f;
        look = Look.NONE;
        knockbackSpeed = 0f;
        harm = Harm.NONE;
        slowFactor = 1f;
        slowSeconds = 0f;
        heeded = 1f;
        bodily = false;
    }
}
