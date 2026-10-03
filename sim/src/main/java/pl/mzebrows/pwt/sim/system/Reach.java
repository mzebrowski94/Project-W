package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * What is within a man's <strong>reach</strong>, and which of it he would get to first - one distance for
 * every action there is (G0021), so one copy of how it is measured.
 *
 * <p>A scan rather than a neighbour query: there are a handful of casualties, guns and crates on the
 * longest level anybody would build. Which of two offers wins when both are in reach is {@code
 * ActionSystem}'s, not this.
 */
final class Reach {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Crate> CRATE = ComponentMapper.getFor(Crate.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    private Reach() {}

    /**
     * The candidate offering this action that the man would reach first, or null if none is within reach.
     * Nearest surface to surface; never the man himself.
     */
    static @Nullable Entity nearest(ImmutableArray<Entity> candidates, Entity man, float reach, ActionKind offer) {
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Entity candidate = candidates.get(i);
            if (Objects.equals(candidate, man) || !offers(candidate, offer)) {
                continue;
            }
            float gap = gap(man, candidate);
            if (gap <= reach && gap < nearestGap) {
                nearest = candidate;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /** How far apart two bodies are, surface to surface, in world units. */
    static float gap(Entity man, Entity other) {
        Position at = POSITION.get(man);
        Position where = POSITION.get(other);
        float toX = where.x - at.x;
        float toY = where.y - at.y;
        return (float) Math.sqrt(toX * toX + toY * toY) - BODY.get(man).radius - BODY.get(other).radius;
    }

    /** Whether this is something the action could be done to now: a casualty, an empty gun, a shut crate. */
    private static boolean offers(Entity candidate, ActionKind offer) {
        return switch (offer) {
            case STABILISE -> Life.casualty(candidate);
            case MAN_TURRET -> !TURRET.get(candidate).crewed;
            case OPEN_CRATE -> !CRATE.get(candidate).open() && !DEAD.has(candidate);
            case NONE, LEAVE_TURRET -> throw new IllegalArgumentException("nothing is reached for to " + offer);
        };
    }
}
