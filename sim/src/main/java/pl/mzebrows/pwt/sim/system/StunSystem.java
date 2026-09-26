package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Bleeds the knockback out of anything being held still.
 *
 * <p>A stunned entity's own drivers - control, chase, leap - all stand down, so nothing is writing
 * its velocity while the stun runs. Left alone it would coast on whatever it was doing when the blow
 * landed, or on the throw the blow gave it, for the whole stun and then some. This is the only thing
 * that slows it down.
 *
 * <p>Linear decay, so a throw visibly stops at exactly zero. A blow that throws nothing stops its
 * victim at once.
 */
public final class StunSystem extends IteratingSystem {

    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);

    public StunSystem() {
        super(Family.all(Stunned.class, Velocity.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Stunned stunned = STUNNED.get(entity);
        if (!stunned.active()) {
            return;
        }
        Velocity velocity = VELOCITY.get(entity);
        if (stunned.knockbackDecay <= 0f) {
            velocity.set(0f, 0f);
            return;
        }
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        if (speed <= 0f) {
            return;
        }
        float reduced = speed - stunned.knockbackDecay * deltaTime;
        if (reduced <= 0f) {
            velocity.set(0f, 0f);
            return;
        }
        float scale = reduced / speed;
        velocity.set(velocity.x * scale, velocity.y * scale);
    }
}
