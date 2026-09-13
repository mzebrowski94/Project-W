package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * What a killing blow does to a body, in one place.
 *
 * <p>There are two ways to run out of life now - a blow that finishes you, and a bleed-out nobody
 * answered - and they must end identically, or a man who was left on the ground would leave a
 * different corpse from one who was shot in the head. Copying the four steps into
 * {@code CasualtySystem} is exactly the drift {@link Drivers} exists to prevent, one class along.
 *
 * <p>Nothing here removes an entity. The gap between being marked and being reaped is where the
 * death reactions run (G0004), and this only ever opens it.
 */
final class Deaths {

    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private Deaths() {}

    /**
     * A killing blow: either the entity goes now, or it takes its time about it.
     *
     * <p>A body that lingers becomes scenery - solid, and immovable like an obstacle. Immovable is
     * the half that matters for the clinch: the solver stops trying to shuffle the corpse and pushes
     * the living party out instead, so the two settle exactly touching rather than being prised
     * apart or left overlapping.
     *
     * <p>It does not stun the body. If the killing blow carried a lock, contact resolution has
     * already stunned it and that stun is what makes the pair read as locked together; if it did not
     * - anything shot - the body has nothing to hide and reads as dead at once. What keeps a corpse
     * from still chasing is that the drivers treat dying as inert in its own right, not a stun
     * standing in for it.
     */
    static void kill(Spawner spawner, Entity entity) {
        if (VELOCITY.has(entity)) {
            VELOCITY.get(entity).set(0f, 0f);
        }
        if (!DYING.has(entity)) {
            spawner.markDead(entity);
            return;
        }
        Dying throes = DYING.get(entity);
        if (throes.begun) {
            return;
        }
        throes.begin();
        if (COLLIDER.has(entity)) {
            COLLIDER.get(entity).set(true, false);
        }
        if (INVULNERABLE.has(entity)) {
            INVULNERABLE.get(entity).grant(throes.throesSeconds);
        }
    }
}
