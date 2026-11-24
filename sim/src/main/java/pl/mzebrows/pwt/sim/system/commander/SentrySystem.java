package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.collision.SpatialGrid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Sentry;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.system.combat.Guns;
import pl.mzebrows.pwt.sim.system.query.Aim;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * Works every sentry on the field (G0129): counts its life down, brings it round onto the nearest alien it has a clear
 * shot at within its reach and fires when it is on target, and stuns the aliens round it as it goes off.
 *
 * <p><strong>Nobody crews it.</strong> It is a turret's turn - a rate, slower while it fires - with a trooper-crewed
 * turret's judgement ({@code TurretSystem.ON_TARGET}, the one place it is written), and nothing else: nobody gets on it, nobody hauls it. Its shots are on the players' side, so the
 * collision rule passes them through our men; no fouled-line test is needed for that.
 *
 * <p><strong>It goes off by dying</strong>, one path for both endings: out of time it is marked dead here; destroyed, it
 * was marked by {@code DamageSystem} earlier in the tick. Either way, while its body is still on the field, this stuns
 * the aliens round it through {@link Surges#stunRound} - the dash surge's harmless stunning blow, never a man (G0108) -
 * and its small blast is its {@code BlastsOnDeath}, left by {@code BlastSystem} after it.
 *
 * <p>The aliens are filed in a {@link SpatialGrid} at most once a tick, on the first sentry that looks. Nothing in the
 * per-sentry loop allocates.
 *
 * <p>Inside the death window, after {@code RespawnSystem} and before {@code ProjectileBoundsSystem}: after
 * {@code DamageSystem}, so a sentry destroyed this tick is dead here; before {@code BlastSystem}, so one that runs out
 * of time here goes off this tick; before {@code UpgradeSystem}, since it fires through {@link Guns}, which reads the
 * ledger. A shot fired here first moves on the next tick, and the stun it lands is settled on the next, as a blast's is.
 */
public final class SentrySystem extends EntitySystem {

    private static final ComponentMapper<Sentry> SENTRY = ComponentMapper.getFor(Sentry.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    /** Below this a target stands on the sentry's middle and gives no way to aim. */
    private static final float EPSILON = 1e-4f;

    private final Solid solid;
    private final Spawner spawner;
    private final Guns guns;
    private final Surges surges;

    @NotState("scratch: the aliens filed by where they stand, refilled on the first sentry of a tick that looks")
    private final SpatialGrid grid;

    @NotState("scratch: what the grid hands back for one sentry")
    private final Array<Entity> nearby = new Array<>(false, 16);

    @NotState("scratch: a sentry's aim brought round this tick")
    private final float[] swung = new float[2];

    @NotState("scratch: whether this tick's aliens are in the grid yet")
    private boolean filed;

    private ImmutableArray<Entity> sentries = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> obstacles = new ImmutableArray<>(new Array<>());

    public SentrySystem(SimConfig config, TileMap map, Solid solid, Spawner spawner, Guns guns, Surges surges) {
        this.solid = solid;
        this.spawner = spawner;
        this.guns = guns;
        this.surges = surges;
        this.grid = new SpatialGrid(
                map.worldWidth(), map.worldHeight(), Math.max(config.sentry().range(), config.largestRadius() * 2f));
    }

    @Override
    public void addedToEngine(Engine engine) {
        sentries =
                engine.getEntitiesFor(Family.all(Sentry.class, Position.class, Body.class, Weapon.class, Faction.class)
                        .get());
        quarry = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        obstacles = engine.getEntitiesFor(
                Family.all(Obstacle.class, Position.class, Body.class).get());
        surges.bind(engine);
    }

    @Override
    public void update(float deltaTime) {
        surges.tick();
        filed = false;
        for (int i = 0; i < sentries.size(); i++) {
            Entity entity = sentries.get(i);
            Sentry sentry = SENTRY.get(entity);
            if (DEAD.has(entity)) {
                // Destroyed this tick: it goes off while its body is still here (G0018).
                goOff(entity, sentry);
                continue;
            }
            if (entity.isScheduledForRemoval()) {
                continue;
            }
            if (--sentry.remainingTicks <= 0) {
                // Out of time: the same death, the same blast, the same stun.
                spawner.markDead(entity);
                goOff(entity, sentry);
                continue;
            }
            work(entity, sentry, deltaTime);
        }
    }

    /** The stun round it as it goes off; the blast is its death reaction's. */
    private void goOff(Entity entity, Sentry sentry) {
        Position at = POSITION.get(entity);
        surges.stunRound(at.x, at.y, sentry.stunRadius, sentry.stunTicks, sentry.slot);
    }

    /** Brings it round onto the nearest alien it can hit, and fires once it is on target. */
    private void work(Entity entity, Sentry sentry, float deltaTime) {
        Position at = POSITION.get(entity);
        Weapon weapon = WEAPON.get(entity);
        @Nullable Position target = nearest(at, sentry.range, weapon.projectileRadius + weapon.muzzleOffset);
        if (target == null) {
            return;
        }
        float toX = target.x - at.x;
        float toY = target.y - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        float ontoX = toX / distance;
        float ontoY = toY / distance;
        Aim.swing(sentry.aimX, sentry.aimY, ontoX, ontoY, sentry.turnRateFor(true), deltaTime, swung);
        sentry.aim(swung[0], swung[1]);
        if (sentry.aimX * ontoX + sentry.aimY * ontoY >= TurretSystem.ON_TARGET && guns.ready(entity)) {
            guns.pull(entity, sentry.aimX, sentry.aimY, sentry.slot);
        }
    }

    /**
     * The nearest alien on its feet within reach that a shot from here would reach - rock and boulders in the way rule
     * it out, as for a crewed turret - or null if there is none.
     */
    private @Nullable Position nearest(Position at, float range, float shotWidth) {
        fileTheAliens();
        grid.near(at.x, at.y, range, nearby);
        @Nullable Position nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < nearby.size; i++) {
            Entity candidate = nearby.get(i);
            if (Life.dying(candidate) || Drivers.burrowed(candidate)) {
                continue;
            }
            Position where = POSITION.get(candidate);
            if (!Sight.withinRange(at.x, at.y, where.x, where.y, range)) {
                continue;
            }
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            float distance = toX * toX + toY * toY;
            if (distance < nearestDistance
                    && solid.clearLine(at.x, at.y, where.x, where.y)
                    && Sight.clearShot(obstacles, at.x, at.y, where.x, where.y, shotWidth)) {
                nearestDistance = distance;
                nearest = where;
            }
        }
        return nearest;
    }

    private void fileTheAliens() {
        if (filed) {
            return;
        }
        filed = true;
        grid.clear();
        for (int i = 0; i < quarry.size(); i++) {
            Entity alien = quarry.get(i);
            Position at = POSITION.get(alien);
            grid.insert(alien, at.x, at.y);
        }
    }
}
