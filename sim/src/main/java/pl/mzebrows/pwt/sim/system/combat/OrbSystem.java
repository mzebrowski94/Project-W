package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Orb;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Beams from every orb in the air (G0128), and sets off the one whose beams have landed its power.
 *
 * <p><strong>Up to its count of aliens within its reach, nearest first, re-picked every beam</strong>: each wait, one
 * shot of its beam's gun from its middle at each - the laser's kind of beam, a stream of short shots drawn as a line,
 * which stops on and lands on the first thing in its way. It never picks a man, and its shots are on the side it was
 * thrown from, so they pass our men harmlessly (the collision rule's own: nothing happens within a side). What they
 * land comes off its power where the blow is recorded, in {@code CollisionSystem}; spent, it is set off here, by dying
 * (G0018), so its blast is the one every shot leaves.
 *
 * <p>The aliens are filed in a {@link SpatialGrid} at most once a tick, on the first orb that beams - most ticks none is
 * out, and those cost nothing. Nothing in the per-orb loop allocates.
 *
 * <p>Straight after {@code LimpetSystem}: this tick's contacts have spent its power and stopped the orbs that touched
 * something, and an orb set off here reaches the death-reaction window this tick. A beam shot fired here first moves on
 * the next tick.
 */
public final class OrbSystem extends EntitySystem {

    private static final ComponentMapper<Orb> ORB = ComponentMapper.getFor(Orb.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    /** Below this an alien stands on the orb's middle and gives no way to beam along. */
    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;

    @NotState("scratch: the aliens filed by where they stand, refilled on the first orb of a tick that beams")
    private final SpatialGrid grid;

    @NotState("scratch: what the grid hands back for one orb")
    private final Array<Entity> nearby = new Array<>(false, 16);

    @NotState("scratch: the nearest aliens one orb beams at, and how far each is, squared")
    private final Entity[] picked;

    @NotState("scratch: see picked")
    private final float[] apart;

    @NotState("scratch: whether this tick's aliens are in the grid yet")
    private boolean filed;

    private ImmutableArray<Entity> orbs = new ImmutableArray<>(new Array<>());

    private ImmutableArray<Entity> aliens = new ImmutableArray<>(new Array<>());

    public OrbSystem(SimConfig config, TileMap map, Spawner spawner) {
        this.spawner = spawner;
        int most = 1;
        for (WeaponKind kind : WeaponKind.values()) {
            WeaponDef def = config.weapon(kind);
            if (def.beams()) {
                most = Math.max(most, def.beam().count());
            }
        }
        this.picked = new Entity[most];
        this.apart = new float[most];
        this.grid = new SpatialGrid(map.worldWidth(), map.worldHeight(), config.largestRadius() * 2f);
    }

    @Override
    public void addedToEngine(Engine engine) {
        orbs = engine.getEntitiesFor(
                Family.all(Orb.class, Position.class, Handle.class).get());
        aliens = engine.getEntitiesFor(Family.all(Faction.class, Damage.class, Position.class)
                .one(Health.class, Vitality.class)
                .exclude(Door.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        filed = false;
        for (int i = 0; i < orbs.size(); i++) {
            Entity entity = orbs.get(i);
            if (entity.isScheduledForRemoval() || DEAD.has(entity)) {
                continue;
            }
            Orb orb = ORB.get(entity);
            if (orb.spent()) {
                // Its beams have landed its power: it goes off where it is, by dying (G0018).
                spawner.markDead(entity);
                continue;
            }
            if (orb.remainingTicks > 0) {
                orb.remainingTicks--;
                continue;
            }
            if (beam(entity, orb) > 0) {
                orb.remainingTicks = orb.cooldownTicks;
            }
        }
    }

    /** One shot at each of the nearest aliens in its reach; how many it beamed at. */
    private int beam(Entity entity, Orb orb) {
        if (orb.count <= 0) {
            return 0;
        }
        fileTheAliens();
        Position at = POSITION.get(entity);
        int found = pick(at.x, at.y, orb);
        long handle = HANDLE.get(entity).id;
        for (int k = 0; k < found; k++) {
            Position where = POSITION.get(picked[k]);
            float x = where.x - at.x;
            float y = where.y - at.y;
            float length = (float) Math.sqrt(x * x + y * y);
            spawner.spawnBeamShot(
                    at.x, at.y, x / length, y / length, orb.gun, orb.beamDamage, orb.side, orb.credit, handle);
        }
        return found;
    }

    /** Fills {@link #picked} with the nearest aliens on their feet within its reach, nearest first; how many. */
    private int pick(float x, float y, Orb orb) {
        int most = Math.min(orb.count, picked.length);
        int found = 0;
        float reach = orb.reach * orb.reach;
        grid.near(x, y, orb.reach, nearby);
        for (int i = 0; i < nearby.size; i++) {
            Entity alien = nearby.get(i);
            if (Life.dying(alien)) {
                continue;
            }
            Position at = POSITION.get(alien);
            float dx = at.x - x;
            float dy = at.y - y;
            float squared = dx * dx + dy * dy;
            if (squared > reach || squared <= EPSILON) {
                continue;
            }
            // Insertion into the few kept, nearest first; a tie keeps the one met first.
            int slot = found < most ? found : most;
            while (slot > 0 && apart[slot - 1] > squared) {
                if (slot < most) {
                    picked[slot] = picked[slot - 1];
                    apart[slot] = apart[slot - 1];
                }
                slot--;
            }
            if (slot < most) {
                picked[slot] = alien;
                apart[slot] = squared;
                if (found < most) {
                    found++;
                }
            }
        }
        return found;
    }

    /** Every alien on the field into the grid, once a tick, on the first orb that needs them. */
    private void fileTheAliens() {
        if (filed) {
            return;
        }
        filed = true;
        grid.clear();
        for (int i = 0; i < aliens.size(); i++) {
            Entity entity = aliens.get(i);
            if (FACTION.get(entity).side != Side.ALIEN) {
                continue;
            }
            Position at = POSITION.get(entity);
            grid.insert(entity, at.x, at.y);
        }
    }
}
