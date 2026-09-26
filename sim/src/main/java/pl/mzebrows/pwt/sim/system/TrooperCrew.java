package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;

/** Getting on a free gun with something in its reach: the half of the cascade below aid. */
final class TrooperCrew {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);

    private final Rng nerve;
    private final Chances chances;
    private final float reach;
    private final float crewRadius;

    private ImmutableArray<Entity> stations = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());

    TrooperCrew(SimConfig config, Rng nerve) {
        this.nerve = nerve;
        this.chances = config.chances();
        this.reach = config.action().reach();
        this.crewRadius = config.turret().crewRadius();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        stations = engine.getEntitiesFor(
                Family.all(Turret.class, Position.class, Body.class).get());
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
    }

    /**
     * Goes and gets on a gun, if there is a free one near enough and something it could shoot from it.
     *
     * <p>Below going to fetch somebody and above the level's objectives: a gun is worth leaving a
     * walk for and never worth leaving a man on the ground for. In practice that ranking is what
     * "objectives take priority unless it is defensive" comes to - the guns a level puts down are
     * where it expects the fighting, so a trooper crossing the map to an objective simply never has
     * one within reach of its route.
     *
     * <p><strong>Only ever with something already in the gun's reach.</strong> A man does not sit
     * down at a gun for the view, and without that condition a trooper that had just got off a quiet
     * one would climb straight back on it - the dice are rolled once on the way in, so the loop would
     * never break itself.
     *
     * <p>What it heads for is a <em>place</em>, and boarding is decided again from the gun actually
     * in reach when it arrives (G0010).
     */
    boolean crews(Entity entity, Trooper trooper, Position at, float deltaTime) {
        @Nullable Entity station = worthGettingOn(at);
        if (station == null) {
            if (trooper.mode == Trooper.Mode.CREW) {
                // It was walking to a gun and there is no longer one worth walking to: somebody else
                // got there first, or there is nothing left in reach of it. Where it was headed was
                // decided for a reason that has stopped being true, so it is dropped rather than
                // walked out - a man standing over an occupied gun is a man missing from the squad.
                trooper.clearTarget();
            }
            return false;
        }
        if (trooper.mode != Trooper.Mode.CREW) {
            trooper.turretDue = Odds.spend(nerve, trooper.turretDue, chances, AiChance.MAN_A_TURRET, deltaTime);
            if (!Odds.spent(trooper.turretDue)) {
                return false;
            }
        }
        trooper.mode = Trooper.Mode.CREW;
        if (Crews.gapTo(entity, station) <= reach) {
            Crews.board(entity, station);
            trooper.clearTarget();
            return true;
        }
        Position where = POSITION.get(station);
        trooper.headFor(where.x, where.y);
        return true;
    }

    /** The nearest free gun within walking distance that has something in its own reach, or null. */
    private @Nullable Entity worthGettingOn(Position at) {
        @Nullable Entity nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < stations.size(); i++) {
            Entity station = stations.get(i);
            Turret turret = TURRET.get(station);
            if (turret.crewed) {
                continue;
            }
            Position where = POSITION.get(station);
            if (!Sight.withinRange(at.x, at.y, where.x, where.y, crewRadius)) {
                continue;
            }
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            float distance = toX * toX + toY * toY;
            if (distance < nearestDistance && somethingToShootFrom(where, turret.range)) {
                nearestDistance = distance;
                nearest = station;
            }
        }
        return nearest;
    }

    /** Whether anything on the other side is inside this gun's reach. */
    private boolean somethingToShootFrom(Position where, float range) {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (DYING.has(enemy) && DYING.get(enemy).begun) {
                continue;
            }
            Position standing = POSITION.get(enemy);
            if (Sight.withinRange(where.x, where.y, standing.x, standing.y, range)) {
                return true;
            }
        }
        return false;
    }
}
