package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Finding a crate within arm's length and opening it (G0056), for {@code ActionSystem}, which owns the key,
 * and {@code CrateSystem}, which owns everything after - the way {@link Crews} serves the gun.
 *
 * <p>A scan of the crates rather than a neighour query, for the reason {@link Aid#nearest} scans the
 * casualties: there are a handful on the longest level anybody would build.
 */
final class Crates {

    private static final ComponentMapper<Crate> CRATE = ComponentMapper.getFor(Crate.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    private Crates() {}

    /** The shut crate this man would reach first, or null if there is none within arm's length. */
    static @Nullable Entity nearestOpenable(ImmutableArray<Entity> crates, Entity man, float reach) {
        Position at = POSITION.get(man);
        float radius = BODY.get(man).radius;
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < crates.size(); i++) {
            Entity candidate = crates.get(i);
            Crate crate = CRATE.get(candidate);
            if (crate.open() || DEAD.has(candidate)) {
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

    /** The crate this slot has open, or null. A slot has at most one: he cannot open another meanwhile. */
    static @Nullable Entity openBy(ImmutableArray<Entity> crates, int slot) {
        for (int i = 0; i < crates.size(); i++) {
            Crate crate = CRATE.get(crates.get(i));
            if (crate.openedBy == slot) {
                return crates.get(i);
            }
        }
        return null;
    }

    /** How far this man is from that crate, surface to surface, so offers can be ranked. */
    static float gapTo(Entity man, Entity crate) {
        return Aid.gapBetween(POSITION.get(man), BODY.get(man).radius, crate);
    }

    /**
     * Opens a crate for a slot, if nobody has it (G0056). {@code ActionSystem} opens in slot order, so two
     * presses on one tick go to the lower slot, and the higher one to the next crate in his reach.
     */
    static void open(Entity entity, int slot, long tick) {
        Crate crate = CRATE.get(entity);
        if (crate.open()) {
            return;
        }
        crate.openedBy = slot;
        crate.openedOn = tick;
        crate.openerWounds = 0;
    }
}
