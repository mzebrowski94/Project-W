package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.system.combat.Guns;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Sending everything within earshot to see who is at a place, in one place, for everything that
 * shouts.
 *
 * <p>Two things do: a control point somebody has started taking, and a nest somebody is hitting.
 * Both mean the same thing to a creature - <strong>there is trouble over there</strong> - and a
 * provocation is a place rather than a target (G0016), so neither of them needs to know who caused
 * it. The same argument {@link Drivers} and {@link Guns} make for their own questions: two copies of
 * this is one chance for them to drift, and the drift would be a nest that woke the level in a way a
 * point did not.
 *
 * <p>It is loud <strong>once per event</strong> rather than every tick. Re-latching constantly would
 * pin everything within earshot to the place for ever and override any other grudge it had, and once
 * is all it takes for them to come and look. Deciding what counts as one event is the caller's.
 */
final class Rouse {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);

    private Rouse() {}

    /**
     * Provokes every watcher inside the radius towards the place.
     *
     * <p>A zero or negative radius shouts at nobody, which is how a level switches the behaviour off
     * - the codebase's usual way of saying "not for me". Anything held still is skipped: a stunned
     * creature or one on the ground is not going anywhere about it.
     */
    static void within(ImmutableArray<Entity> watchers, float x, float y, float radius) {
        if (radius <= 0f) {
            return;
        }
        float reach = radius * radius;
        for (int i = 0; i < watchers.size(); i++) {
            Entity watcher = watchers.get(i);
            if (Drivers.held(watcher)) {
                continue;
            }
            Position where = POSITION.get(watcher);
            float toX = where.x - x;
            float toY = where.y - y;
            if (toX * toX + toY * toY <= reach) {
                PERCEPTION.get(watcher).provokeAt(x, y);
            }
        }
    }
}
