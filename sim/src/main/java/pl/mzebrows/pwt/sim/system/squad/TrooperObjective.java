package pl.mzebrows.pwt.sim.system.squad;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Site;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.query.Compass;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

/**
 * The level's points - and its worked sites, walked to and stood round as points are (G0111): holding one that is
 * taken, walking to the nearest one that is not, and splitting the watch between the men standing on one. At a worked
 * site the men stand at arm's length of it; which one of them works it is {@code WorkedSiteSystem}'s. The half of {@link TrooperMindSystem}'s cascade below the
 * errands, and above roaming.
 *
 * <p><strong>Nearest means nearest by route</strong> (2.e): each point owns a flow field built for a
 * trooper's body, once, because points neither move nor go away.
 *
 * <p><strong>Ground held afterwards waits for a commander</strong> (G0061): a point that holds afterwards is
 * walked to and taken as any point is, but only while a commander on his feet is in its biome - the last
 * fight starts when he gets there. Once it is taken it is stood round, on a ring {@code
 * sim.trooper.postRingRadius} round its middle, and with nothing left to take it is where every man goes next.
 *
 * <p><strong>The capture crowd</strong> (G0086): once {@code sim.trooper.captureCrowd} men are settled on a point
 * being taken - commanders on it, troopers on it securing it - a trooper who comes to it goes on to the next point the
 * squad would take after it and holds there: one point ahead, never past it (G0040's line), so a point somebody was
 * sent on to is never itself passed over. Men who settle on it together past a full crowd are its spares, the last in
 * spawn order, and go on the same way. A man sent on keeps to the point chosen for him ({@code Trooper.sentAhead})
 * until he stands on it or it is taken. With none, he wanders round this one. Counted once a tick, men against points;
 * never man against man.
 */
final class TrooperObjective {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Site> SITE = ComponentMapper.getFor(Site.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    /** How far inside a point counts as standing on it, as a fraction of its radius. */
    private static final float INSIDE_THE_EDGE = 0.7f;

    private final TileMap map;
    private final Rng rng;
    private final float bodyRadius;
    private final float holdSeconds;
    private final float holdJitterSeconds;
    private final float rallyRadius;
    private final float postRingRadius;
    private final int captureCrowd;

    /** How far past a worked site's edge a man counts as at it: his body and his arm's length (G0111). */
    private final float atAWorkedSite;

    /** Where a crowd's spare man with no next point wanders round it. */
    private final TrooperRoam roam;

    /** One field per control point, parallel to the family. Rebuilt only if the set of points changes. */
    @NotState("cache of one flow field per control point, a pure function of the map and where the points are")
    private FlowField[] routes = new FlowField[0];

    /**
     * Parallel to {@link #routes}: for ground held afterwards, the column of its <em>way in</em> - the tile just outside
     * its biome that its route enters by - or {@code -1} (for every other point, and for one no route reaches from
     * outside).
     */
    @NotState("cache of the way into each held point's biome, a pure function of the map and where the points are")
    private int[] wayInCol = new int[0];

    /** Parallel to {@link #wayInCol}: the row of the same tile. */
    @NotState("cache of the way into each held point's biome, a pure function of the map and where the points are")
    private int[] wayInRow = new int[0];

    /**
     * Scratch: where each trooper is watching from this tick, in family order - a point's index, the number of
     * points plus a door's index for a man waiting at a door, or {@code -1}.
     */
    @NotState("scratch rebuilt every tick by splitIntoSectors")
    private int[] standingOnPoint = new int[0];

    /** Scratch: how many men are watching from each point, then each door, this tick. */
    @NotState("scratch rebuilt every tick by splitIntoSectors")
    private int[] menOnPoint = new int[0];

    /** Scratch: how many of them have been handed a sector so far. */
    @NotState("scratch rebuilt every tick by splitIntoSectors")
    private int[] placed = new int[0];

    /** Scratch: how many men are settled on each point being taken this tick - its crowd, up to a full one. */
    @NotState("scratch rebuilt every tick by countCrowds")
    private int[] crowdOn = new int[0];

    /** Scratch: for each crowded point, the next point the squad would take after it, or {@code -1}. */
    @NotState("scratch rebuilt every tick by countCrowds")
    private int[] nextAfter = new int[0];

    /** Scratch: whether a point is the one a crowded point sends its spare men on to - never passed over itself. */
    @NotState("scratch rebuilt every tick by countCrowds")
    private boolean[] onePointAhead = new boolean[0];

    /** Scratch: the troopers settled on a point whose crowd was already full, in family order. */
    @NotState("scratch rebuilt every tick by countCrowds")
    private final Array<Entity> spares = new Array<>();

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> men = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());

