package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Lair;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.def.Arrival;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Wakes a level's lair: the first tick every commander still on his feet stands on its ground, it puts
 * down everything it holds - creatures, nests and pickups (G0048).
 *
 * <p><strong>Every one of them, not the first.</strong> The lair is kept off the field so nobody sees
 * the end of the level from the one before it, and a boss that rises behind a player still in the
 * last biome is a boss he meets with half his friends missing. A player who is down does not hold it
 * up - he is not walking anywhere - and nor does one whose link has dropped (G0052): his commander stands
 * idle wherever he was, and would hold it up for ever. At least one has to be standing there.
 *
 * <p>Every tick it waits it says how many are there of how many it wants, for the interface (B.1).
 *
 * <p>A creature rises facing the nearest commander on its ground, not east as everything spawns: a boss
 * that met the players with his back would read as asleep, and nothing turns a rooted thing until it
 * has somebody to shoot at (pillar 5).
 *
 * <p><strong>What it raises stands out as its nests do</strong> (G0088, provisional): a trooper picks its boss out, weighs
 * him and keeps on him on the nest's own terms, {@code sim.nest.standsOut}, {@code threat} and {@code kept}. A boss
 * that shoots from past where a man notices anything would otherwise be fought by the commander alone.
 *
 * <p>It runs with the other systems that are about the level rather than anything standing in it, after
 * the death chain and before the objectives, for the reason {@code WaveSystem} does: what rises this
 * tick counts against the level on this tick.
 */
public final class LairSystem extends EntitySystem {

    private static final ComponentMapper<Lair> LAIR = ComponentMapper.getFor(Lair.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Presence> PRESENCE = ComponentMapper.getFor(Presence.class);

    private final TileMap map;
    private final Spawner spawner;

    /** The nest's terms as a target, which everything the lair raises is given (G0088). */
    private final SimConfig.NestConfig nest;

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public LairSystem(SimConfig config, TileMap map, Spawner spawner) {
        this.map = map;
        this.spawner = spawner;
        this.nest = config.nest();
    }

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(Family.all(Lair.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < levels.size(); i++) {
            Entity level = levels.get(i);
            Lair lair = LAIR.get(level);
            if (!lair.waiting()) {
                continue;
            }
            count(lair, PRESENCE.has(level) ? PRESENCE.get(level) : null);
            if (lair.present() == 0 || lair.present() < lair.needed()) {
                continue;
            }
            for (int a = 0; a < lair.size(); a++) {
                Arrival arrival = lair.get(a);
                UnitKind strain = arrival.strain();
                PickupKind pickup = arrival.pickup();
                if (arrival.form() == Arrival.Form.PICKUP && pickup != null) {
                    spawner.spawnPickup(pickup, arrival.x(), arrival.y());
                } else if (arrival.form() == Arrival.Form.NEST && strain != null) {
                    spawner.spawnNest(strain, arrival.x(), arrival.y())
                            .getComponent(Enemy.class)
                            .raisedByALair();
                } else if (strain != null) {
                    Entity nearest = nearestOn(lair.ground(), arrival.x(), arrival.y());
                    Position at = nearest == null ? null : POSITION.get(nearest);
                    float faceX = at == null ? 1f : at.x - arrival.x();
                    float faceY = at == null ? 0f : at.y - arrival.y();
                    spawner.spawnUnit(strain, arrival.x(), arrival.y(), faceX, faceY)
                            .getComponent(Enemy.class)
                            .standingOut(nest.standsOut())
                            .weighed(nest.threat(), nest.kept())
                            .raisedByALair();
                }
            }
            lair.wake();
        }
    }

    /** The commander on his feet on this ground nearest a place - what rises there faces him. */
    private @Nullable Entity nearestOn(TileRect ground, float x, float y) {
        Entity nearest = null;
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            Position at = POSITION.get(commander);
            if (Life.down(commander) || !ground.contains(map.colAt(at.x), map.rowAt(at.y))) {
                continue;
            }
            float dx = at.x - x;
            float dy = at.y - y;
            if (dx * dx + dy * dy < closest) {
                closest = dx * dx + dy * dy;
                nearest = commander;
            }
        }
        return nearest;
    }

    /** How many commanders on their feet and present stand on its ground, of how many there are. */
    private void count(Lair lair, @Nullable Presence presence) {
        TileRect ground = lair.ground();
        int present = 0;
        int needed = 0;
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (Life.down(commander) || (presence != null && presence.isAway(COMMANDER.get(commander).slot))) {
                continue;
            }
            needed++;
            Position at = POSITION.get(commander);
            if (ground.contains(map.colAt(at.x), map.rowAt(at.y))) {
                present++;
            }
        }
        lair.count(present, needed);
    }
}
