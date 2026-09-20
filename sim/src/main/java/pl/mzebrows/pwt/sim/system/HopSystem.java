package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Hop;
import pl.mzebrows.pwt.sim.component.Steering;

/**
 * Spends a course in bursts: a creature that hops travels in jumps with a sit between them rather
 * than at a walk.
 *
 * <p><strong>It paces a course; it never chooses one.</strong> Everything that decides where a unit
 * is going has run by the time this does, and all this does is scale what they asked for - up while
 * it is in the air, to nothing while it is sitting. Over one whole cycle it covers exactly what a
 * walk would, which is what keeps {@code speed} on a strain meaning the same thing for a frog as for
 * a grunt: the gait is a different-looking journey, not a faster or a slower one.
 *
 * <p>It runs after {@code EnemyRangedSystem}, so a shooter that has planted itself is scaled from
 * nothing and stays planted, and <strong>before {@code DodgeSystem}</strong>, so a dodge overwrites
 * the gait outright. A sidestep from a shot it saw coming is a commitment and must not land in the
 * middle of a sit (G0012); being mid-hop is not a reason to be unable to get out of the way.
 *
 * <p>The clock advances whether or not the creature is going anywhere and whether or not something
 * is holding it still, because it is a gait rather than a distance - the one place this differs from
 * a weave, and the reason is in {@code HopDef}.
 *
 * <p>A unit that asked to go nowhere is left where it is: multiplying nothing is still nothing, and
 * a sit that a driver did not ask for is not this system's to invent.
 */
public final class HopSystem extends IteratingSystem {

    private static final ComponentMapper<Hop> HOP = ComponentMapper.getFor(Hop.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);

    public HopSystem() {
        super(Family.all(Hop.class, Steering.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Hop hop = HOP.get(entity);
        if (!hop.active()) {
            return;
        }
        // Read, then advance. A creature spawned at the start of its cycle hops on its first tick
        // rather than on its second, and a whole cycle is exactly as many airborne ticks as the
        // fraction says - which is what makes the ground covered match a walk's to within a tick.
        float pace = hop.pace();
        hop.advance(deltaTime);
        if (Drivers.held(entity)) {
            return;
        }
        Steering steering = STEERING.get(entity);
        steering.set(steering.desiredX * pace, steering.desiredY * pace);
    }
}
