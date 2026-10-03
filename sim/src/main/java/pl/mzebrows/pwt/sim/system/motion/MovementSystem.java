package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;

/** Integrates velocity into position once per fixed step. */
public final class MovementSystem extends IteratingSystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);

    public MovementSystem() {
        super(Family.all(Position.class, Velocity.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Position position = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        position.x += velocity.x * deltaTime;
        position.y += velocity.y * deltaTime;
    }
}
