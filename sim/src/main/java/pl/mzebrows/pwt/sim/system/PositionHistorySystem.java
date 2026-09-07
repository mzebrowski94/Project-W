package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;

/**
 * Records where everything was before the tick moves it, so rendering can interpolate.
 *
 * <p>Registered first, and the reason is worth stating: a position is written by movement, then by
 * the constraint solver, and possibly again by a future system. Only a copy taken before any of that
 * is a true "where it was", and the only place guaranteed to be before all of them is the front of
 * the tick.
 */
public final class PositionHistorySystem extends IteratingSystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);

    public PositionHistorySystem() {
        super(Family.all(Position.class, PreviousPosition.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Position position = POSITION.get(entity);
        PREVIOUS.get(entity).set(position.x, position.y);
    }
}
