package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Turns what a unit wants into what it is doing: commits {@link Steering} to {@link Velocity}, once,
 * after every system that had something to say about it.
 *
 * <p>One writer at the end of the pipeline is what lets several systems have an opinion about where a
 * unit is going without any of them having to know the others exist. Deciding is upstream; this is
 * the only place a decision becomes motion.
 *
 * <p>It clears the desired velocity after committing it, so a unit no system spoke for this tick
 * stops rather than coasting. That single rule is what makes "stand still" the default and removes
 * the halt-everything branch every driver used to need.
 *
 * <p><strong>A held unit is left entirely alone</strong> - not driven, and not stopped either. A
 * stunned unit's velocity belongs to {@code StunSystem} and a knocked-back one is mid-flight; writing
 * a zero over either would delete the throw without failing anything that tests the throw.
 */
public final class DriveSystem extends IteratingSystem {

    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);

    public DriveSystem() {
        super(Family.all(Steering.class, Velocity.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Steering steering = STEERING.get(entity);
        if (!Drivers.held(entity)) {
            VELOCITY.get(entity).set(steering.desiredX, steering.desiredY);
        }
        steering.clear();
    }
}
