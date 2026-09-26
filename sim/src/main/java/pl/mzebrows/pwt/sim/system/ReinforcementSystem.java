package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;

/**
 * Turns the men a level owes into men standing on it.
 *
 * <p>Reads the count {@code PickupSystem} left on the level (G0004). <strong>How many is capped, and
 * the cap is the point</strong> (pillar 3, G0030): a full squad wastes a crate. <strong>Where is
 * beside the commander</strong>, as a respawn, jittered through {@code Scatter} (G0009).
 *
 * <p><strong>One man a tick</strong>; with nobody standing, the debt waits.
 */
public final class ReinforcementSystem extends EntitySystem {

    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);

    private final SimConfig config;
    private final TileMap map;
    private final Spawner spawner;
    private final Rng rng;

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> squad = new ImmutableArray<>(new Array<>());

    public ReinforcementSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng) {
        this.config = config;
        this.map = map;
        this.spawner = spawner;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
        // A body already marked dead is not a man the squad still has.
        squad = engine.getEntitiesFor(
                Family.all(Trooper.class).exclude(Dead.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < levels.size(); i++) {
            Level level = LEVEL.get(levels.get(i));
            if (level.pendingReinforcements <= 0) {
                continue;
            }
            Position rally = Drivers.livingTarget(commanders);
            if (rally == null) {
                continue;
            }
            if (config.trooper().squadSize() + config.trooper().squadGrowth() - squad.size() <= 0) {
                // A full squad wastes what it was owed, all of it (G0030).
                level.pendingReinforcements = 0;
                continue;
            }
            level.pendingReinforcements--;
            land(rally);
        }
    }

    /**
     * Puts one man down around one place.
     *
     * <p>Allocates, as {@code WaveSystem} does. The commander's body is reserved first; a man who will
     * not fit is not placed.
     */
    private void land(Position rally) {
        float radius = config.trooper().radius();
        Scatter scatter = new Scatter(map, rng, radius, radius);
        scatter.reserve(rally.x, rally.y, config.commander().radius());
        TileRect around =
                TileRect.around(map, rally.x, rally.y, config.trooper().reinforcementRadius());
        scatter.scatter(around, 1, radius, spawner::spawnTrooper);
    }
}
