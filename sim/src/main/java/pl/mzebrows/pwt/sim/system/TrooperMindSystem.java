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
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
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

    private final TileMap map;
    private final Rng rng;
    private final Rng nerve;
    private final Chances chances;
    private final float bodyRadius;
    private final float arriveRadius;
    private final float reach;

    /** One field per control point, parallel to the family. Rebuilt only if the set of points changes. */
    private FlowField[] routes = new FlowField[0];

    private ImmutableArray<Entity> troopers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> points = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());

    public TrooperMindSystem(SimConfig config, TileMap map, Rng rng, Rng nerve) {
        this.map = map;
        this.rng = rng;
        this.nerve = nerve;
        this.chances = config.chances();
        this.bodyRadius = config.trooper().radius();
        this.arriveRadius = config.trooper().arriveRadius();
        this.reach = config.action().reach();
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
    }

    @Override
    public void update(float deltaTime) {
        buildRoutesIfNeeded();
        for (int i = 0; i < troopers.size(); i++) {
            Entity entity = troopers.get(i);
            if (Drivers.held(entity)) {
                continue;
            }
            Trooper trooper = TROOPER.get(entity);
            Position at = POSITION.get(entity);

            Position quarry = enemyFor(trooper, at);
            if (quarry != null) {
                trooper.mode = Trooper.Mode.FIGHT;
                trooper.headFor(quarry.x, quarry.y);
                continue;
            }

            if (obeys(trooper)) {
                continue;
            }

            if (aids(entity, trooper, at, deltaTime)) {
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
                trooper.mode = standingOn(point, at) ? Trooper.Mode.SECURE : Trooper.Mode.ADVANCE;
                continue;
            }
            trooper.mode = Trooper.Mode.EXPLORE;
            if (!trooper.hasTarget || arrived(at, trooper)) {
                roam(trooper);
            }
        }
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
    private boolean obeys(Trooper trooper) {
        if (trooper.order == Order.DEFENCE) {
            trooper.mode = Trooper.Mode.HOLD;
            trooper.clearTarget();
            return true;
        }
        if (trooper.order != Order.FOLLOW_ME) {
            return false;
        }
        Position commander = Drivers.livingTarget(commanders);
        if (commander == null) {
            // Belt and braces: his death already released the order upstream this tick. Falling
            // through to the objective is still better than standing about waiting for a corpse.
            return false;
        }
        trooper.mode = Trooper.Mode.FOLLOW;
        trooper.headFor(commander.x, commander.y);
        return true;
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
        if (trooper.mode != Trooper.Mode.AID && !Odds.rolls(nerve, chances, willingness(subject), deltaTime)) {
            return false;
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
     * The enemy this trooper is dealing with, or null if it has nothing to shoot at.
     *
     * <p><strong>Starting a fight and staying in one are different questions</strong>, and that
     * asymmetry is the whole of the hysteresis. Noticing something costs it every test the trooper
     * declares - range, arc, a clear line (G0015). Staying on it costs range alone, out to the longer
     * hold range: a trooper that lost its target every time it turned its head, or every time the
     * thing stepped behind a rock, would stutter between fighting and walking on at exactly the
     * distance a fight actually happens at.
     *
     * <p>Re-resolved every tick and never held between them (G0010). What survives a tick is the
     * mode, not the thing - so an enemy reaped between ticks cannot come back as whatever was built
     * from the recycled instance.
     *
     * <p>Ties go to whichever was spawned first, because the family is in insertion order and the
     * comparison is strict.
     */
    private @Nullable Position enemyFor(Trooper trooper, Position at) {
        boolean alreadyFighting = trooper.mode == Trooper.Mode.FIGHT;
        float range = alreadyFighting ? trooper.holdRange : trooper.sightRange;
        Position nearest = null;
        float shortest = range * range;
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
            float distance = dx * dx + dy * dy;
            if (distance > shortest) {
                continue;
            }
            if (!alreadyFighting && !inSight(trooper, at, where)) {
                continue;
            }
            shortest = distance;
            nearest = where;
        }
        return nearest;
    }

    /** Every test this trooper declares, cheapest first. */
    private boolean inSight(Trooper trooper, Position at, Position target) {
        return Sight.withinArc(trooper.lookX, trooper.lookY, at.x, at.y, target.x, target.y, trooper.arcCosine)
                && Sight.clearLine(map, at.x, at.y, target.x, target.y, trooper.requiresClearLine);
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
    private void roam(Trooper trooper) {
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
}
