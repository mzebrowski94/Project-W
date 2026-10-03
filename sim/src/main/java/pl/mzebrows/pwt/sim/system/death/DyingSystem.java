package pl.mzebrows.pwt.sim.system.death;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Dying;

/**
 * Takes away a body once it has lain on the field long enough.
 *
 * <p>The whole of the difference between dying and being dead: {@code DamageSystem} starts the throes
 * when health runs out, this marks the entity dead when they run out, and the reaper removes it at
 * the end of that tick. Every existing death reaction still runs in its usual place, once, because
 * {@code Dead} still means exactly what it always meant.
 *
 * <p>It runs before the reactions rather than after, so the tick that ends the throes is also the
 * tick the respawn countdown opens on - a body does not linger and then pause again before anything
 * happens.
 */
public final class DyingSystem extends IteratingSystem {

    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    private final Spawner spawner;

    public DyingSystem(Spawner spawner) {
        super(Family.all(Dying.class).get());
        this.spawner = spawner;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (DYING.get(entity).spent()) {
            spawner.markDead(entity);
        }
    }
}
