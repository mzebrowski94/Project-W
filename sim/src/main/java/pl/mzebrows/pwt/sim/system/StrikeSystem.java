package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strike;

/**
 * Sweeps a swing through its arc and takes it away when it is done.
 *
 * <p>Runs after movement and before collision, so the sweep is where it should be for this tick by
 * the time contacts are found. It writes a position directly rather than a velocity because it
 * travels on an arc, and a velocity integrated in a straight line for a tick would cut the corner.
 */
public final class StrikeSystem extends IteratingSystem {

    private static final ComponentMapper<Strike> STRIKE = ComponentMapper.getFor(Strike.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private final Spawner spawner;

    public StrikeSystem(Spawner spawner) {
        super(Family.all(Strike.class, Position.class).get());
        this.spawner = spawner;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Strike strike = STRIKE.get(entity);
        strike.elapsedSeconds += deltaTime;
        if (strike.spent()) {
            spawner.markDead(entity);
            return;
        }
        float angle = strike.angle();
        POSITION.get(entity)
                .set(
                        strike.originX + (float) StrictMath.cos(angle) * strike.reach,
                        strike.originY + (float) StrictMath.sin(angle) * strike.reach);
    }
}
