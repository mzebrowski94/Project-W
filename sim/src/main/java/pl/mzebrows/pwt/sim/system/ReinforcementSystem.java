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
 * <p>The far half of a reinforcement. {@code PickupSystem} decides that a crate was worth a man and
 * writes the count onto the level entity, because a grant that belongs to nobody in particular has to
 * land somewhere that is not a taker (G0004); this reads that count and answers the only two
 * questions left - <strong>how many</strong> and <strong>where</strong>.
 *
 * <p><strong>How many is capped, and the cap is the point</strong> (pillar 3). A squad that can be
 * topped up without limit is not a resource, and men won would stop being worth crossing the ground
 * for. The ceiling is the squad the level started with plus the headroom it configured, so a full
 * squad wastes a crate - which is the honest arcade answer, and the alternative, a reserve that pays
 * out later, is a number the player would be tracking and the game would not be showing (pillar 5).
 *
 * <p><strong>Where is beside the commander</strong>, the same answer a man coming back from the dead
 * gets from {@code RespawnSystem} and for the same reason: ground somebody is demonstrably standing
 * on is neither rock nor unreachable, it is inside the leash, and it puts the man in the fight rather
 * than at the edge of the level walking towards it. He is jittered into the tiles around that ground
 * rather than stood on it (G0009), through the same {@code Scatter} that puts the starting squad down.
 *
 * <p><strong>One man a tick.</strong> A crate is worth several, and putting all of them down at once
 * means one scatter doing several placements at the moment a crate is taken - the one moment the
 * field is already busiest. Spreading them costs a sixtieth of a second each, which nobody can see,
 * and makes the work this does per tick constant rather than proportional to what was taken. The
 * level's owed count is the queue, and it was already the right place to keep one.
 *
 * <p><strong>With nobody standing, the debt is kept</strong> rather than spent. A trooper can reach a
 * crate while the commander is down, and dropping the grant on the floor because of it would make a
 * rescue depend on who happened to walk into the thing. It is not a balance carried anywhere - it
 * pays out on the tick he is back on his feet.
 *
 * <p>It runs at the end of the tick with the other systems that are about the level rather than about
 * anything standing in it, which is also after the solver has settled every position, so "beside the
 * commander" means where he actually ended up rather than where he was aiming.
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
        // A body already marked dead is not a man the squad still has, and it is gone by the next
        // tick. Counting it would make the last crate of a wipe arrive one man short.
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
                // A full squad wastes what it was owed, all of it and now rather than a man a tick.
                // Holding the rest until somebody died would be exactly the reserve G0030 rejects.
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
     * <p>The allocation is the one {@code WaveSystem} makes and for the same reason: a man arrives on
     * the ticks a crate has been taken, which is a handful of moments in a level and never a
     * per-entity loop. Keeping a scatter alive for the length of a level to save it would be the
     * worse trade.
     *
     * <p>The commander's own body is reserved before anything is placed, so the man forms up beside
     * him instead of inside him. One that will not fit - a corner with no open ground in it - is
     * simply not placed; the ground was full, and the alternative is a trooper inside rock.
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
