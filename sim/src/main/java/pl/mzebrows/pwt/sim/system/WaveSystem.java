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
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.Wave;

/**
 * Sends in what the level still has to throw at you, when its time comes.
 *
 * <p>A wave is why a defence is a defence: the ground in front of you empties and then it does not.
 * Everything before this had its whole roster on the field from the first tick, which makes a level a
 * map you clear rather than a place that is attacked.
 *
 * <p>Everyone in a wave is jittered inside the tiles it names, never stood at a tile centre (G0009) -
 * the same {@code Scatter} that puts the squad down, for the same reason: a line of aliens arriving on
 * tile centres reads as a grid, and the grid is a unit of construction rather than of play.
 *
 * <p><strong>A level may hold its schedule back.</strong> Nothing arrives until something arms it,
 * which on a march is the moment the squad takes the ground it is then told to hold - so the last
 * fight is one the player starts rather than one that is already running when he walks in.
 *
 * <p>It draws from a stream of its own, so re-tuning when a wave arrives never reshuffles where the
 * squad roams or how a spitter aims (pillar 6).
 *
 * <p>It runs at the end of the tick, with the other systems that are about the level rather than
 * about anything standing in it, and <strong>before</strong> {@code ObjectiveSystem} - a level whose
 * last wave lands this tick must not read as complete for the tick before the aliens appear.
 */
public final class WaveSystem extends EntitySystem {

    private static final ComponentMapper<Waves> WAVES = ComponentMapper.getFor(Waves.class);

    private final TileMap map;
    private final SimConfig config;
    private final Spawner spawner;
    private final Rng rng;

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public WaveSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng) {
        this.config = config;
        this.map = map;
        this.spawner = spawner;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(Family.all(Waves.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < levels.size(); i++) {
            Waves waves = WAVES.get(levels.get(i));
            if (!waves.armed() || waves.spent()) {
                continue;
            }
            waves.waited++;
            if (waves.waited < Ticks.of(waves.next().afterSeconds(), deltaTime)) {
                continue;
            }
            send(waves.next());
            waves.advance();
        }
    }

    /**
     * Puts one wave on the field.
     *
     * <p>The allocation here is deliberate and safe: a wave arrives every several seconds at most, and
     * this is not a per-entity loop. Doing it any other way would mean a scatter kept alive for the
     * length of a level to save an object a handful of times.
     */
    private void send(Wave wave) {
        float radius = config.unit(wave.kind()).radius();
        Scatter scatter = new Scatter(map, rng, radius, radius);
        scatter.scatter(wave.where(), wave.count(), radius, (x, y) -> spawner.spawnUnit(wave.kind(), x, y));
    }
}
