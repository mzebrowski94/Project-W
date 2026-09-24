package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * Decides, once a tick, what each trooper is doing and where it is headed.
 *
 * <p>Deciding is here and driving is {@code TrooperMoveSystem}; that split is G0010's shape and it is
 * what will let orders slot in above this without touching how a trooper walks. Everything downstream
 * reads {@link Trooper#mode} and the target, and asks the question again for nobody.
 *
 * <p><strong>Nearest means nearest by route</strong> (2.e). A point twenty units away through a wall
 * is not nearer than one forty units away down a corridor, and choosing on a straight line marches a
 * squad into rock. Each point owns a flow field built for a trooper's body, so the same grid pass
 * answers both "which is nearest" and "which way do I walk", for every trooper at once - which is the
 * whole reason to reach for a field rather than a search per unit.
 *
 * <p>The fields are built <strong>once</strong>. Control points do not move and are never removed, so
 * a rebuild would be a full grid pass per tick for an answer that cannot have changed.
 *
 * <p><strong>The order of precedence is fight, order, casualty, objective.</strong> A fight beats
 * everything because a squad that walked past one to do anything else reads as the AI being broken.
 * A standing order beats going to fetch somebody because an order that a body on the ground could
 * override is not an order (G0017); a man told to hold a corner holds it. And fetching somebody
 * beats the level's objectives, because a squad that walked past one of its own to stand on a square
 * would be the same failure as walking past a fight.
 *
 * <p>Whether it goes at all is a roll - {@code AiChance.AID_A_TROOPER}, or the much likelier
 * {@code AID_THE_COMMANDER} - and it is rolled only while the trooper is not already going. Rolling
 * every tick of an approach would have men setting off and thinking better of it halfway, which
 * reads as indecision rather than as judgement.
 *
 * <p>Something in front of it is dealt with before anywhere it was going, because the alternative is
 * a squad walking past a fight to stand on a square - which is the version that reads as the AI being
 * broken. Where it was headed is not
 * remembered: the objective is a pure function of where the trooper is standing and what is still
 * untaken, so it is re-derived the moment the shooting stops rather than saved and restored.
 *
 * <p>With nothing left to take, a trooper roams instead: a seeded target somewhere it can walk to,
 * picked again whenever it arrives. That is what "explore the map to clear it" is until a trooper can
 * notice an enemy for itself - and because roaming and advancing differ only in <em>how the target is
 * chosen</em>, they are the same driver downstream rather than a second behaviour.
 */
public final class TrooperMindSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<ControlPoint> POINT = ComponentMapper.getFor(ControlPoint.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Turret> TURRET = ComponentMapper.getFor(Turret.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);

    private final TileMap map;
    private final Rng rng;
    private final Rng nerve;
    private final Chances chances;
    private final float bodyRadius;
    private final float arriveRadius;
    private final float reach;
    private final float crewRadius;
    private final float pushDistance;
    private final float patchRadius;
    private final float followAhead;
    private final float holdSeconds;
    private final float holdJitterSeconds;
    private final float rallyRadius;
    private final float threatBonus;
    private final float sharedTargetPenalty;
    private final float followDistance;

    /** One field per control point, parallel to the family. Rebuilt only if the set of points changes. */
    @NotState("cache of one flow field per control point, a pure function of the map and where the points are")
    private FlowField[] routes = new FlowField[0];

    /** Scratch: how many men are shooting at each enemy this tick, parallel to the family. */
    @NotState("scratch rebuilt every tick before any trooper chooses a target")
    private int[] shooters = new int[0];

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
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> stations = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> posts = new ImmutableArray<>(new Array<>());

    public TrooperMindSystem(SimConfig config, TileMap map, Rng rng, Rng nerve) {
        this.map = map;
        this.rng = rng;
        this.nerve = nerve;
        this.chances = config.chances();
        this.bodyRadius = config.trooper().radius();
        this.arriveRadius = config.trooper().arriveRadius();
        this.reach = config.action().reach();
        this.crewRadius = config.turret().crewRadius();
        this.pushDistance = config.trooper().forwardDistance();
        this.patchRadius = config.trooper().patchRadius();
        this.followAhead = config.trooper().followAheadSeconds();
        this.holdSeconds = config.trooper().holdSeconds();
        this.holdJitterSeconds = config.trooper().holdJitterSeconds();
        this.rallyRadius = config.trooper().rallyRadius();
        this.threatBonus = config.trooper().threatBonus();
        this.sharedTargetPenalty = config.trooper().sharedTargetPenalty();
        this.followDistance = config.trooper().followDistance();
    }

    @Override
    public void addedToEngine(Engine engine) {
        troopers =
                engine.getEntitiesFor(Family.all(Trooper.class, Position.class).get());
        points = engine.getEntitiesFor(
                Family.all(ControlPoint.class, Position.class).get());
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
        casualties = engine.getEntitiesFor(
                Family.all(Casualty.class, Position.class, Body.class).get());
        stations = engine.getEntitiesFor(
                Family.all(Turret.class, Position.class, Body.class).get());
        posts = engine.getEntitiesFor(Family.all(Post.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        buildRoutesIfNeeded();
        if (shooters.length < enemies.size()) {
            shooters = new int[enemies.size()];
        }
        for (int e = 0; e < enemies.size(); e++) {
            shooters[e] = 0;
        }
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            Position at = POSITION.get(entity);
            if (takenPointUnder(at) < 0) {
                // Off every taken point, so the next one it holds is a hold of its own. Asked before
                // anything else, so a fight or an errand that took it off the point ends the hold too.
                trooper.holding = false;
            }

            if (trooper.alertRemaining > 0f) {
                trooper.alertRemaining = Math.max(0f, trooper.alertRemaining - deltaTime);
            }

            int quarry = enemyFor(trooper, at);
            if (quarry >= 0) {
                Position where = POSITION.get(enemies.get(quarry));
                shooters[quarry]++;
                trooper.mode = Trooper.Mode.FIGHT;
                trooper.headFor(where.x, where.y);
                continue;
            }

            if (obeys(trooper, at)) {
                continue;
            }

            if (aids(entity, trooper, at, deltaTime)) {
                continue;
            }

            if (crews(entity, trooper, at, deltaTime)) {
                continue;
            }

            if (holdsWhatItHasTaken(trooper, at, deltaTime)) {
                continue;
            }

            int nearest = nearestUncapturedPoint(at);
            if (nearest >= 0) {
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
                continue;
            }
            trooper.mode = Trooper.Mode.EXPLORE;
            if (!trooper.hasTarget || arrived(at, trooper)) {
                roam(trooper, at);
            }
        }
        splitIntoSectors();
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
    private void splitIntoSectors() {
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
    private boolean crews(Entity entity, Trooper trooper, Position at, float deltaTime) {
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
     * Carries out a standing order, if there is one worth acting on.
     *
     * <p>Below the fight and above the objective, which is the whole of 3.e. Nothing here has to
     * remember being interrupted: a trooper that was shooting simply never reached this branch, and
     * reaches it again the tick the shooting stops.
     *
     * <p>"Forward" is not handled here at all - it is a burst of pace on the way to the objective
     * rather than somewhere else to be, so it falls through to the objective and is spent by
     * {@code TrooperMoveSystem} as ground is covered.
     */
    private boolean obeys(Trooper trooper, Position at) {
        if (trooper.order == Order.DEFENCE) {
            trooper.mode = Trooper.Mode.HOLD;
            trooper.clearTarget();
            return true;
        }
        if (trooper.order != Order.FOLLOW_ME) {
            return false;
        }
        // The man who said it, not whichever commander happens to be first. With four of them,
        // following anybody else would make "follow me" the one order the player cannot aim.
        Entity giver = Players.inSlot(commanders, trooper.orderedBy);
        if (giver == null || Drivers.down(giver)) {
            // Belt and braces: his death already released the order upstream this tick. Falling
            // through to the objective is still better than standing about waiting for a corpse.
            return false;
        }
        Position commander = POSITION.get(giver);
        trooper.mode = Trooper.Mode.FOLLOW;
        keepUpWith(trooper, at, commander, VELOCITY.get(giver));
        return true;
    }

    /**
     * Keeps up with where the commander is going rather than where he is.
     *
     * <p>A follower that walks at the man himself chases the spot he has just left, so a squad strings
     * out on his heels and cuts every corner behind him. Walking at where he will be in a moment - the
     * squad formations of <em>Company of Heroes</em>, shrunk to one multiply - brings it alongside him
     * when he turns. Once it is within its following distance it is aimed at the man himself, so it
     * settles round him and never overtakes him into his own line of fire.
     *
     * <p>The point ahead is dropped where it would be inside rock; the route to him is always good.
     */
    private void keepUpWith(Trooper trooper, Position at, Position commander, Velocity moving) {
        float aheadX = commander.x + moving.x * followAhead;
        float aheadY = commander.y + moving.y * followAhead;
        boolean closeEnough = Sight.withinRange(at.x, at.y, commander.x, commander.y, followDistance);
        if (followAhead <= 0f || closeEnough || !map.isClear(aheadX, aheadY, bodyRadius)) {
            trooper.headFor(commander.x, commander.y);
            return;
        }
        trooper.headFor(aheadX, aheadY);
    }

    /**
     * Goes to pick somebody up, if there is somebody and this man is willing.
     *
     * <p>Below the order and above the objective, which is the whole of where a rescue sits. The
     * candidate has to be somewhere this trooper could have seen - its own sight range, rather than a
     * number of its own - so "it went for a man it could see go down" is the rule, and a squad does
     * not converge on a body across the map.
     *
     * <p><strong>The dice are consulted once, on the way in.</strong> A trooper already in the mode
     * keeps going: re-rolling every tick of an approach would have men setting off and thinking
     * better of it halfway, which reads as a bug rather than as judgement. It stops only when there
     * is nobody left within range - because he was picked up, because somebody else got there, or
     * because he ran out - and then falls through to the objective on the same tick.
     *
     * <p>What it heads for is a <em>place</em> (G0010). Which body it is standing over is asked again
     * by {@code AidSystem} every tick, from the entities actually in reach, so a casualty reaped
     * between ticks cannot come back as whatever was built from the recycled component.
     */
    private boolean aids(Entity entity, Trooper trooper, Position at, float deltaTime) {
        Entity subject = nearestCasualty(entity, at, trooper.sightRange);
        if (subject == null) {
            return false;
        }
        if (trooper.mode != Trooper.Mode.AID) {
            // One due for both kinds of rescue: it is one willingness, spent faster for the commander.
            trooper.aidDue = Odds.spend(nerve, trooper.aidDue, chances, willingness(subject), deltaTime);
            if (!Odds.spent(trooper.aidDue)) {
                return false;
            }
        }
        trooper.mode = Trooper.Mode.AID;
        if (Aid.gapBetween(at, BODY.get(entity).radius, subject) <= reach) {
            // Standing over him. Nothing to head for, so it stops where it is and gets on with it.
            trooper.clearTarget();
        } else {
            Position where = POSITION.get(subject);
            trooper.headFor(where.x, where.y);
        }
        return true;
    }

    /**
     * How readily this trooper would go for that particular man.
     *
     * <p>The commander is worth far more than a comrade, and that is the whole of "more frequently
     * the commander": no ranking, no second pass over the candidates, just a number that makes a man
     * with two bodies in front of him set off for the right one far more often than not.
     */
    private static AiChance willingness(Entity subject) {
        return COMMANDER.has(subject) ? AiChance.AID_THE_COMMANDER : AiChance.AID_A_TROOPER;
    }

    /**
     * The nearest man on the ground within this trooper's sight range, or null if there is none.
     *
     * <p>By straight-line distance rather than by route, unlike an objective: a body you can see is
     * one you go to, and a squad that worked out the walking distance to each casualty before setting
     * off would be doing arithmetic in a firefight. Nobody is offered himself, and nothing already
     * dying is offered at all - the work would be spent on a corpse.
     */
    private @Nullable Entity nearestCasualty(Entity helper, Position at, float range) {
        Entity nearest = null;
        float shortest = range * range;
        for (int i = 0; i < casualties.size(); i++) {
            Entity candidate = casualties.get(i);
            if (Objects.equals(candidate, helper) || !CASUALTY.get(candidate).begun) {
                continue;
            }
            if (DYING.has(candidate) && DYING.get(candidate).begun) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float distance = dx * dx + dy * dy;
            if (distance > shortest) {
                continue;
            }
            shortest = distance;
            nearest = candidate;
        }
        return nearest;
    }

    /**
     * The enemy this trooper is dealing with, as its index in the family, or {@code -1} if it has
     * nothing to shoot at.
     *
     * <p><strong>Starting a fight and staying in one are different questions</strong>, and that
     * asymmetry is the whole of the hysteresis. Noticing something costs it every test the trooper
     * declares - range, arc, a clear line (G0015). Staying on it costs range alone, out to the longer
     * hold range: a trooper that lost its target every time it turned its head, or every time the
     * thing stepped behind a rock, would stutter between fighting and walking on at exactly the
     * distance a fight actually happens at. A man who has just been hit forgives the arc too: he is
     * looking all round for what did it.
     *
     * <p><strong>Of what it can see, the one it would most rather shoot</strong>, as a distance it
     * would rather it were. Something coming for somebody counts as {@code threatBonus} nearer than
     * something that is not - a nest, an alien that has noticed nobody - so a man shoots the grunt
     * walking at him before the nest behind it. And every man already shooting at a thing this tick
     * makes it {@code sharedTargetPenalty} further, so a squad spreads its fire instead of all emptying
     * into the nearest one. Threat ordering beat nearest-first for AI companions in the one study that
     * compared them (Tremblay et al., 2014); this is the cheapest form of it that a player can read.
     *
     * <p>Re-resolved every tick and never held between them (G0010). What survives a tick is the
     * mode, not the thing - so an enemy reaped between ticks cannot come back as whatever was built
     * from the recycled instance. The count of who is shooting what is scratch, rebuilt every tick.
     *
     * <p>Ties go to whichever was spawned first, because the family is in insertion order and the
     * comparison is strict.
     */
    private int enemyFor(Trooper trooper, Position at) {
        boolean alreadyFighting = trooper.mode == Trooper.Mode.FIGHT;
        boolean lookingAllRound = alreadyFighting || trooper.alertRemaining > 0f;
        float range = alreadyFighting ? trooper.holdRange : trooper.sightRange;
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (DYING.has(enemy) && DYING.get(enemy).begun) {
                // A body is not a target. Standing over one shooting is how a squad misses the next
                // thing coming at it.
                continue;
            }
            Position where = POSITION.get(enemy);
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared > range * range) {
                continue;
            }
            float score = (float) Math.sqrt(distanceSquared) + sharedTargetPenalty * shooters[i];
            if (PERCEPTION.has(enemy) && PERCEPTION.get(enemy).aggro) {
                score -= threatBonus;
            }
            if (score >= bestScore) {
                continue;
            }
            if (!inSight(trooper, at, where, lookingAllRound)) {
                continue;
            }
            bestScore = score;
            best = i;
        }
        return best;
    }

    /**
     * Every test this trooper declares, cheapest first.
     *
     * <p><strong>A fight in progress forgives the arc and never the wall</strong> - and so does a man
     * who has just been hit, for as long as he is looking round for what did it. The two are not
     * the same kind of hysteresis: where a man is looking is his own doing and changes twice a
     * second as he sweeps, so dropping a target the moment his head passes it would make a trooper
     * unable to hold a fight at all. Rock is not his doing and does not move. Forgiving both - which
     * is what this did - is a squad standing in the open firing into a wall the thing it was
     * shooting at stepped behind, which is the one thing a trooper must never look like doing.
     */
    private boolean inSight(Trooper trooper, Position at, Position target, boolean lookingAllRound) {
        return (lookingAllRound
                        || Sight.withinArc(
                                trooper.lookX, trooper.lookY, at.x, at.y, target.x, target.y, trooper.arcCosine))
                && Sight.clearLine(map, at.x, at.y, target.x, target.y, trooper.requiresClearLine);
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
    private boolean holdsWhatItHasTaken(Trooper trooper, Position at, float deltaTime) {
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
            trooper.holdRemaining = holdSeconds + rng.nextFloat(-holdJitterSeconds, holdJitterSeconds);
        }
        trooper.holdRemaining -= deltaTime;
        return trooper.holdRemaining <= 0f && !aCommanderAt(points.get(taken)) && aCommanderAheadOf(at, next);
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
    private int takenPointUnder(Position at) {
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

    /** Somewhere on the map a trooper can actually stand, drawn from the squad's own stream. */
    private void roam(Trooper trooper, Position at) {
        if (trooper.pushingForward() && pushesOutFromThePost(trooper, at)) {
            return;
        }
        if (posts.size() > 0 && roamsAroundThePost(trooper)) {
            return;
        }
        for (int attempt = 0; attempt < ROAM_ATTEMPTS; attempt++) {
            float x = rng.nextFloat(0f, map.worldWidth());
            float y = rng.nextFloat(0f, map.worldHeight());
            if (map.isClear(x, y, bodyRadius)) {
                trooper.headFor(x, y);
                return;
            }
        }
        // A map with almost nowhere to stand. Better to hold still than to walk into rock.
        trooper.clearTarget();
    }

    /**
     * Pushes the front line out: on a level that names a post, "forward" means away from the thing
     * you were told to hold.
     *
     * <p>Everywhere else forward is a burst of pace on the way to an objective, and it needs no
     * direction of its own because there is one to walk to. A defence has none - there is nothing to
     * take - so without this the order would spend itself sending men to a seeded random corner,
     * which is the opposite of a line moving.
     *
     * <p>It walks the target back towards the man until it lands on ground he can stand on, rather
     * than giving up at the first refusal: a push that stopped because the full distance happened to
     * end in rock would read as the order being ignored.
     */
    private boolean pushesOutFromThePost(Trooper trooper, Position at) {
        if (posts.size() == 0) {
            return false;
        }
        Position post = POSITION.get(posts.first());
        float awayX = at.x - post.x;
        float awayY = at.y - post.y;
        float distance = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (distance <= EPSILON) {
            // Standing on it. There is no "away" from here, so the ordinary roam is the better answer.
            return false;
        }
        float reach = pushDistance;
        for (int attempt = 0; attempt < PUSH_ATTEMPTS; attempt++) {
            float x = at.x + awayX / distance * reach;
            float y = at.y + awayY / distance * reach;
            if (map.isClear(x, y, bodyRadius)) {
                trooper.headFor(x, y);
                return true;
            }
            reach *= 0.5f;
        }
        return false;
    }

    /**
     * Roams inside the squad's patch around the post, rather than across the whole level.
     *
     * <p>A defence has nothing to take, so with nothing in front of them the squad falls through to
     * roaming - and roaming the map is the wrong answer on the one level whose whole instruction is
     * <em>stay here</em>. Men who wander off leave the post held by whoever happened to be standing
     * on it, which reads as the squad ignoring the level.
     *
     * <p>It is the same idea as an idle creature's patch, aimed at the post instead of at where the
     * unit was spawned - and it costs the player nothing, because "forward" still pushes the line out
     * past it and a fight still outranks all of this.
     *
     * <p>Drawn inside the square and rejected outside the circle, so no trigonometry is needed to
     * pick a point in a disc; {@code Math.sin} and {@code Math.cos} are banned here (pillar 6).
     */
    private boolean roamsAroundThePost(Trooper trooper) {
        Position post = POSITION.get(posts.first());
        for (int attempt = 0; attempt < ROAM_ATTEMPTS; attempt++) {
            float offsetX = rng.nextFloat(-patchRadius, patchRadius);
            float offsetY = rng.nextFloat(-patchRadius, patchRadius);
            if (offsetX * offsetX + offsetY * offsetY > patchRadius * patchRadius) {
                continue;
            }
            float x = post.x + offsetX;
            float y = post.y + offsetY;
            if (map.isClear(x, y, bodyRadius)) {
                trooper.headFor(x, y);
                return true;
            }
        }
        // Nowhere to stand anywhere near it. Better to hold where it is than to walk into rock.
        trooper.clearTarget();
        return true;
    }

    private boolean arrived(Position at, Trooper trooper) {
        float dx = trooper.targetX - at.x;
        float dy = trooper.targetY - at.y;
        return dx * dx + dy * dy <= arriveRadius * arriveRadius;
    }

    /** Builds one field per point, aimed at that point, the first time there are any. */
    private void buildRoutesIfNeeded() {
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

    /** How far inside a point counts as standing on it, as a fraction of its radius. */
    private static final float INSIDE_THE_EDGE = 0.7f;

    /** Enough tries to find open ground on any map worth playing; a few more would not help. */
    private static final int ROAM_ATTEMPTS = 16;

    /** How many times a push out from the post is halved before it gives up and roams instead. */
    private static final int PUSH_ATTEMPTS = 5;

    /** Below this there is no direction away from the post worth normalising. */
    private static final float EPSILON = 1e-4f;
}