    TrooperObjective(SimConfig config, TileMap map, Rng rng, TrooperRoam roam) {
        this.map = map;
        this.rng = rng;
        this.bodyRadius = config.trooper().radius();
        this.holdSeconds = config.trooper().holdSeconds();
        this.holdJitterSeconds = config.trooper().holdJitterSeconds();
        this.rallyRadius = config.trooper().rallyRadius();
        this.postRingRadius = config.trooper().postRingRadius();
        this.captureCrowd = config.trooper().captureCrowd();
        this.atAWorkedSite = config.trooper().radius() + config.action().reach();
        this.roam = roam;
    }

    /** Takes the views it reads from the engine, once, as its system is added. */
    void bind(Engine engine) {
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Position.class).get());
        // A worked site is walked to and stood round as a point is (G0111): one family, spawn order.
        points = engine.getEntitiesFor(Family.one(ControlPoint.class, WorkedSite.class)
                .all(Position.class)
                .get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
        doors = engine.getEntitiesFor(Family.all(Door.class, Handle.class).get());
        men = engine.getEntitiesFor(Life.onTheField());
    }

    /**
     * Counts the crowd settled on each point being taken, settles where each crowded point sends its spare men, and
     * sends on the troopers settled past a full crowd (G0086). Once a tick, before any man decides: men against
     * points, then points against points. Commanders first, so a man never crowds out a commander; then troopers
     * securing it, in family order, so who is a spare is the same answer on every machine.
     */
    void countCrowds() {
        int count = points.size();
        if (crowdOn.length < count) {
            crowdOn = new int[count];
            nextAfter = new int[count];
            onePointAhead = new boolean[count];
        }
        for (int p = 0; p < count; p++) {
            crowdOn[p] = 0;
            nextAfter[p] = -1;
            onePointAhead[p] = false;
        }
        spares.clear();
        if (captureCrowd <= 0) {
            return;
        }
        for (int i = 0; i < men.size(); i++) {
            Entity man = men.get(i);
            if (COMMANDER.has(man) && !Drivers.held(man) && POSITION.has(man)) {
                int point = pointBeingTakenUnder(POSITION.get(man));
                if (point >= 0) {
                    crowdOn[point]++;
                }
            }
        }
        countTroopers(true);
        countTroopers(false);
        for (int p = 0; p < count; p++) {
            if (crowdOn[p] >= captureCrowd) {
                Position where = POSITION.get(points.get(p));
                nextAfter[p] = nearestUncapturedPoint(map.colAt(where.x), map.rowAt(where.y), p);
                if (nextAfter[p] >= 0) {
                    onePointAhead[nextAfter[p]] = true;
                }
            }
        }
        for (int i = 0; i < spares.size; i++) {
            Entity spare = spares.get(i);
            int point = pointBeingTakenUnder(POSITION.get(spare));
            if (!onePointAhead[point] && nextAfter[point] >= 0) {
                TROOPER.get(spare).sentAhead = Handles.of(points.get(nextAfter[point]));
            }
        }
    }

    /**
     * Counts into each point's crowd the troopers settled on it - securing it ({@code securing}), or else on their way
     * onto it - up to a full crowd; the rest are its spares. Those securing it first, so a man walking in never
     * displaces one already standing there.
     */
    private void countTroopers(boolean securing) {
        for (int i = 0; i < men.size(); i++) {
            Entity man = men.get(i);
            if (!TROOPER.has(man) || Drivers.held(man) || !POSITION.has(man)) {
                continue;
            }
            Trooper trooper = TROOPER.get(man);
            if ((trooper.mode == Trooper.Mode.SECURE) != securing) {
                continue;
            }
            int point = settledOn(trooper, POSITION.get(man));
            if (point < 0) {
                continue;
            }
            if (crowdOn[point] < captureCrowd) {
                crowdOn[point]++;
            } else {
                spares.add(man);
            }
        }
    }

    /**
     * The point being taken this trooper is settled on, or {@code -1}: inside its whole radius, and securing it or
     * walking onto it - not merely crossing it, nor wandering round it. A man shoved off his spot is still on it.
     */
    private int settledOn(Trooper trooper, Position at) {
        int point = pointBeingTakenUnder(at);
        if (point < 0) {
            return -1;
        }
        if (trooper.mode == Trooper.Mode.SECURE) {
            return point;
        }
        Position where = POSITION.get(points.get(point));
        boolean walkingOnto = trooper.mode == Trooper.Mode.ADVANCE
                && trooper.hasTarget
                && trooper.targetX == where.x
                && trooper.targetY == where.y;
        return walkingOnto ? point : -1;
    }

    /**
     * Heads for the nearest point not yet taken, and settles on it once standing there - or, with none
     * left, for ground held afterwards unless he is pushing out from it - or reports that there is
     * nowhere to go. A man the capture crowd sent on heads for the point chosen for him instead (G0086).
     */
    boolean advances(Trooper trooper, Position at) {
        int nearest = sentAheadTo(trooper);
        if (nearest < 0) {
            nearest = nearestUncapturedPoint(at);
            if (nearest >= 0 && crowdedFor(nearest, trooper, at)) {
                if (nextAfter[nearest] < 0) {
                    // Nowhere further to go: round this one, as a squad with nothing left to take roams.
                    Position where = POSITION.get(points.get(nearest));
                    roam.exploresAround(trooper, at, where.x, where.y, radiusOf(points.get(nearest)), TileMap.NO_BIOME);
                    return true;
                }
                nearest = nextAfter[nearest];
                trooper.sentAhead = Handles.of(points.get(nearest));
            }
        }
        if (nearest < 0 && !trooper.pushingForward()) {
            nearest = heldPoint();
        }
        if (nearest < 0) {
            return patrolsTheWayIn(trooper, at);
        }
        Entity point = points.get(nearest);
        Position where = POSITION.get(point);
        trooper.headFor(where.x, where.y);
        // Already standing on it: hold this spot and watch, not shuffle towards the middle.
        boolean arrived = standingOn(point, at);
        if (arrived) {
            // One "forward" buys one point: spent on arrival. So does being sent on by a crowd.
            trooper.sentOn = false;
            trooper.sentAhead = Handle.NOBODY;
        }
        trooper.mode = arrived ? Trooper.Mode.SECURE : Trooper.Mode.ADVANCE;
        return true;
    }

    /**
     * The point the capture crowd sent this man on to while it is still there to take, or {@code -1} - clearing it
     * once it is taken or gone (G0086).
     */
    private int sentAheadTo(Trooper trooper) {
        if (trooper.sentAhead == Handle.NOBODY) {
            return -1;
        }
        for (int p = 0; p < points.size(); p++) {
            Entity point = points.get(p);
            if (Handles.of(point) == trooper.sentAhead && !taken(point)) {
                return p;
            }
        }
        trooper.sentAhead = Handle.NOBODY;
        return -1;
    }

    /**
     * Splits the circle round each point into sectors, one for each man standing on it - and round each shut
     * door, one for each man its trooper keeps waiting (G0069): the rest keep a lookout.
     *
     * <p>Each man gets a run of compass slots, the runs together making the whole circle, handed out in
     * family (spawn) order and reshuffled on the same tick anyone arrives, leaves or turns to fight. Two
     * passes, no pairs.
     */
    void splitIntoSectors() {
        if (standingOnPoint.length < troopers.size()) {
            standingOnPoint = new int[troopers.size()];
        }
        int groups = points.size() + doors.size();
        if (menOnPoint.length < groups) {
            menOnPoint = new int[groups];
            placed = new int[groups];
        }
        for (int p = 0; p < groups; p++) {
            menOnPoint[p] = 0;
            placed[p] = 0;
        }
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            Trooper trooper = TROOPER.get(entity);
            standingOnPoint[i] = trooper.mode == Trooper.Mode.SECURE && !Drivers.held(entity)
                    ? pointUnder(POSITION.get(entity))
                    : -1;
            if (standingOnPoint[i] < 0 && !Drivers.held(entity)) {
                standingOnPoint[i] = doorWaitedAt(entity, trooper);
            }
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

    /**
     * The group of the shut door this man is waiting at - his way shut by it and it gone to somebody else - or
     * {@code -1}. Read from last tick's walk, which is when he stopped.
     */
    private int doorWaitedAt(Entity entity, Trooper trooper) {
        if (trooper.door == Handle.NOBODY) {
            return -1;
        }
        for (int d = 0; d < doors.size(); d++) {
            Entity door = doors.get(d);
            long opener = DOOR.get(door).trooper;
            if (Handles.of(door) == trooper.door && opener != Handle.NOBODY && opener != Handles.of(entity)) {
                return points.size() + d;
            }
        }
        return -1;
    }

    /**
     * Whether a man here, coming to this point, finds it already crowded (G0086): a full crowd settled on it, and it
     * not the point a crowd elsewhere sent men on to - one point ahead, never past it. A man already settled on it
     * stays: he is one of the crowd, or a spare {@link #countCrowds} has sent on. One crossing it or wandering round it
     * is not settled and never counted, and men arriving on one tick are counted before any of them is a spare.
     */
    private boolean crowdedFor(int point, Trooper trooper, Position at) {
        if (captureCrowd <= 0 || onePointAhead[point]) {
            return false;
        }
        if (settledOn(trooper, at) == point) {
            return false;
        }
        return crowdOn[point] >= captureCrowd;
    }

    /**
     * Which point being taken this position stands inside, its whole radius, or {@code -1}: the measure a point is
     * held by. The first, if they overlap.
     */
    private int pointBeingTakenUnder(Position at) {
        for (int p = 0; p < points.size(); p++) {
            Entity point = points.get(p);
            Position where = POSITION.get(point);
            if (!taken(point) && Sight.withinRange(at.x, at.y, where.x, where.y, radiusOf(point))) {
                return p;
            }
        }
        return -1;
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
     * Whether this trooper is far enough in to stop walking.
     *
     * <p>Short of the edge, so one shove does not drop it out and reset the capture - or, round ground
     * held afterwards that has been taken, on the ring rather than inside the point at all (G0061).
     */
    private boolean standingOn(Entity point, Position at) {
        Position where = POSITION.get(point);
        float reach;
        if (!POINT.has(point)) {
            // At arm's length of its edge, short of it, so the man who works it can reach it (G0111).
            reach = BODY.get(point).radius + atAWorkedSite * INSIDE_THE_EDGE;
        } else {
            ControlPoint held = POINT.get(point);
            reach = held.holdsAfterwards && held.captured ? postRingRadius : held.radius * INSIDE_THE_EDGE;
        }
        return Sight.withinRange(at.x, at.y, where.x, where.y, reach);
    }

    /**
     * How far from its middle a man counts as on a site, the measure a crowd is counted by: a point's radius, or for a
     * worked site its edge and a man's arm's length past it (G0111).
     */
    private float radiusOf(Entity point) {
        return POINT.has(point) ? POINT.get(point).radius : BODY.get(point).radius + atAWorkedSite;
    }

    /** Whether a site has been taken, whatever its kind. */
    private static boolean taken(Entity point) {
        return POINT.has(point) ? POINT.get(point).captured : SITE.get(point).taken;
    }

    /** Whether a site is ground held afterwards (G0061) - a point the finale names; never a worked site. */
    private static boolean holdsAfterwards(Entity point) {
        return POINT.has(point) && POINT.get(point).holdsAfterwards;
    }

    /**
     * Holds ground the squad has already taken, unless it has been told to push on.
     *
     * <p><strong>A point taken is a point held</strong> (G0029): the level advances at the commander's
     * pace. "Forward" sends them on (G0017); they also go on by themselves to catch up with a commander
     * who is ahead, never to run ahead of one (G0040). Posture is {@code SECURE}, as while capturing.
     */
    boolean holdsWhatItHasTaken(Trooper trooper, Position at, float deltaTime) {
        if (trooper.sentOn || trooper.sentAhead != Handle.NOBODY) {
            // Told to go on, or sent on by a crowd (G0086): ground already taken is ground he crosses.
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
     * <p><strong>The squad follows the war; it never runs ahead of it.</strong> A commander near the
     * next point sends them at once; otherwise they hold their own while, then go only if a commander
     * is ahead by route and none is on this point. Every commander on his feet counts.
     */
    private boolean movesOn(Trooper trooper, Position at, int taken, float deltaTime) {
        int next = nearestUncapturedPoint(at);
        if (next < 0) {
            // Nothing left to take: what is left is the ground held afterwards (G0061).
            next = heldPoint();
        }
        if (next < 0 || next == taken) {
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
            if (Life.down(commander)) {
                continue;
            }
            Position standing = POSITION.get(commander);
            if (Sight.withinRange(standing.x, standing.y, where.x, where.y, rallyRadius)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any commander on his feet is in the same biome as this point. */
    private boolean aCommanderInBiomeOf(Entity point) {
        Position where = POSITION.get(point);
        int biome = map.biomeAt(where.x, where.y);
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (Life.down(commander)) {
                continue;
            }
            Position standing = POSITION.get(commander);
            if (map.biomeAt(standing.x, standing.y) == biome) {
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
            if (Life.down(commander)) {
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
            if (taken(point) && standingOn(point, at)) {
                return p;
            }
        }
        return -1;
    }

    /**
     * Which uncaptured point is the shortest walk away, or {@code -1} if none can be reached - ground held
     * afterwards only while a commander is in its biome (G0061).
     *
     * <p>Ties go to the first spawned: insertion order and a strict comparison.
     */
    private int nearestUncapturedPoint(Position at) {
        return nearestUncapturedPoint(map.colAt(at.x), map.rowAt(at.y), -1);
    }

    /** The same, from a tile, passing over one point by its index ({@code -1} passes over none). */
    private int nearestUncapturedPoint(int col, int row, int passOver) {
        int nearest = -1;
        int shortest = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            Entity point = points.get(i);
            if (i == passOver || taken(point) || (holdsAfterwards(point) && !aCommanderInBiomeOf(point))) {
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

    /**
     * Which point has been taken and is held afterwards, or {@code -1}. One a level, as there is one post
     * (G0027).
     */
    private int heldPoint() {
        for (int i = 0; i < points.size(); i++) {
            Entity point = points.get(i);
            if (taken(point) && holdsAfterwards(point)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * With nothing left to take and ground held afterwards still waiting for a commander (G0061), patrols the way into
     * its biome from this side (G0094): round its way in, the tile its route enters by, never inside that biome - a
     * trooper there would wake what it holds before the commander arrives. False when there is no such ground, or no
     * way in to it, and the man roams as before.
     */
    private boolean patrolsTheWayIn(Trooper trooper, Position at) {
        for (int i = 0; i < points.size(); i++) {
            Entity point = points.get(i);
            if (taken(point) || !holdsAfterwards(point) || wayInCol[i] < 0) {
                continue;
            }
            Position where = POSITION.get(points.get(i));
            roam.exploresAround(
                    trooper,
                    at,
                    map.tileCentreX(wayInCol[i]),
                    map.tileCentreY(wayInRow[i]),
                    0f,
                    map.biomeAt(where.x, where.y));
            return true;
        }
        return false;
    }

    /** Builds one field per point, aimed at that point, the first time there are any - and each held one's way in. */
    void buildRoutesIfNeeded() {
        if (routes.length == points.size()) {
            return;
        }
        routes = new FlowField[points.size()];
        wayInCol = new int[points.size()];
        wayInRow = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Position where = POSITION.get(points.get(i));
            FlowField field = FlowField.over(map, bodyRadius);
            field.rebuildFor(map.colAt(where.x), map.rowAt(where.y));
            routes[i] = field;
            wayInCol[i] = -1;
            wayInRow[i] = -1;
            if (holdsAfterwards(points.get(i))) {
                findWayIn(i, field, map.biomeAt(where.x, where.y));
            }
        }
    }

    /**
     * Finds point {@code index}'s way in: the tile outside {@code biome}, beside one inside it, that is the fewest steps
     * from the point along its route - where the route crosses in. Once per held point, when the routes are built.
     * Ties go to the first in row order; none found leaves it at {@code -1}.
     */
    private void findWayIn(int index, FlowField route, int biome) {
        int shortest = Integer.MAX_VALUE;
        for (int row = 0; row < map.rows(); row++) {
            for (int col = 0; col < map.cols(); col++) {
                if (map.biomeAtTile(col, row) == biome) {
                    continue;
                }
                int steps = route.stepsTo(col, row);
                if (steps < 0 || steps >= shortest || !borders(col, row, biome)) {
                    continue;
                }
                shortest = steps;
                wayInCol[index] = col;
                wayInRow[index] = row;
            }
        }
    }

    /** Whether one of a tile's four neighbours is in {@code biome}. */
    private boolean borders(int col, int row, int biome) {
        return map.biomeAtTile(col - 1, row) == biome
                || map.biomeAtTile(col + 1, row) == biome
                || map.biomeAtTile(col, row - 1) == biome
                || map.biomeAtTile(col, row + 1) == biome;
    }
}
