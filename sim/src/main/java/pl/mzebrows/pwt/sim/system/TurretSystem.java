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
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;

/**
 * Works every gun that somebody is on: brings it round, fires it, and holds whoever is aboard.
 *
 * <p><strong>Who is on what is settled here, once a tick, from the riders.</strong> A station's
 * {@code crewed} flag is cleared and re-established rather than trusted, so a man who was killed,
 * blown off or simply removed cannot leave a gun looking manned for the rest of the level. The rider
 * is the truth and the station publishes it; two latched halves of one fact is how they disagree.
 *
 * <p><strong>The gun fires where it is pointing, not where the man is pointing.</strong> That is the
 * whole of what a mount costs: a carried gun is aimed on the tick you aim it, and this one has to be
 * swung round, slower while the trigger is down. Holding fire to track and holding the trigger to
 * kill are therefore different decisions, which is what makes stepping round the side of one work.
 *
 * <p>Nothing else drives a man who is aboard - {@link Drivers#held} answers for every driver at once
 * - so this is the only thing writing his position, and it writes the gun's. He is not in the
 * pairwise path at all while he is on it, so nothing can shove, shoot or claw him; what can still
 * reach him is a blast, because a blast is a radius test over everything that can be hurt. The gun
 * going up under him is meant to catch him.
 *
 * <p>It runs immediately after the commander's own control and his swing, and before anything looks
 * around: a shot fired from a turret this tick is in the world before the enemies decide what they
 * can see, exactly as a melee sweep is.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because it walks two families
 * against each other - every station is cleared before any rider claims one.
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
     * <p>About ten degrees. The commander fires wherever his gun happens to be pointing, because
     * that is his decision to get wrong; a trooper waits until the shot would land, or a squad would
     * spray the arena every time something crossed behind it.
     */
    private static final float ON_TARGET = 0.985f;

    private final TileMap map;
    private final Spawner spawner;

    /** Scratch, so swinging a gun and firing it cost no allocation per tick. */
    private final float[] swung = new float[2];

    private final float[] muzzle = new float[2];

    private ImmutableArray<Entity> stations = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> riders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> quarry = new ImmutableArray<>(new Array<>());

    public TurretSystem(TileMap map, Spawner spawner) {
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
                // The gun was destroyed under him, or he was: a man who is off his feet is off the
                // gun too, and one that no longer exists cannot be ridden.
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
            if (TURRET.get(stations.get(i)).id == id) {
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
            fire(station, turret);
        }
    }

    /**
     * A trooper's half: it shoots the nearest thing the <em>gun</em> can reach, and gets off once
     * there has been nothing to shoot at for long enough.
     *
     * <p>The gun's range rather than the man's sight, which is the whole reason a trooper wants one:
     * on it, it answers things that are still crossing the ground towards the squad. What it will
     * not do is fire before it has come round - the commander may spray, a squad may not.
     *
     * <p>Leaving is the other half of that. A man sitting on a quiet gun is a man missing from the
     * squad, and nothing else would ever move him: while he is aboard, every system that decides
     * what a trooper should be doing skips him.
     */
    private void ofItsOwnJudgement(Entity rider, Entity station, Turret turret, Position at, float deltaTime) {
        Crew crew = CREW.get(rider);
        @Nullable Position target = nearestQuarry(at, turret.range);
        if (target == null) {
            crew.quietSeconds += deltaTime;
            if (crew.quietSeconds >= turret.quietSeconds) {
                Crews.leave(stations, rider);
            }
            return;
        }
        crew.quietSeconds = 0f;
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
            fire(station, turret);
        }
    }

    /** The nearest thing on the other side this gun could reach, or null if there is nothing. */
    private @Nullable Position nearestQuarry(Position at, float range) {
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
            if (distance < nearestDistance && map.clearLine(at.x, at.y, where.x, where.y)) {
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

    /** One shot, out of whichever muzzle is next. The cooldown is counted down by {@code CooldownSystem}. */
    private void fire(Entity station, Turret turret) {
        Weapon weapon = WEAPON.get(station);
        if (!Guns.ready(station, weapon)) {
            return;
        }
        Position at = POSITION.get(station);
        Muzzle.next(at.x, at.y, BODY.get(station).radius, weapon, MUZZLE_GAP, turret.aimX, turret.aimY, muzzle);
        spawner.spawnProjectile(muzzle[0], muzzle[1], turret.aimX, turret.aimY, weapon, FACTION.get(station).side);
        Guns.spend(station, weapon, weapon.cooldownSeconds);
    }
}
