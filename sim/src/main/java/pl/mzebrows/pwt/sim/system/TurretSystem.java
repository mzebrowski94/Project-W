package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Works every gun that somebody is on: brings it round, fires it, and holds whoever is aboard.
 *
 * <p><strong>Who is on what is settled here, once a tick, from the riders</strong>: {@code crewed} is
 * cleared and re-established, never trusted. <strong>The gun fires where it is pointing</strong>,
 * swung round slower while the trigger is down.
 *
 * <p>The only writer of a rider's position. He is out of the pairwise path; only a blast reaches him.
 */
public final class TurretSystem extends EntitySystem {

    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

    /** Clear of the mount's own body, so a shot never starts inside the thing that fired it. */
    private static final float MUZZLE_GAP = 0.1f;

    private static final float EPSILON = 1e-4f;

    /**
     * How nearly on target a gun has to be before a crew of its own judgement pulls the trigger.
     *
     * <p>About ten degrees. The commander fires wherever it points; a trooper waits.
     */
    private static final float ON_TARGET = 0.985f;

    private final TileMap map;
    private final Spawner spawner;
    private final Modifiers modifiers;

    /** A gun's own pattern. Zero-width for the gun a mount carries, and asked for all the same. */
    private final Rng scatter;

    /** Scratch, so swinging a gun and firing it cost no allocation per tick. */
    private final float[] swung = new float[2];

    private final float[] muzzle = new float[2];

    private ImmutableArray<Entity> stations = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> riders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> obstacles = new ImmutableArray<>(new Array<>());

    public TurretSystem(TileMap map, Spawner spawner, Modifiers modifiers, Rng scatter) {
        this.modifiers = modifiers;
        this.scatter = scatter;
        this.map = map;
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        stations =
                engine.getEntitiesFor(Family.all(Turret.class, Position.class, Body.class, Weapon.class, Faction.class)
                        .get());
        riders = engine.getEntitiesFor(Family.all(Crew.class, Position.class).get());
        quarry = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        obstacles = engine.getEntitiesFor(
                Family.all(Obstacle.class, Position.class, Body.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < stations.size(); i++) {
            TURRET.get(stations.get(i)).crewed = false;
        }
        for (int i = 0; i < riders.size(); i++) {
            Entity rider = riders.get(i);
            Crew crew = CREW.get(rider);
            if (!crew.aboard()) {
                continue;
            }
            @Nullable Entity station = stationOf(crew.station);
            if (station == null || Drivers.down(rider)) {
                // The gun or the man is gone, or he is off his feet.
                Crews.leave(stations, rider);
                continue;
            }
            Turret turret = TURRET.get(station);
            turret.crewed = true;
            work(rider, station, turret, deltaTime);
        }
    }

    /** The station carrying this id, or null if it is no longer on the field. */
    private @Nullable Entity stationOf(long id) {
        for (int i = 0; i < stations.size(); i++) {
            if (Handles.of(stations.get(i)) == id) {
                return stations.get(i);
            }
        }
        return null;
    }

    /** Holds the man at the gun, and then lets whoever he is decide what the gun does. */
    private void work(Entity rider, Entity station, Turret turret, float deltaTime) {
        Position at = POSITION.get(station);
        POSITION.get(rider).set(at.x, at.y);
        if (VELOCITY.has(rider)) {
            VELOCITY.get(rider).set(0f, 0f);
        }
        if (INTENT.has(rider)) {
            player(rider, station, turret, at, deltaTime);
            return;
        }
        ofItsOwnJudgement(rider, station, turret, at, deltaTime);
    }

    /** The commander's half: it goes where he points and fires when he asks, and no further. */
    private void player(Entity rider, Entity station, Turret turret, Position at, float deltaTime) {
        Intent intent = INTENT.get(rider);
        float toX = intent.aimX - at.x;
        float toY = intent.aimY - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance > EPSILON) {
            // Aim is a direction and never a distance, so how far away the point is cannot matter.
            swingOnto(turret, toX / distance, toY / distance, intent.firing, deltaTime);
        }
        if (intent.firing) {
            fire(station, turret, Players.credit(rider));
        }
    }

    /**
     * A trooper's half: it shoots the nearest thing the <em>gun</em> can reach, and gets off once
     * there has been nothing to shoot at for long enough.
     *
     * <p>The gun's range, not the man's sight; never before it has come round. Getting off is decided
     * here because every other trooper system skips a man aboard.
     */
    private void ofItsOwnJudgement(Entity rider, Entity station, Turret turret, Position at, float deltaTime) {
        Crew crew = CREW.get(rider);
        Weapon weapon = WEAPON.get(station);
        @Nullable Position target = nearestQuarry(at, turret.range, weapon.projectileRadius + weapon.muzzleOffset);
        if (target == null) {
            crew.quietTicks++;
            if (crew.quietTicks >= turret.quietTicks) {
                Crews.leave(stations, rider);
            }
            return;
        }
        crew.quietTicks = 0;
        float toX = target.x - at.x;
        float toY = target.y - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        float ontoX = toX / distance;
        float ontoY = toY / distance;
        swingOnto(turret, ontoX, ontoY, true, deltaTime);
        if (turret.aimX * ontoX + turret.aimY * ontoY >= ON_TARGET) {
            fire(station, turret, Players.credit(rider));
        }
    }

    /**
     * The nearest thing on the other side this gun could reach, or null if there is nothing.
     *
     * <p>Reach means a clear shot, boulders included: a man on a twin mount does not pour rounds into one.
     */
    private @Nullable Position nearestQuarry(Position at, float range, float shotWidth) {
        @Nullable Position nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < quarry.size(); i++) {
            Entity candidate = quarry.get(i);
            if (DYING.has(candidate) && DYING.get(candidate).begun) {
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
                    && map.clearLine(at.x, at.y, where.x, where.y)
                    && Sight.clearShot(obstacles, at.x, at.y, where.x, where.y, shotWidth)) {
                nearestDistance = distance;
                nearest = where;
            }
        }
        return nearest;
    }

    /** Brings the gun round, slower while the trigger is down - the whole cost of a mount. */
    private void swingOnto(Turret turret, float ontoX, float ontoY, boolean firing, float deltaTime) {
        Aim.swing(turret.aimX, turret.aimY, ontoX, ontoY, turret.turnRateFor(firing), deltaTime, swung);
        turret.aim(swung[0], swung[1]);
    }

    /**
     * One shot, out of whichever muzzle is next, credited to whoever crews the gun - a commander's
     * slot, nobody's for a trooper (G0044). The cooldown is counted down by {@code CooldownSystem}.
     */
    private void fire(Entity station, Turret turret, int credit) {
        Weapon weapon = WEAPON.get(station);
        if (!Guns.ready(station, weapon)) {
            return;
        }
        Position at = POSITION.get(station);
        Guns.volley(
                spawner,
                modifiers,
                scatter,
                station,
                weapon,
                at.x,
                at.y,
                BODY.get(station).radius,
                MUZZLE_GAP,
                turret.aimX,
                turret.aimY,
                FACTION.get(station).side,
                credit,
                muzzle);
        Guns.spend(modifiers, station, weapon, weapon.cooldownTicks);
    }
}
