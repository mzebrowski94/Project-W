package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Puts a lot of creatures down round a place, out of it rather than onto the men standing by it: a nest's brood, and
 * the riders a slinger still carried when it died (G0074).
 *
 * <p>Through {@code Scatter} (G0009): the tiles nearest the place are filled first, nothing is put down on the body
 * the creatures come out of, and nothing within {@code keepOff} of anybody on the player's side - a last brood put
 * down on the man who fired the killing shot was a hit he could not answer. A cornered place puts down fewer.
 *
 * <p>Allocates, as {@code WaveSystem} does: once per brood, never in a per-entity loop.
 */
public final class Brood {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    private final TileMap map;
    private final Spawner spawner;

    /** How far off anybody on the player's side nothing is put down. */
    private final float keepOff;

    public Brood(TileMap map, Spawner spawner, float keepOff) {
        this.map = map;
        this.spawner = spawner;
        this.keepOff = keepOff;
    }

    /**
     * Puts {@code count} of a strain down within {@code radius} of a place, off a body of {@code bodyRadius} there
     * and off everybody on the player's side among {@code quarry}, stamped with {@code wave}.
     */
    public void hatch(
            Rng rng,
            UnitKind kind,
            float unitRadius,
            int wave,
            Position at,
            float bodyRadius,
            float radius,
            int count,
            ImmutableArray<Entity> quarry) {
        if (count <= 0) {
            return;
        }
        Scatter scatter = new Scatter(map, rng, unitRadius, unitRadius);
        scatter.reserve(at.x, at.y, bodyRadius);
        keepOffOurSide(scatter, at, radius + map.tileSize() * 2f, quarry);
        scatter.scatterNearest(
                TileRect.around(map, at.x, at.y, radius),
                at.x,
                at.y,
                count,
                unitRadius,
                (x, y) -> spawner.spawnUnit(kind, x, y, wave));
    }

    /**
     * Reserves a keep-off round every man on the player's side near enough to matter. The same list an alien hunts
     * from; a man who is down is still a man it must not land on.
     */
    private void keepOffOurSide(Scatter scatter, Position at, float near, ImmutableArray<Entity> quarry) {
        if (keepOff <= 0f) {
            return;
        }
        float reach = (near + keepOff) * (near + keepOff);
        for (int i = 0; i < quarry.size(); i++) {
            Entity candidate = quarry.get(i);
            if (FACTION.get(candidate).side != Side.PLAYER || DEAD.has(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            if (toX * toX + toY * toY <= reach) {
                scatter.reserve(where.x, where.y, keepOff);
            }
        }
    }
}
