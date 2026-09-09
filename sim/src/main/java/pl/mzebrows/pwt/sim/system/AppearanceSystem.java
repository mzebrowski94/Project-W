package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Publishes what each entity is doing, so the renderer can show it without asking (G0003, pillar 5).
 *
 * <p>Every state an entity can be in is derived here, in one place and in one order, rather than
 * written by whichever system happened to cause it. Two systems writing the same field is how a unit
 * ends up drawn as winding up while it is actually stunned - which would be worse than no tell at
 * all, because the player would have learned to trust it.
 *
 * <p>The precedence is worst-news-first, with one deliberate exception at the top. <strong>A
 * <em>fatal</em> lock outranks being dead</strong>, and only a fatal one: something swatted while it
 * walked is stunned as it dies and reads as dying, because the player has no reason to be in suspense
 * about it. Both parties to a landed blow read as {@code GRAPPLING} for the
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
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);
    private static final ComponentMapper<Strain> STRAIN = ComponentMapper.getFor(Strain.class);

    private final Defs<UnitKind, UnitDef> units;

    public AppearanceSystem(SimConfig config) {
        super(Family.all(Appearance.class).get());
        this.units = config.units();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Appearance appearance = APPEARANCE.get(entity);
        appearance.state = stateOf(entity);
        appearance.charge = chargeOf(entity);
    }

    /**
     * How close a unit is to turning into something else, in {@code [0, 1]}.
     *
     * <p>Pillar 5 says the player never tracks a number the game does not show, and how much an
     * enemy has eaten is exactly such a number. Published as a fraction rather than as a count so the
     * renderer needs to know nothing about strains or thresholds - and so that anything else that
     * fills up can use the same channel later.
     *
     * <p>A strain with nowhere to grow reports nothing, which is what almost everything reports.
     */
    private float chargeOf(Entity entity) {
        if (!FEED.has(entity) || !STRAIN.has(entity)) {
            return 0f;
        }
        UnitDef def = units.of(STRAIN.get(entity).kind);
        if (!def.canEvolveByFeeding()) {
            return 0f;
        }
        float progress = (float) FEED.get(entity).count / def.feedToEvolve();
        return progress < 1f ? progress : 1f;
    }

    private static Appearance.State stateOf(Entity entity) {
        boolean held = STUNNED.has(entity) && STUNNED.get(entity).active();
        if (held && STUNNED.get(entity).grappling) {
            return Appearance.State.GRAPPLING;
        }
        if (DYING.has(entity) && DYING.get(entity).begun) {
            return Appearance.State.DYING;
        }
        if (held) {
            return Appearance.State.STAGGERED;
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
        if (PERCEPTION.has(entity) && !PERCEPTION.get(entity).aggro) {
            return Appearance.State.UNAWARE;
        }
        return Appearance.State.NORMAL;
    }
}
