package pl.mzebrows.pwt.sim.system.death;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.Dead;

/**
 * Removes everything marked dead, and is the only thing in the simulation that removes an entity at
 * all.
 *
 * <p>That single rule is what makes death reactions possible. Anything that kills only marks; the
 * whole of the rest of the tick can still see what died and act on it; and this runs last, once, so
 * no system ever finds an entity vanished from under it mid-tick.
 */
public final class ReaperSystem extends EntitySystem {

    private ImmutableArray<Entity> dead = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        dead = engine.getEntitiesFor(Family.all(Dead.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = dead.size() - 1; i >= 0; i--) {
            getEngine().removeEntity(dead.get(i));
        }
    }
}
