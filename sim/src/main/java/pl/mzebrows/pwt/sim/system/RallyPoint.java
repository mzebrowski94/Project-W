package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Where a man comes onto the field after the first tick (G0055): the <strong>rally point</strong>.
 *
 * <p>The spawn area the level started the side in, until a point is taken; round the point taken last
 * after that - ground the side holds, and the ground nearest the fight it has moved on to. One answer
 * for everybody drawn from the reserve: a muster's troopers, a crate's men, and a returning commander.
 * A pure function of the points and the config, asked on the tick a man arrives rather than kept.
 */
final class RallyPoint {

    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private RallyPoint() {}

    /**
     * The tiles a man is put down in this tick.
     *
     * @param points every control point on the level, each with a {@link Position}
     */
    static TileRect area(ImmutableArray<Entity> points, SimConfig config, TileMap map) {
        Position taken = lastTaken(points);
        if (taken == null) {
            return config.spawn().on(map);
        }
        return TileRect.around(map, taken.x, taken.y, config.trooper().reinforcementRadius());
    }

    /** Where the point taken last stands, or null while none has been. */
    private static @Nullable Position lastTaken(ImmutableArray<Entity> points) {
        Position found = null;
        int order = -1;
        for (int i = 0; i < points.size(); i++) {
            Entity entity = points.get(i);
            ControlPoint point = POINT.get(entity);
            if (point.captured && point.takenOrder > order) {
                order = point.takenOrder;
                found = POSITION.get(entity);
            }
        }
        return found;
    }
}
