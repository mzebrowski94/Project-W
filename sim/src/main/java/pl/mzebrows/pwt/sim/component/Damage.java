package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

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

    /** Adds to what is already owed, and to how long the victim recovers afterwards. */
    public void add(float extra, float recovery) {
        amount += extra;
        if (recovery > recoverySeconds) {
            recoverySeconds = recovery;
        }
    }

    /** True once a hit that claims a recovery window has landed this tick. */
    public boolean staggered() {
        return recoverySeconds > 0f;
    }

    @Override
    public void reset() {
        amount = 0f;
        recoverySeconds = 0f;
    }
}
