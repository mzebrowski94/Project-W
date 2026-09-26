package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * The level's points: holding one that is taken, walking to the nearest one that is not, and splitting
 * the watch between the men standing on one. The half of {@link TrooperMindSystem}'s cascade below the
 * errands, and above roaming.
 *
 * <p><strong>Nearest means nearest by route</strong> (2.e): each point owns a flow field built for a
 * trooper's body, once, because points neither move nor go away.
 */
final class TrooperObjective {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);

    /** How far inside a point counts as standing on it, as a fraction of its radius. */
    private static final float INSIDE_THE_EDGE = 0.7f;

    private final TileMap map;
    private final Rng rng;
    private final float bodyRadius;
    private final float holdSeconds;
    private final float holdJitterSeconds;
    private final float rallyRadius;

    /** One field per control point, parallel to the family. Rebuilt only if the set of points changes. */
    @NotState("cache of one flow field per control point, a pure function of the map and where the points are")
    private FlowField[] routes = new FlowField[0];

    /** Scratch: which point each trooper is standing on this tick, in family order. */
    @NotState("scratch rebuilt every tick by splitIntoSectors")
    private int[] standingOnPoint = new int[0];

    /** Scratch: how many men are watching from each point this tick, parallel to the family. */
    @NotState("scratch rebuilt every tick by splitIntoSectors")
    private int[] menOnPoint = new int[0];

    /** Scratch: how many of them have been handed a sector so far. */
    @NotState("scratch rebuilt every tick by splitIntoSectors")
    private int[] placed = new int[0];

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    TrooperObjective(SimConfig config, TileMap map, Rng rng) {
        this.map = map;
        this.rng = rng;
        this.bodyRadius = config.trooper().radius();
        this.holdSeconds = config.trooper().holdSeconds();
        this.holdJitterSeconds = config.trooper().holdJitterSeconds();
        this.rallyRadius = config.trooper().rallyRadius();
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Position.class).get());
        points = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    /**
     * Heads for the nearest point not yet taken, and settles on it once standing there - or reports
     * that there is none left to go to.
     */
    boolean advances(Trooper trooper, Position at) {
        int nearest = nearestUncapturedPoint(at);
        if (nearest < 0) {
            return false;
        }
        Entity point = points.get(nearest);
        Position where = POSITION.get(point);
        trooper.headFor(where.x, where.y);
        // Already standing on it: hold this spot and watch, rather than shuffling towards
        // the middle. Everyone inside the radius counts equally, so walking further in buys
        // nothing and a squad piled on one spot cannot see past itself.
        boolean arrived = standingOn(point, at);
        if (arrived) {
            // The walk is over, so the order that paid for it is spent. One "forward" buys
            // one point, and the squad settles again on the one it has just reached.
            trooper.sentOn = false;
        }
        trooper.mode = arrived ? Trooper.Mode.SECURE : Trooper.Mode.ADVANCE;
        return true;
    }

    /**
     * Splits the circle round each point into sectors, one for each man standing on it.
     *
     * <p><strong>A bearing each, drawn at random, left whole sides unwatched.</strong> A trooper sees
     * inside an arc, and four arcs pointed anywhere cover what they happen to cover - so a nest behind
     * the squad was never found at any range. Each man on a point is handed a run of compass slots
     * instead, the runs together making the whole circle, and turns through his own run a scan at a
     * time; one man alone turns through all of it.
     *
     * <p>Who gets which run is their order in the family, which is spawn order and the same on every
     * replay. A man arriving, leaving, or turning to fight reshuffles the runs on the same tick, so the
     * ones still watching cover for him.
     *
     * <p>Two passes over the troopers and none over pairs of them: which point each stands on and how
     * many stand on each, then each man's place among them.
     */
    void splitIntoSectors() {
        if (standingOnPoint.length < troopers.size()) {
            standingOnPoint = new int[troopers.size()];
        }
        if (menOnPoint.length < points.size()) {
            menOnPoint = new int[points.size()];
            placed = new int[points.size()];
        }
        for (int p = 0; p < points.size(); p++) {
            menOnPoint[p] = 0;
            placed[p] = 0;
        }
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            Trooper trooper = TROOPER.get(entity);
            standingOnPoint[i] = trooper.mode == Trooper.Mode.SECURE && !Drivers.held(entity)
                    ? pointUnder(POSITION.get(entity))
                    : -1;
            if (standingOnPoint[i] >= 0) {
                menOnPoint[standingOnPoint[i]]++;
            }
        }
        for (int i = 0; i < troopers.size(); i++) {
            Trooper trooper = TROOPER.get(troopers.get(i));
            int point = standingOnPoint[i];
            if (point < 0) {
                trooper.sectorFrom = -1;
                trooper.sectorSpan = 0;
                continue;
            }
            int place = placed[point]++;
            int count = menOnPoint[point];
            int from = place * Compass.SLOTS / count;
            int to = (place + 1) * Compass.SLOTS / count;
            trooper.sectorFrom = from;
            trooper.sectorSpan = Math.max(1, to - from);
        }
    }

    /** Which point this position counts as standing on, or {@code -1}. The first, if they overlap. */
    private int pointUnder(Position at) {
        for (int p = 0; p < points.size(); p++) {
            if (standingOn(points.get(p), at)) {
                return p;
            }
        }
        return -1;
    }

    /**
     * Whether this trooper is far enough inside the point to stop walking.
     *
     * <p>Short of the edge rather than at it: a trooper that stopped exactly on the boundary would be
     * one shove away from dropping out of the radius, and the capture resets the moment the last
     * holder leaves. The margin is what makes a squad holding a point robust to being jostled.
     */
    private static boolean standingOn(Entity point, Position at) {
        ControlPoint held = POINT.get(point);
        Position where = POSITION.get(point);
        return Sight.withinRange(at.x, at.y, where.x, where.y, held.radius * INSIDE_THE_EDGE);
    }

    /**
     * Holds ground the squad has already taken, unless it has been told to push on.
     *
     * <p><strong>A point taken is a point held.</strong> Walking off the moment the bar fills meant
     * the squad ran the level on its own and the commander spent it catching up; waiting means ground
     * the squad has taken is ground somebody is still standing on, and the level advances at his pace.
     * "Forward" sends them on at once, and because an order reaches only what is within earshot
     * (G0017), that means going to them first. Since G0040 they also go on by themselves - but only
     * to catch up with a commander who is already ahead of them, never to run ahead of one.
     *
     * <p>Only on a point it has <em>taken</em>. A trooper anywhere else advances as it always did, so
     * the first walk of a level needs no order, and a point somebody is still capturing is still
     * being captured.
     *
     * <p>{@code SECURE} rather than a mode of its own: standing on a point, stationary, watching
     * outward and not shuffling towards the middle is the same posture whether the bar is filling or
     * full, and the renderer already draws it.
     */
    boolean holdsWhatItHasTaken(Trooper trooper, Position at, float deltaTime) {
        if (trooper.sentOn) {
            return false;
        }
        int taken = takenPointUnder(at);
        if (taken < 0) {
            return false;
        }
        if (movesOn(trooper, at, taken, deltaTime)) {
            // Permission to leave, exactly as "forward" gives it: spent on reaching the next point.
            trooper.sentOn = true;
            trooper.holding = false;
            return false;
        }
        trooper.mode = Trooper.Mode.SECURE;
        trooper.clearTarget();
        return true;
    }

    /**
     * Whether a man holding a taken point should go on to the next one without being told (G0040).
     *
     * <p><strong>The squad follows the war; it never runs ahead of it.</strong> A commander already near
     * the next point sends the men on at once - waiting to be fetched by somebody who is standing where
     * you are going reads as men who have not noticed the fight moving. Otherwise they hold for their
     * own while, and then go only if a commander has gone on ahead of them by route and none is on this
     * point with them. A commander behind them keeps them where they are however long they have held:
     * that is G0029's reason, kept - the level still advances at his pace, not at theirs.
     *
     * <p>Every commander on his feet counts, whoever ordered what. "Forward" is untouched and still
     * sends them at once.
     */
    private boolean movesOn(Trooper trooper, Position at, int taken, float deltaTime) {
        int next = nearestUncapturedPoint(at);
        if (next < 0) {
            return false;
        }
        if (aCommanderAt(points.get(next))) {
            return true;
        }
        if (!trooper.holding) {
            trooper.holding = true;
            trooper.holdRemaining =
                    Ticks.of(holdSeconds + rng.nextFloat(-holdJitterSeconds, holdJitterSeconds), deltaTime);
        }
        trooper.holdRemaining--;
        return trooper.holdRemaining <= 0 && !aCommanderAt(points.get(taken)) && aCommanderAheadOf(at, next);
    }

    /** Whether any commander on his feet is within the rally radius of this point. */
    private boolean aCommanderAt(Entity point) {
        Position where = POSITION.get(point);
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (Drivers.down(commander)) {
                continue;
            }
            Position standing = POSITION.get(commander);
            if (Sight.withinRange(standing.x, standing.y, where.x, where.y, rallyRadius)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any commander on his feet is a shorter walk from that point than this position is. */
    private boolean aCommanderAheadOf(Position at, int next) {
        FlowField route = routes[next];
        int own = route.stepsTo(map.colAt(at.x), map.rowAt(at.y));
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (Drivers.down(commander)) {
                continue;
            }
            Position standing = POSITION.get(commander);
            int his = route.stepsTo(map.colAt(standing.x), map.rowAt(standing.y));
            if (his >= 0 && his < own) {
                return true;
            }
        }
        return false;
    }

    /** Which taken point this position counts as standing on, or {@code -1}. */
    int takenPointUnder(Position at) {
        for (int p = 0; p < points.size(); p++) {
            Entity point = points.get(p);
            if (POINT.get(point).captured && standingOn(point, at)) {
                return p;
            }
        }
        return -1;
    }

    /**
     * Which uncaptured point is the shortest walk away, or {@code -1} if none can be reached.
     *
     * <p>Ties go to whichever was spawned first, because the family is in insertion order and the
     * comparison is strict - so two points equally far away pull the same way on every replay.
     */
    private int nearestUncapturedPoint(Position at) {
        int col = map.colAt(at.x);
        int row = map.rowAt(at.y);
        int nearest = -1;
        int shortest = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            if (POINT.get(points.get(i)).captured) {
                continue;
            }
            int steps = routes[i].stepsTo(col, row);
            if (steps >= 0 && steps < shortest) {
                shortest = steps;
                nearest = i;
            }
        }
        return nearest;
    }

    /** Builds one field per point, aimed at that point, the first time there are any. */
    void buildRoutesIfNeeded() {
        if (routes.length == points.size()) {
            return;
        }
        routes = new FlowField[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Position where = POSITION.get(points.get(i));
            FlowField field = FlowField.over(map, bodyRadius);
            field.rebuildFor(map.colAt(where.x), map.rowAt(where.y));
            routes[i] = field;
        }
    }
}
