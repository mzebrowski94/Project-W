package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;

/**
 * The two questions every system that drives a unit has to answer first, answered once.
 *
 * <p>Both were copied between the chase and the leap while there were two of them. There are more
 * now, and a gate that has drifted in one of five copies is a unit that keeps walking while it is
 * stunned - which breaks knockback without breaking any test that is about knockback.
 */
final class Drivers {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    private Drivers() {}

    /**
     * Held still by a blow, or dead on the ground. Neither drives itself: a stunned unit's velocity
     * belongs to {@code StunSystem} until it comes round, and a corpse's belongs to nobody. A driver
     * that zeroed either would silently delete knockback.
     */
    static boolean held(Entity entity) {
        if (STUNNED.has(entity) && STUNNED.get(entity).active()) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }

    /**
     * The first commander still on his feet, or null if none is.
     *
     * <p>A body on the ground is not a target: the pack stops rather than piling onto a corpse, which
     * is what makes the seconds after a kill read as a beat rather than a scrum.
     */
    static @Nullable Position livingTarget(ImmutableArray<Entity> commanders) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (DYING.has(commander) && DYING.get(commander).begun) {
                continue;
            }
            return POSITION.get(commander);
        }
        return null;
    }
}
