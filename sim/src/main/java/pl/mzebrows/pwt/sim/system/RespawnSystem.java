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
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;
import pl.mzebrows.pwt.sim.component.Shield;

/**
 * A death reaction, the countdown it starts, and what it costs (G0055).
 *
 * <p>Runs inside the window between a unit being marked dead and being reaped, which is where every
 * reaction to a death belongs (G0004). It reads <em>when</em> and <em>whether</em> off the reaction
 * on the dying unit.
 *
 * <p><strong>A man coming back is a man from the reserve.</strong> A ticket is paid for with one the
 * tick after it opens - before the muster, which runs later in the tick, so a player is heard before a
 * trooper - and counts down only once it is paid. With nobody in reserve it waits. With every commander
 * out and every ticket waiting on an empty reserve the level is lost, and stays lost.
 *
 * <p><strong>Where</strong> is the rally point on the tick he returns ({@link RallyPoint}): the spawn
 * area until a point is taken, the point taken last after that - never beside a friend who is still
 * standing, which was the rule before the reserve (G0055 amends G0023).
 *
 * <p>The countdown lives on a ticket entity of its own because the unit holding it is about to stop
 * existing. Keeping the dead unit around in a half-alive state instead would mean every family in
 * the game having to exclude it.
 */
public final class RespawnSystem extends EntitySystem {

    private static final ComponentMapper<RespawnsOnDeath> REACTION = ComponentMapper.getFor(RespawnsOnDeath.class);
    private static final ComponentMapper<Respawning> PENDING = ComponentMapper.getFor(Respawning.class);
    private static final ComponentMapper<Shield> SHIELD = ComponentMapper.getFor(Shield.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);

    private final SimConfig config;
    private final TileMap map;
    private final Spawner spawner;
    private final Rng rng;

    private ImmutableArray<Entity> dying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> pending = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> living = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());

    public RespawnSystem(SimConfig config, TileMap map, Spawner spawner, Rng rng) {
        this.config = config;
        this.map = map;
        this.spawner = spawner;
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        dying = engine.getEntitiesFor(
                Family.all(Dead.class, RespawnsOnDeath.class).get());
        pending = engine.getEntitiesFor(Family.all(Respawning.class).get());
        // On the field in any state short of gone: a man down may still be put back on his feet.
        living = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).exclude(Dead.class).get());
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
        points = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        openTickets();
        if (levels.size() == 0) {
            return;
        }
        Level level = LEVEL.get(levels.first());
        payTickets(level);
        closeExpiredTickets();
        judge(level);
    }

    /** Runs once per death: the unit is marked dead now and reaped at the end of this same tick. */
    private void openTickets() {
        for (int i = 0; i < dying.size(); i++) {
            Entity dead = dying.get(i);
            RespawnsOnDeath reaction = REACTION.get(dead);
            int slot = COMMANDER.has(dead) ? COMMANDER.get(dead).slot : 0;
            spawner.spawnRespawn(reaction.delaySeconds, reaction.shieldSeconds, slot);
        }
    }

    /** A man from the reserve for every ticket still waiting on one, oldest first, while there are any. */
    private void payTickets(Level level) {
        if (level.failed) {
            return;
        }
        for (int i = 0; i < pending.size() && level.reserve > 0; i++) {
            Respawning respawning = PENDING.get(pending.get(i));
            if (!respawning.paid) {
                respawning.paid = true;
                level.reserve--;
            }
        }
    }

    private void closeExpiredTickets() {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Entity ticket = pending.get(i);
            Respawning respawning = PENDING.get(ticket);
            if (!respawning.paid) {
                continue;
            }
            respawning.remainingTicks--;
            if (respawning.remainingTicks > 0) {
                continue;
            }
            Entity commander = bringBack(respawning.slot);
            // Behind a shield that breaks at the first hit or runs out, whichever is first (G0053).
            SHIELD.get(commander).raiseFor(respawning.shieldTicks);
            spawner.markDead(ticket);
        }
    }

    /**
     * Puts him down at the rally point, jittered inside its tiles (G0009); at its middle if nowhere in
     * it will take him. Allocates, once per return.
     */
    private Entity bringBack(int slot) {
        TileRect area = RallyPoint.area(points, config, map);
        float radius = config.commander().radius();
        Scatter scatter = new Scatter(map, rng, radius, radius);
        Entity[] placed = new Entity[1];
        scatter.scatter(area, 1, radius, (x, y) -> placed[0] = spawner.spawnCommander(x, y, slot));
        Entity commander = placed[0];
        return commander != null ? commander : spawner.spawnCommander(area.centreX(map), area.centreY(map), slot);
    }

    /**
     * Every commander out, somebody waiting to come back, and nobody left to come back as: the level is
     * lost. A ticket already paid for is a man on his way, so it holds the verdict off.
     */
    private void judge(Level level) {
        if (level.failed || living.size() > 0 || pending.size() == 0 || level.reserve > 0) {
            return;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (PENDING.get(pending.get(i)).paid) {
                return;
            }
        }
        level.failed = true;
    }
}
