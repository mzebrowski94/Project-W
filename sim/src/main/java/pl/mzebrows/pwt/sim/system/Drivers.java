package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Drag;
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
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Drag> DRAG = ComponentMapper.getFor(Drag.class);

    private Drivers() {}

    /**
     * Held still by a blow, dead on the ground, or down on it waiting for somebody. None of the
     * three drives itself: a stunned unit's velocity belongs to {@code StunSystem} until it comes
     * round, a corpse's belongs to nobody, and a casualty's belongs to whoever is hauling him. A
     * driver that zeroed any of them would silently delete knockback, or a drag.
     *
     * <p>The casualty case is the reason this is one question rather than sixteen copies of it. A
     * man who is down must stop shooting, stop thinking, stop holding ground and stop being steered,
     * and every one of those is a system that already asks here.
     */
    static boolean held(Entity entity) {
        if (STUNNED.has(entity) && STUNNED.get(entity).active()) {
            return true;
        }
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }

    /**
     * Whether this unit is hauling something, and therefore has nothing to fight with.
     *
     * <p>Asked by everything a commander does with his hands - firing, swinging, throwing - for the
     * same reason {@link #held} is asked by everything that drives one: three copies of the same
     * test is two chances for one of them to drift, and the drift would be a commander who could
     * somehow still throw a grenade with a man over his shoulder.
     */
    static boolean handsFull(Entity entity) {
        return DRAG.has(entity) && DRAG.get(entity).active();
    }

    /**
     * The first commander still on his feet, or null if none is.
     *
     * <p>A body on the ground is not a target: the pack stops rather than piling onto a corpse, which
     * is what makes the seconds after a kill read as a beat rather than a scrum.
     *
     * <p><strong>A man bleeding out is off his feet too</strong> (G0023). This one call is what "his
     * going down releases everything" is actually made of - the pack, the firing line and the squad
     * all ask it - so the moment he could be a casualty rather than a corpse, the release had to
     * cover both, or a rescue would be something the game showed you and never let you reach.
     */
    static @Nullable Position livingTarget(ImmutableArray<Entity> commanders) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (down(commander)) {
                continue;
            }
            return POSITION.get(commander);
        }
        return null;
    }

    /** Off his feet: on the ground bleeding, or on it for good. Narrower than {@link #held}. */
    static boolean down(Entity entity) {
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }
}
