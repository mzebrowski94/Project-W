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
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.def.Side;

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
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);

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
        if (aboard(entity)) {
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
        Entity standing = Players.standing(commanders);
        return standing == null ? null : POSITION.get(standing);
    }

    /**
     * On a gun rather than on the ground. {@code TurretSystem} owns where he is and what he fires
     * for as long as he is, so every other driver leaves him alone - the same answer {@link #held}
     * gives for a man who is stunned or down, and for the same reason: one question, in one place.
     */
    static boolean aboard(Entity entity) {
        return CREW.has(entity) && CREW.get(entity).aboard();
    }

    /**
     * The nearest thing on the player's side still on its feet, or null if there is nothing left.
     *
     * <p><strong>The single writer for who an alien comes for</strong> (G0026). It used to be
     * {@link #livingTarget}, which answered "the commander", and the difference is one line here
     * rather than a targeting rule in each of the three systems that ask - which is exactly how the
     * old rule stayed consistent and how this one has to.
     *
     * <p>A man who is down is not on the list, whatever put him there. That is what keeps a rescue
     * reachable: a pack that stood over a body would finish it before anybody could cross the ground.
     *
     * <p>Ties go to whichever was spawned first, because the family is in insertion order and the
     * comparison is strict - so two men at exactly equal distance pull the same way on every replay.
     *
     * <p>A scan rather than a neighbour query: the player's side is a commander, a handful of men and
     * whatever guns a level put down. The collision grid exists for everything against everything,
     * which this is not.
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
     * <p>Asked by the system that decides <em>who</em> is hunted and by the one that works out
     * <em>how to get there</em>, which is why it is one question rather than two copies. A man who is
     * down being on one list and off the other is how a pack ends up routing to a corpse.
     */
    static boolean huntable(Entity entity) {
        return !down(entity) && FACTION.get(entity).side == Side.PLAYER;
    }

    /** Off his feet: on the ground bleeding, or on it for good. Narrower than {@link #held}. */
    static boolean down(Entity entity) {
        return Players.down(entity);
    }
}
