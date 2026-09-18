package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Weave;

/**
 * Bends a course it did not choose: whatever a weaving creature was about to walk at, it walks at
 * slightly off to one side, and then slightly off to the other.
 *
 * <p><strong>It turns the course rather than adding to it</strong>, so the creature travels at
 * exactly the speed something else asked for. Adding a sideways velocity would make a weaving unit
 * faster than a straight one, which would be a free upgrade rather than a harder shot.
 *
 * <p>The phase advances with ground covered rather than with the clock, so a creature stopped by a
 * wall stops weaving and one slowed down weaves no faster (G0012's argument, applied to a course
 * instead of to a sidestep). It reads the desired velocity for that, because that is where it is
 * about to go: everything that decides an alien's course has already run and nothing has moved yet.
 *
 * <p>It runs immediately after {@code NavigationSystem}, which is what writes the course it bends,
 * and well before {@code AvoidanceSystem} - a weave that could steer a creature into rock would be a
 * weave that stops being a weave the moment a level has walls in it. Avoidance still has the last
 * word, and the dodge after it overrides both.
 *
 * <p>A unit that asked for nothing is left alone: weaving on the spot is not a weave.
 */
public final class WeaveSystem extends IteratingSystem {

    private static final ComponentMapper<Weave> WEAVE = ComponentMapper.getFor(Weave.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);

    private static final float EPSILON = 1e-4f;

    public WeaveSystem() {
        super(Family.all(Weave.class, Steering.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Weave weave = WEAVE.get(entity);
        if (!weave.active() || Drivers.held(entity)) {
            return;
        }
        Steering steering = STEERING.get(entity);
        float speed = (float) Math.sqrt(steering.desiredX * steering.desiredX + steering.desiredY * steering.desiredY);
        if (speed <= EPSILON) {
            return;
        }
        weave.travelled += speed * deltaTime;
        float forwardX = steering.desiredX / speed;
        float forwardY = steering.desiredY / speed;
        float lean = weave.lean() * weave.amplitude;
        // Perpendicular to where it was going, then renormalised: the course turns and the pace does
        // not change, which is what keeps a weave a harder shot rather than a faster creature.
        float bentX = forwardX - forwardY * lean;
        float bentY = forwardY + forwardX * lean;
        float length = (float) Math.sqrt(bentX * bentX + bentY * bentY);
        if (length <= EPSILON) {
            return;
        }
        steering.set(bentX / length * speed, bentY / length * speed);
    }
}
