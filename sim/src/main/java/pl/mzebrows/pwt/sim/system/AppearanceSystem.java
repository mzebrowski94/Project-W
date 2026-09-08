package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Stunned;

/**
 * Publishes what each entity is doing, so the renderer can show it without asking (G0003, pillar 5).
 *
 * <p>Every state an entity can be in is derived here, in one place and in one order, rather than
 * written by whichever system happened to cause it. Two systems writing the same field is how a unit
 * ends up drawn as winding up while it is actually stunned - which would be worse than no tell at
 * all, because the player would have learned to trust it.
 *
 * <p>The precedence is worst-news-first, with one deliberate exception at the top. <strong>Being
 * locked outranks being dead.</strong> Both parties to a landed blow read as {@code GRAPPLING} for the
 * whole lock, the loser included, so that for that beat the player cannot tell from the picture which
 * of them is getting back up - only when the lock ends does the loser fail to stand.
 *
 * <p>That is a knowing exception to pillar 5, and it is narrow: it lasts exactly as long as the lock,
 * it applies only to an entity that has just been in an exchange, and everything else about the two
 * is still on screen. A death with no exchange behind it - anything shot - reads as {@code DYING}
 * immediately, as it should.
 */
public final class AppearanceSystem extends IteratingSystem {

    private static final ComponentMapper<Appearance> APPEARANCE = ComponentMapper.getFor(Appearance.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);

    public AppearanceSystem() {
        super(Family.all(Appearance.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        APPEARANCE.get(entity).state = stateOf(entity);
    }

    private static Appearance.State stateOf(Entity entity) {
        if (STUNNED.has(entity) && STUNNED.get(entity).active()) {
            return STUNNED.get(entity).grappling ? Appearance.State.GRAPPLING : Appearance.State.STAGGERED;
        }
        if (DYING.has(entity) && DYING.get(entity).begun) {
            return Appearance.State.DYING;
        }
        if (LEAP.has(entity)) {
            Leap leap = LEAP.get(entity);
            if (leap.airborne()) {
                return Appearance.State.AIRBORNE;
            }
            if (leap.phase == Leap.Phase.HESITATING) {
                return Appearance.State.WINDING_UP;
            }
        }
        return Appearance.State.NORMAL;
    }
}
