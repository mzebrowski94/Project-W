package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * Putting a man back on his feet, in one place, for everybody who can do it.
 *
 * <p>Two things can now: the commander, on his action key, and a trooper that has decided to go and
 * fetch somebody. <strong>They must do exactly the same thing.</strong> How long the work takes, what
 * being hauled costs, how much health he gets back and how long he is protected as he stands up are
 * all rules about the man on the ground rather than about who reached him - and two copies of them
 * is how a man revived by a trooper quietly comes up with a full bar.
 *
 * <p>It is the same argument {@link Drivers} makes for its two questions and {@link Deaths} makes for
 * the one death path, and this is the third case of it in the codebase.
 *
 * <p>Nothing here holds an entity across ticks. Who is being worked on is re-resolved every tick by
 * whoever is doing the working, exactly as {@code Perception} re-resolves what a unit is hunting: a
 * pooled component keeping a reference for a second and a half would come back aimed at whatever was
 * built from the recycled instance (G0010). Nothing is lost by re-deciding - a man who has walked
 * away from the body he was working on is no longer working on it.
 */
final class Aid {

    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private Aid() {}

    /**
     * The man on the ground this one would reach first, or null if there is nobody within arm's
     * length.
     *
     * <p>A scan of the casualties rather than a neighbour query: there are almost never any, and on
     * the worst day there are a handful. The collision grid exists for pairs of everything against
     * everything, which this is not.
     *
     * <p>Nearest by the gap between surfaces, so a wide man and a narrow one standing equally close
     * are equally close. Nothing already dying is offered, or the key would do nothing over a corpse
     * that had just run out of time - and nobody is offered himself, which matters now that the men
     * who can do this are the same men it can be done to.
     */
    static @Nullable Entity nearest(ImmutableArray<Entity> casualties, Entity helper, float reach) {
        Position at = POSITION.get(helper);
        float radius = BODY.get(helper).radius;
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < casualties.size(); i++) {
            Entity candidate = casualties.get(i);
            if (Objects.equals(candidate, helper) || !CASUALTY.get(candidate).begun) {
                continue;
            }
            if (DYING.has(candidate) && DYING.get(candidate).begun) {
                continue;
            }
            float gap = gapBetween(at, radius, candidate);
            if (gap <= reach && gap < nearestGap) {
                nearest = candidate;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /** How far apart two bodies are, surface to surface, in world units. */
    static float gapBetween(Position at, float radius, Entity other) {
        Position where = POSITION.get(other);
        float toX = where.x - at.x;
        float toY = where.y - at.y;
        return (float) Math.sqrt(toX * toX + toY * toY) - radius - BODY.get(other).radius;
    }

    /**
     * A tick's worth of work on him, and puts him back on his feet once there has been enough of it.
     *
     * <p>He comes up hurt and briefly protected. Both are the point: picking a man up is not the same
     * as getting him out, and without the moment of protection the round that was already in the air
     * undoes the whole rescue, which reads as the game cheating rather than as bad luck.
     */
    static void work(Entity subject, float deltaTime, float healthFraction, float invulnerableSeconds) {
        Casualty casualty = CASUALTY.get(subject);
        casualty.stabilised += deltaTime;
        if (!casualty.stabilisedEnough(hauled(subject))) {
            return;
        }
        casualty.recover();
        HEALTH.get(subject).current = HEALTH.get(subject).max * healthFraction;
        if (INVULNERABLE.has(subject)) {
            INVULNERABLE.get(subject).grant(Ticks.of(invulnerableSeconds, deltaTime));
        }
    }

    /**
     * Loses the work done so far.
     *
     * <p>A stabilise is a commitment rather than an accumulation: a man interrupted halfway is a man
     * you have to start again on. Banking the work would mean tapping the key on every pass, which
     * is no commitment at all - and it is the whole reason hauling him somewhere quiet is worth what
     * it costs.
     */
    static void letGo(Entity subject) {
        CASUALTY.get(subject).stabilised = 0f;
    }

    /** How far through being put back on his feet he is, in {@code [0, 1]}, for the interface. */
    static float progress(Entity subject) {
        return CASUALTY.get(subject).stabiliseProgress(hauled(subject));
    }

    /** Whether he was carried here, which is what makes the work at this end so much longer. */
    private static boolean hauled(Entity subject) {
        return DRAGGED.has(subject) && DRAGGED.get(subject).hauled;
    }
}
