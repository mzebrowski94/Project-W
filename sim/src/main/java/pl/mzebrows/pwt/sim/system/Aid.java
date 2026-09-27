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
 * <p>The commander and a trooper <strong>must do exactly the same thing</strong>: every rule is about
 * the man on the ground, not who reached him. Who is being worked on is re-resolved every tick (G0010).
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
     * <p>A scan: there are only ever a handful. Nearest surface to surface; nothing dying, and nobody
     * himself.
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
     * <p>He comes up hurt and briefly protected. Returns whether this was the tick he came up - the
     * stabilise a commander's tally counts (G0044).
     */
    static boolean work(Entity subject, float deltaTime, float healthFraction, float invulnerableSeconds) {
        Casualty casualty = CASUALTY.get(subject);
        casualty.stabilised += deltaTime;
        if (!casualty.stabilisedEnough(hauled(subject))) {
            return false;
        }
        casualty.recover();
        HEALTH.get(subject).current = HEALTH.get(subject).max * healthFraction;
        if (INVULNERABLE.has(subject)) {
            INVULNERABLE.get(subject).grant(Ticks.of(invulnerableSeconds, deltaTime));
        }
        return true;
    }

    /**
     * Loses the work done so far.
     *
     * <p>A commitment, not an accumulation.
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
