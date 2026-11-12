package pl.mzebrows.pwt.sim.system.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.Bag;
import pl.mzebrows.pwt.map.Scatter;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.RallyPoint;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Fills the field from what the side has (G0055): a crate's men, and the reserve's muster.
 *
 * <p><strong>How many is capped, and the cap is the point</strong> (pillar 3): the field holds the
 * squad plus its headroom and upgrades, and <strong>every commander on it is one of those men</strong>
 * (G0055) - whatever his slot; one waiting to come back is not on it. A crate's men go onto it <strong>one a tick</strong> while it
 * has room, and into the reserve after that - past the reserve's own most they are wasted. Every
 * {@code sim.reserve.musterSeconds} a muster calls a few men out of the reserve, as many as the field
 * has room for; a full field leaves them where they are.
 *
 * <p><strong>Where is the rally point</strong> ({@link RallyPoint}), jittered through {@code Scatter}
 * (G0009) - the spawn area until a point is taken, the point taken last after that. A man who will
 * not fit there is not placed: a crate's goes to the reserve, a muster's stays in it.
 *
 * <p>A lost level fills nothing.
 */
public final class ReinforcementSystem extends EntitySystem {

    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);

    private final SimConfig config;
    private final TileMap map;
    private final Spawner spawner;
    private final Modifiers modifiers;
    private final Rng rng;
    private final int musterTicks;

    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> men = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());

    public ReinforcementSystem(SimConfig config, TileMap map, Spawner spawner, Modifiers modifiers, Rng rng) {
        this.config = config;
        this.map = map;
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.rng = rng;
        this.musterTicks = Ticks.of(config.reserve().musterSeconds(), config.tickSeconds());
    }

    @Override
    public void addedToEngine(Engine engine) {
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
        men = engine.getEntitiesFor(Life.onTheField());
        points = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < levels.size(); i++) {
            Level level = LEVEL.get(levels.get(i));
            if (level.failed) {
                level.pendingReinforcements = 0;
                continue;
            }
            int room = squadMost(config, modifiers) - men.size();
            room -= deliver(level, room);
            muster(level, room);
        }
    }

    /**
     * The most men the field holds, troopers and commanders together: the squad the level started with,
     * its headroom, and what the upgrades held add to that. The one place it is worked out.
     */
    public static int squadMost(SimConfig config, Modifiers modifiers) {
        return config.trooper().squadSize()
                + modifiers.squadHeadroom(config.trooper().squadGrowth());
    }

    /** One of a crate's men onto the field while it has room; the rest of the crate into the reserve. */
    private int deliver(Level level, int room) {
        if (level.pendingReinforcements <= 0) {
            return 0;
        }
        if (room <= 0) {
            bank(level, level.pendingReinforcements);
            level.pendingReinforcements = 0;
            return 0;
        }
        level.pendingReinforcements--;
        if (land(1) == 1) {
            return 1;
        }
        bank(level, 1);
        return 0;
    }

    /** Into the reserve, up to its most; past that a crate's men are wasted (G0055). */
    private void bank(Level level, int men) {
        level.reserve = Math.min(config.reserve().max(), level.reserve + men);
    }

    /**
     * Every period, as many of the reserve as the muster calls up and the field has room for. The next
     * muster's size is dealt as this one goes.
     */
    private void muster(Level level, int room) {
        if (musterTicks <= 0) {
            return;
        }
        if (level.musterTick == 0L) {
            level.musterTick = level.tick + musterTicks;
            level.musterNext = deal(level);
            return;
        }
        if (level.tick < level.musterTick) {
            return;
        }
        level.musterTick = level.tick + musterTicks;
        int called = Math.min(level.musterNext, Math.min(room, level.reserve));
        level.musterNext = deal(level);
        if (called > 0) {
            level.reserve -= land(called);
        }
    }

    /** How many the next muster calls up, from the level's bag: every size once before any again. */
    private int deal(Level level) {
        int fewest = config.reserve().musterMin();
        int sizes = config.reserve().musterMax() - fewest + 1;
        int size = Bag.draw(rng, level.musterDealt, sizes);
        level.musterDealt = Bag.take(level.musterDealt, size, sizes);
        return fewest + size;
    }

    /**
     * Puts men down at the rally point, and says how many fitted.
     *
     * <p>Allocates, as {@code WaveSystem} does: once per arrival, not in a per-entity loop.
     */
    private int land(int count) {
        float radius = config.trooper().radius();
        Scatter scatter = new Scatter(map, rng, radius, radius);
        return scatter.scatter(RallyPoint.area(points, config, map), count, radius, spawner::spawnTrooper);
    }
}
