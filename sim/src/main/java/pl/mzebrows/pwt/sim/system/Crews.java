package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Getting on a gun and getting off it, in one place, for everybody who can.
 *
 * <p>Two will be able to: the commander, on his action key, and a trooper that has decided a gun is
 * worth more than the walk it was on. <strong>They must do exactly the same thing.</strong> What
 * being aboard costs - no body on the field, no velocity of your own, nothing driving you - is a
 * rule about being on a gun rather than about who climbed on it, and two copies of that is how one
 * of them ends up still solid, standing inside the thing he is firing.
 *
 * <p>The same argument {@link Aid} makes for putting a man back on his feet, and {@link Drivers} for
 * the questions every driver asks first.
 */
final class Crews {

    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private Crews() {}

    /**
     * The empty gun this man would reach first, or null if there is none within arm's length.
     *
     * <p>A scan of the stations rather than a neighbour query, for the reason {@link Aid#nearest}
     * scans the casualties: there are a handful on the worst level anybody would build.
     */
    static @Nullable Entity nearestFree(ImmutableArray<Entity> stations, Entity man, float reach) {
        Position at = POSITION.get(man);
        float radius = BODY.get(man).radius;
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < stations.size(); i++) {
            Entity candidate = stations.get(i);
            if (TURRET.get(candidate).crewed) {
                continue;
            }
            float gap = Aid.gapBetween(at, radius, candidate);
            if (gap <= reach && gap < nearestGap) {
                nearest = candidate;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /** How far this man is from that gun, surface to surface, so two offers can be ranked. */
    static float gapTo(Entity man, Entity station) {
        return Aid.gapBetween(POSITION.get(man), BODY.get(man).radius, station);
    }

    /**
     * Puts a man on a gun: at it rather than beside it, stopped, and out of the pairwise path
     * entirely, so that while he is on it the gun is the only thing anything can hit.
     *
     * <p>He is left carrying his {@code Vitality} and a {@code Damage} tally, which is deliberate: a blast
     * is a radius test over everything that can be hurt rather than a contact, so the gun going up
     * under him catches him exactly as standing on it should.
     */
    static void board(Entity man, Entity station) {
        Turret turret = TURRET.get(station);
        Crew crew = CREW.get(man);
        crew.station = Handles.of(station);
        crew.quietSeconds = 0f;
        turret.crewed = true;
        Position at = POSITION.get(station);
        POSITION.get(man).set(at.x, at.y);
        if (VELOCITY.has(man)) {
            VELOCITY.get(man).set(0f, 0f);
        }
        if (COLLIDER.has(man)) {
            COLLIDER.get(man).present = false;
        }
    }

    /**
     * Puts him back on the ground where the gun is standing, and back into the pairwise path.
     *
     * <p>He comes off inside the gun's own body, which the constraint solver then pushes him out of
     * on the next tick - the same answer it gives anything else that ends up overlapping something
     * immovable, and one less rule than placing him at a spot that might be rock.
     *
     * <p>The gun stops reading as manned on the same tick he steps off it rather than on the next
     * one. {@code TurretSystem} would put that right anyway, but a tick of a gun drawn with a crew
     * that has walked away is a tick of the picture lying about what is on the field.
     */
    static void leave(ImmutableArray<Entity> stations, Entity man) {
        Crew crew = CREW.get(man);
        for (int i = 0; i < stations.size(); i++) {
            if (Handles.of(stations.get(i)) == crew.station) {
                TURRET.get(stations.get(i)).crewed = false;
                break;
            }
        }
        crew.station = Crew.ON_FOOT;
        crew.quietSeconds = 0f;
        if (COLLIDER.has(man)) {
            COLLIDER.get(man).present = true;
        }
    }
}
