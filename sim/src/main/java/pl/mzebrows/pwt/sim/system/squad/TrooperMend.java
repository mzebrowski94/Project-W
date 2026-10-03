package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * A wounded man walking to a medkit (G0053): the half of the cascade below fetching somebody who is down
 * and above getting on a gun.
 *
 * <p><strong>No roll.</strong> A wounded man with nothing better to do and a medkit within reach always
 * goes - the reach is what keeps it from emptying the line. What it heads for is a place; taking it is
 * the ordinary claim when he walks onto it, which is also what refuses it to a man who is no longer
 * wounded. <strong>He drops the walk the moment it stops being worth it</strong> - somebody else took it,
 * or his wounds cleared on the way - rather than finishing the journey and standing over it.
 */
final class TrooperMend {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);

    private final SimConfig config;
    private final float reach;

    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());

    TrooperMend(SimConfig config) {
        this.config = config;
        this.reach = config.trooper().medkitReach();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        lying = engine.getEntitiesFor(Family.all(Pickup.class, Position.class).get());
    }

    /** Goes for the nearest medkit within reach, if he is wounded. */
    boolean mends(Entity entity, Trooper trooper, Position at) {
        @Nullable Entity medkit = wounded(entity) ? nearest(at) : null;
        if (medkit == null) {
            if (trooper.mode == Trooper.Mode.MEND) {
                trooper.clearTarget();
            }
            return false;
        }
        trooper.mode = Trooper.Mode.MEND;
        Position where = POSITION.get(medkit);
        trooper.headFor(where.x, where.y);
        return true;
    }

    private static boolean wounded(Entity entity) {
        return VITALITY.has(entity) && VITALITY.get(entity).wounded();
    }

    /** The nearest pickup within reach that would mend a man on his side, or null. A handful: a scan. */
    private @Nullable Entity nearest(Position at) {
        if (reach <= 0f) {
            return null;
        }
        @Nullable Entity nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < lying.size(); i++) {
            Entity candidate = lying.get(i);
            Pickup pickup = PICKUP.get(candidate);
            if (pickup.claimed || Life.dying(candidate)) {
                continue;
            }
            if (!(config.pickup(pickup.kind).grantFor(Side.PLAYER) instanceof Grant.Mend)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            if (!Sight.withinRange(at.x, at.y, where.x, where.y, reach)) {
                continue;
            }
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            float distance = toX * toX + toY * toY;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
