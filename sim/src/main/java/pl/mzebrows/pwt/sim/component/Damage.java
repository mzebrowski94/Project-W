package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;
import org.jspecify.annotations.Nullable;

/**
 * Damage owed to an entity this tick, waiting to be applied.
 *
 * <p>It accumulates rather than being written, and that is the point: two projectiles landing in the
 * same tick both count, and the result does not depend on which pair the collision grid happened to
 * visit first. Nothing subtracts from health at the moment of contact - contact only adds here.
 *
 * <p>It also carries the recovery window the hit asks for, rather than the attacker granting that at
 * the moment of contact. Granting it there would make the victim invulnerable to the very hit that
 * caused it, since nothing is applied until later in the tick - a mistake worth naming, because it
 * looks correct and simply makes contact damage silently stop working.
 *
 * <p>The knockback rides here for a different and equally load-bearing reason: <strong>only a target
 * that survives is thrown.</strong> Contact resolution cannot know whether it survives, because
 * nothing has touched its health yet - so it records the shove it would like to give and
 * {@code DamageSystem}, which does know, either applies it or does not. A blow that kills leaves the
 * body exactly where it was struck, which is what makes a lethal answer to a leap read as a clinch
 * rather than as a body being punted away.
 *
 * <p>That window doubles as the "already staggered this tick" flag. A hit that grants recovery is
 * exclusive: once one has landed, no second staggering hit lands in the same tick, which is what
 * stops a crowd deleting a unit instantly. Hits that grant no recovery - projectiles - are not
 * exclusive, so rapid fire is never wasted.
 *
 * <p>Every entity with {@link Health} carries one of these permanently, zeroed after each
 * application. Adding and removing it instead would mean mutating a family mid-iteration, which is
 * how entities get silently skipped.
 */
public final class Damage implements Component, Pool.Poolable {

    public float amount;

    /** The longest recovery window any of this tick's hits asked for. Applied once the damage is. */
    public float recoverySeconds;

    /** Unit direction of the shove owed, pointing away from whoever swung. */
    public float knockbackX;

    public float knockbackY;

    /** How hard, in world units per second. Zero means no shove was asked for. */
    public float knockbackSpeed;

    /**
     * Who gets held still if this damage proves fatal - the one who landed the blow. Null when the
     * blow carries no lock, or when nothing owning it survives to be held.
     *
     * <p>The second entity reference in a pooled component in this codebase, after {@code Strike}.
     * {@link #reset()} clearing it is not optional: a stale one here would keep a dead commander
     * reachable from the pool for as long as the game ran.
     */
    public @Nullable Entity locksOnDeath;

    public float deathLockSeconds;

    /** How fast this body finishes its lunge into whoever killed it, if the blow proves fatal. */
    public float clinchCarrySpeed;

    public float clinchCarryDecay;

    /**
     * Where the hit came from, in world units - the muzzle a shot was fired from, or the attacker
     * itself for a blow. Meaningless unless {@link #hasSource}.
     *
     * <p>A place rather than whoever landed it (G0016). Contact resolution records it and
     * {@code DamageSystem} decides what it provokes, which is the same division of labour as the
     * damage itself: what is owed here, what it costs later.
     */
    public float sourceX;

    public float sourceY;

    /** Whether anything this tick said where it came from. */
    public boolean hasSource;

    /**
     * Whether any of this tick's hits was the kind that kills outright rather than leaving a
     * casualty.
     *
     * <p>Fatal wins when hits combine, exactly as the longest recovery window does. A man caught by
     * a blast and a set of claws in the same tick is dead: the merciful reading would let a unit
     * survive a killing blow because a bullet happened to land alongside it, which would make the
     * rule "it depends what else hit you" rather than "claws kill".
     *
     * <p>It is a property of the <em>source</em>, not of the amount. That is what keeps a new damage
     * source from having to know that casualties exist - it says what kind of thing it is once, at
     * the point the blow is recorded, and {@code DamageSystem} does the rest.
     */
    public boolean fatal;

    /**
     * Records where a hit came from, keeping the <strong>first</strong> of the tick.
     *
     * <p>First rather than last so that two attackers landing in one tick resolve the same way on
     * every replay, and so that what provokes a unit is the hit that reached it soonest rather than
     * whichever pair the collision grid happened to visit last.
     */
    public void cameFrom(float x, float y) {
        if (hasSource) {
            return;
        }
        hasSource = true;
        sourceX = x;
        sourceY = y;
    }

    /**
     * Adds a blow that kills outright, and to how long the victim recovers afterwards.
     *
     * <p>The shorter call is the killing one because that is what almost everything in the game
     * does - claws, a sweep, a leap - and because it is what every caller meant before there was a
     * choice. A source that leaves a casualty says so explicitly.
     */
    public void add(float extra, float recovery) {
        add(extra, recovery, true);
    }

    /**
     * Adds to what is already owed, saying whether this blow kills outright or leaves a casualty.
     *
     * <p>{@code kills} is a property of the weapon rather than of the damage: a sidearm round that
     * takes a man to zero puts him on the ground, and claws that take him to zero finish him, and
     * the numbers may be identical.
     */
    public void add(float extra, float recovery, boolean kills) {
        amount += extra;
        if (recovery > recoverySeconds) {
            recoverySeconds = recovery;
        }
        fatal |= kills;
    }

    /** Records a shove, keeping the hardest asked for this tick. Applied only if the target lives. */
    public void push(float directionX, float directionY, float speed) {
        if (speed <= knockbackSpeed) {
            return;
        }
        knockbackX = directionX;
        knockbackY = directionY;
        knockbackSpeed = speed;
    }

    /** True once a hit that claims a recovery window has landed this tick. */
    public boolean staggered() {
        return recoverySeconds > 0f;
    }

    /** True if something asked to throw this entity this tick. */
    public boolean pushed() {
        return knockbackSpeed > 0f;
    }

    /** Records who to hold still if this kills, keeping the longest lock asked for this tick. */
    public void lockOnDeath(Entity attacker, float seconds, float carrySpeed, float carryDecay) {
        if (seconds <= deathLockSeconds) {
            return;
        }
        locksOnDeath = attacker;
        deathLockSeconds = seconds;
        clinchCarrySpeed = carrySpeed;
        clinchCarryDecay = carryDecay;
    }

    @Override
    public void reset() {
        amount = 0f;
        recoverySeconds = 0f;
        knockbackX = 0f;
        knockbackY = 0f;
        knockbackSpeed = 0f;
        locksOnDeath = null;
        deathLockSeconds = 0f;
        clinchCarrySpeed = 0f;
        clinchCarryDecay = 0f;
        sourceX = 0f;
        sourceY = 0f;
        hasSource = false;
        fatal = false;
    }
}
