package pl.mzebrows.pwt.sim.system.query;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Burrow;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Sling;
import pl.mzebrows.pwt.sim.component.Slither;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * How much of the speed it asked for a unit actually gets this tick, in one place, for everything
 * that moves under its own power.
 *
 * <p>There are two ways to be slow and they are the same answer, so they are multiplied together
 * here rather than answered twice. One is something that was <em>done</em> to the unit - a beam held
 * on it - and lives on its {@link Slowed}; the other is where it is <em>standing</em>, which is a
 * fact about the ground and is asked of the map rather than stored. A unit wading through a thicket
 * with a laser on it is slow twice over, and nothing had to say so.
 *
 * <p>It is here rather than inside {@code DriveSystem} for the reason {@code Guns} and {@code Aid}
 * are single places: the commander is the one thing on the field that does not go through
 * {@code DriveSystem} - his own control system owns his velocity outright - so a rule that lived
 * there would have been a rule the player was exempt from, which is the exact opposite of what
 * ground like this is for.
 *
 * <p><strong>A slow scales a course; it never chooses one</strong> - the same contract a gait and a
 * weave already keep. Nothing here turns anything, so a slowed unit walks exactly where it meant to
 * and simply takes longer about it, and a route through a thicket is the same route it always was.
 *
 * <p>Allocates nothing and reads three component slots and one tile. It is paid once per driven unit
 * per tick, which is the same order as everything else in the movement pipeline.
 */
public final class Pace {

    private static final ComponentMapper<Slowed> SLOWED = ComponentMapper.getFor(Slowed.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Barrage> BARRAGE = ComponentMapper.getFor(Barrage.class);
    private static final ComponentMapper<Sling> SLING = ComponentMapper.getFor(Sling.class);
    private static final ComponentMapper<Burrow> BURROW = ComponentMapper.getFor(Burrow.class);
    private static final ComponentMapper<Slither> SLITHER = ComponentMapper.getFor(Slither.class);

    private Pace() {}

    /**
     * The fraction of its desired velocity this entity may actually have.
     *
     * <p>One for anything carrying neither a slow nor a position, which is what keeps this from
     * being a rule every mover has to opt into.
     */
    public static float of(Entity entity, TileMap map, Defs<Terrain, GroundDef> ground) {
        if (SLITHER.has(entity) && SLITHER.get(entity).unslowed) {
            // A slither keeps its pace: no beam, glue or mud slows it (G0095).
            return 1f;
        }
        if (BURROW.has(entity) && BURROW.get(entity).enabled) {
            // Nor does a worm, under the ground or coming up (G0096).
            return 1f;
        }
        float pace = SLOWED.has(entity) ? SLOWED.get(entity).factor() : 1f;
        if (VITALITY.has(entity)) {
            // A wound is the third way to be slow, and the same answer (G0053).
            pace *= VITALITY.get(entity).paceFactor();
        }
        if (BARRAGE.has(entity)) {
            // A giant standing its ground while it roars and fires: the opening it gives (G0071).
            pace *= BARRAGE.get(entity).paceFactor();
        }
        if (SLING.has(entity)) {
            // A slinger standing for the tell before a sling (G0074).
            pace *= SLING.get(entity).paceFactor();
        }
        return pace * groundFactor(entity, map, ground);
    }

    /**
     * Whether something slows this entity this tick: a slow done to it ({@link Slowed}: glue underfoot, a beam), or
     * ground it costs more to cross. A wound is not a slow here, nor is anything that only stands a creature still.
     * What a dash asks (G0107): a tick of one on slowing ground goes at a fraction of its speed, whatever the fraction
     * of a walk would be.
     */
    public static boolean slowing(Entity entity, TileMap map, Defs<Terrain, GroundDef> ground) {
        if (SLOWED.has(entity) && SLOWED.get(entity).factor() < 1f) {
            return true;
        }
        return groundFactor(entity, map, ground) < 1f;
    }

    /** How much of its speed the ground it stands on leaves it; one off any ground that matters. */
    private static float groundFactor(Entity entity, TileMap map, Defs<Terrain, GroundDef> ground) {
        if (!POSITION.has(entity)) {
            return 1f;
        }
        Position at = POSITION.get(entity);
        GroundDef under = ground.of(map.terrainAt(at.x, at.y));
        if (!under.matters()) {
            return 1f;
        }
        Side side = FACTION.has(entity) ? FACTION.get(entity).side : Side.NEUTRAL;
        return under.factorFor(side);
    }
}
