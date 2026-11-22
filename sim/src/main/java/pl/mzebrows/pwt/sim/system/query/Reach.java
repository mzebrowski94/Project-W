package pl.mzebrows.pwt.sim.system.query;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * What is within a man's <strong>reach</strong>, and which of it he would get to first - one distance for
 * every action there is (G0021), so one copy of how it is measured.
 *
 * <p>A scan rather than a neighbour query: there are a handful of casualties, guns and crates on the
 * longest level anybody would build. Which of two offers wins when both are in reach is {@code
 * ActionSystem}'s, not this.
 */
public final class Reach {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Crate> CRATE = ComponentMapper.getFor(Crate.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<WorkedSite> WORKED = ComponentMapper.getFor(WorkedSite.class);
    private static final ComponentMapper<Site> SITE = ComponentMapper.getFor(Site.class);

    private Reach() {}

    /**
     * The candidate offering this action that the man would reach first, or null if none is within reach.
     * Nearest surface to surface; never the man himself.
     */
    public static @Nullable Entity nearest(
            ImmutableArray<Entity> candidates, Entity man, float reach, ActionKind offer) {
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

    /**
     * The shut door the man would reach first, or null if none is within reach (G0069). Measured from his
     * surface to the nearest part of its doorway: a door has no body.
     */
    public static @Nullable Entity nearestDoor(ImmutableArray<Entity> doors, Entity man, float reach, float tileSize) {
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < doors.size(); i++) {
            Entity door = doors.get(i);
            if (!offers(door, ActionKind.OPEN_DOOR)) {
                continue;
            }
            float gap = doorGap(man, door, tileSize);
            if (gap <= reach && gap < nearestGap) {
                nearest = door;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /**
     * The untaken worked site the man would reach first, whatever its kind, or null if none is within reach (G0111).
     * What it offers is its own {@code WorkedSite.action}.
     */
    public static @Nullable Entity nearestWorkedSite(ImmutableArray<Entity> sites, Entity man, float reach) {
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < sites.size(); i++) {
            Entity site = sites.get(i);
            if (!offers(site, WORKED.get(site).action)) {
                continue;
            }
            float gap = gap(man, site);
            if (gap <= reach && gap < nearestGap) {
                nearest = site;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /** How far a man is from a door, his surface to the nearest part of its doorway, in world units. */
    public static float doorGap(Entity man, Entity door, float tileSize) {
        Position at = POSITION.get(man);
        return DOOR.get(door).distanceFrom(at.x, at.y, tileSize) - BODY.get(man).radius;
    }

    /** How far apart two bodies are, surface to surface, in world units. */
    public static float gap(Entity man, Entity other) {
        Position at = POSITION.get(man);
        Position where = POSITION.get(other);
        float toX = where.x - at.x;
        float toY = where.y - at.y;
        return (float) Math.sqrt(toX * toX + toY * toY) - BODY.get(man).radius - BODY.get(other).radius;
    }

    /**
     * Whether this is something the action could be done to now: a casualty, an empty gun, a shut crate, a shut
     * door, an untaken worked site of the kind that offers it. Public because the interface asks it too, to bracket faintly what else the key could act on (G0098) -
     * whether, never which: which one the key means is {@code ActionSystem}'s alone.
     */
    public static boolean offers(Entity candidate, ActionKind offer) {
        return switch (offer) {
            case STABILISE -> Life.casualty(candidate);
            case MAN_TURRET -> !TURRET.get(candidate).crewed;
            case OPEN_CRATE -> !CRATE.get(candidate).open() && !DEAD.has(candidate);
            case OPEN_DOOR -> !DOOR.get(candidate).open && !DEAD.has(candidate);
            case DOWNLOAD -> WORKED.get(candidate).action == offer && !SITE.get(candidate).taken;
            case NONE, LEAVE_TURRET -> throw new IllegalArgumentException("nothing is reached for to " + offer);
        };
    }
}
