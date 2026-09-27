package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * The two questions every system that drives a unit has to answer first, answered once.
 *
 * <p>One copy, so no driver's gate can drift from the others.
 */
final class Drivers {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Drag> DRAG = ComponentMapper.getFor(Drag.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);

    private Drivers() {}

    /**
     * Held still by a blow, dead on the ground, down on it waiting for somebody, or in something's jaws.
     * None of them drives itself, and no driver may zero its velocity - that would delete knockback, a
     * drag or a carry.
     */
    static boolean held(Entity entity) {
        if (aboard(entity) || gripped(entity)) {
            return true;
        }
        if (STUNNED.has(entity) && STUNNED.get(entity).active()) {
            return true;
        }
        if (CASUALTY.has(entity) && CASUALTY.get(entity).begun) {
            return true;
        }
        return DYING.has(entity) && DYING.get(entity).begun;
    }

    /**
     * In something's jaws (G0045). Held like any other held unit, with one exception every caller that
     * cares asks for by name: <strong>he may still swing</strong>, because that is how a man gets out.
     */
    static boolean gripped(Entity entity) {
        return GRIPPED.has(entity) && GRIPPED.get(entity).held();
    }

    /**
     * Whether this unit is hauling something, and therefore has nothing to fight with.
     *
     * <p>Asked by everything a commander does with his hands.
     */
    static boolean handsFull(Entity entity) {
        return DRAG.has(entity) && DRAG.get(entity).active();
    }

    /**
     * The first commander still on his feet, or null if none is.
     *
     * <p><strong>A man bleeding out is off his feet too</strong> (G0023): this is what "his going down
     * releases everything" is made of.
     */
    static @Nullable Position livingTarget(ImmutableArray<Entity> commanders) {
        Entity standing = Players.standing(commanders);
        return standing == null ? null : POSITION.get(standing);
    }

    /**
     * On a gun rather than on the ground: {@code TurretSystem} owns him, and every other driver leaves
     * him alone.
     */
    static boolean aboard(Entity entity) {
        return CREW.has(entity) && CREW.get(entity).aboard();
    }

    /**
     * The nearest thing on the player's side still on its feet, or null if there is nothing left.
     *
     * <p><strong>The single writer for who an alien comes for</strong> (G0026). A man who is down is
     * not on the list. Ties go to the first spawned. A scan, not a grid query: the player's side is a
     * handful of entities.
     */
    static @Nullable Entity nearestLivingTarget(ImmutableArray<Entity> candidates, Position from) {
        @Nullable Entity nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Entity candidate = candidates.get(i);
            if (!huntable(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - from.x;
            float toY = where.y - from.y;
            float distance = toX * toX + toY * toY;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * Whether an alien would come for this: on the player's side, and still on its feet (G0026).
     *
     * <p>One question for both who is hunted and how to get there.
     */
    static boolean huntable(Entity entity) {
        return !down(entity) && FACTION.get(entity).side == Side.PLAYER;
    }

    /** Off his feet: on the ground bleeding, or on it for good. Narrower than {@link #held}. */
    static boolean down(Entity entity) {
        return Players.down(entity);
    }
}
