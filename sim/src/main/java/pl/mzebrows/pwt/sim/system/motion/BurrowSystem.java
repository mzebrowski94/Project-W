package pl.mzebrows.pwt.sim.system.motion;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Burrow;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Drives a strain that burrows (G0096): under the ground after the nearest man in its biome, up under him to snap its
 * maw shut, and down again to rest.
 *
 * <p><strong>Under the ground it decides the whole of its course</strong>, so it writes its steering last of what
 * steers, as a slither does, and is always on the move: onto the man its perception published at its hunting turn, or,
 * with nobody, bending about its home as a snake does ({@link Meander}). It goes beneath everything - its collider is
 * not on the field, so nothing it meets meets it, rock included - and only the edge of its biome or of the field turns
 * it. <strong>Nothing lands on it</strong> while it burrows: {@code DamageSystem} turns every blow away.
 *
 * <p><strong>Within its reach of him, off its rest, with room for its body, it surfaces</strong>: the maw is set on
 * where he stood and closes over the tell while it steers onto that place; on the field again, it can be hurt, and is
 * not solid, so he is not pushed out of what is about to close on him. A declared stun in the tell breaks the snap off
 * and holds it up. <strong>The snap</strong> wounds every man on his feet inside the maw, once, and the swarm is spared;
 * then it stands in its hole solid and immovable, dives, and rests.
 *
 * <p>Allocates nothing. A scan of the men at the snap and a scan of the scenery on a tick it may surface: a handful of
 * each, as {@code Drivers.nearestLivingTarget} scans the men.
 */
public final class BurrowSystem extends EntitySystem {

    private static final ComponentMapper<Burrow> BURROW = ComponentMapper.getFor(Burrow.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);

    private static final float EPSILON = 1e-4f;

    private final Rng rng;
    private final Solid solid;
    private final TileMap map;
    private final float tickSeconds;

    private ImmutableArray<Entity> burrowing = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> men = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> scenery = new ImmutableArray<>(new Array<>());

    public BurrowSystem(Rng rng, Solid solid, float tickSeconds) {
        this.rng = rng;
        this.solid = solid;
        this.map = solid.map();
        this.tickSeconds = tickSeconds;
    }

    @Override
    public void addedToEngine(Engine engine) {
        burrowing = engine.getEntitiesFor(
                Family.all(Burrow.class, Position.class, Body.class, Speed.class, Steering.class, Collider.class)
                        .get());
        men = engine.getEntitiesFor(Family.all(Vitality.class, Position.class, Body.class, Faction.class, Handle.class)
                .get());
        scenery = engine.getEntitiesFor(
                Family.all(Obstacle.class, Position.class, Body.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < burrowing.size(); i++) {
            Entity entity = burrowing.get(i);
            Burrow burrow = BURROW.get(entity);
            if (!burrow.enabled) {
                continue;
            }
            share(entity, burrow);
            if (Life.dying(entity)) {
                // A body in its throes is what its death made it.
                continue;
            }
            switch (burrow.phase) {
                case BURROWED -> burrowOn(entity, burrow);
                case SURFACING -> comeUp(entity, burrow);
                case STANDING -> stand(entity, burrow);
                case HELD -> {
                    if (!Drivers.held(entity)) {
                        dive(entity, burrow);
                    }
                }
            }
        }
    }

    /** How many teeth its maw has left: its health shared out between them, rounded up - the snake's rule. */
    private static void share(Entity entity, Burrow burrow) {
        if (HEALTH.has(entity)) {
            Health health = HEALTH.get(entity);
            burrow.teethLeft = Whole.piecesLeft(health.current, health.max, burrow.teeth);
        }
    }

    private void burrowOn(Entity entity, Burrow burrow) {
        if (burrow.restTicks > 0) {
            burrow.restTicks--;
        }
        Position at = POSITION.get(entity);
        float radius = BODY.get(entity).radius;
        startHeading(entity, burrow);
        Perception perception = PERCEPTION.get(entity);
        // It goes after its man only once it may come up again; resting, it roams as if it had nobody.
        boolean hunting = perception.aggro && burrow.restTicks <= 0;
        float speed = hunting ? SPEED.get(entity).value : burrow.idleSpeed;
        if (hunting && mayComeUp(burrow, at, radius, perception.targetX, perception.targetY)) {
            surface(entity, burrow, perception.targetX, perception.targetY);
            return;
        }
        if (edgeAhead(at, radius, burrow, speed)) {
            turn(burrow, homeward(at, burrow), burrow.huntCos, burrow.huntSin);
        } else if (hunting) {
            onto(burrow, at, perception.targetX, perception.targetY);
        } else {
            turn(burrow, bendOf(at, burrow), burrow.turnCos, burrow.turnSin);
        }
        STEERING.get(entity).set(burrow.headingX * speed, burrow.headingY * speed);
    }

    /** Off its rest, within its reach of where he stands, and with room there for its body. */
    private boolean mayComeUp(Burrow burrow, Position at, float radius, float x, float y) {
        if (burrow.restTicks > 0) {
            return false;
        }
        float dx = x - at.x;
        float dy = y - at.y;
        return dx * dx + dy * dy <= burrow.surfaceReach * burrow.surfaceReach && room(burrow, x, y, radius);
    }

    /** Open ground in its own biome, inside the field, clear of rock, shut doors and scenery, for a body this big. */
    private boolean room(Burrow burrow, float x, float y, float radius) {
        if (!inItsBiome(burrow, x, y, radius) || !solid.isClearPath(x, y, x, y, radius)) {
            return false;
        }
        for (int i = 0; i < scenery.size(); i++) {
            Entity thing = scenery.get(i);
            Position where = POSITION.get(thing);
            float clear = radius + BODY.get(thing).radius;
            float dx = where.x - x;
            float dy = where.y - y;
            if (dx * dx + dy * dy < clear * clear) {
                return false;
            }
        }
        return true;
    }

    /** Comes up under where he stood: the maw set there, on the field again, hurt as anything up is, not yet solid. */
    private static void surface(Entity entity, Burrow burrow, float x, float y) {
        burrow.phase = Burrow.Phase.SURFACING;
        burrow.phaseTicks = burrow.tellTicks;
        burrow.mawX = x;
        burrow.mawY = y;
        burrow.lookX = burrow.headingX;
        burrow.lookY = burrow.headingY;
        Collider collider = COLLIDER.get(entity);
        collider.present = true;
        collider.solid = false;
        collider.movable = false;
        collider.avoided = false;
        settleOnto(entity, burrow);
    }

    /** The tell: its maw closing on the place it was aimed at, never steered after. A declared stun breaks it off. */
    private void comeUp(Entity entity, Burrow burrow) {
        if (Drivers.held(entity)) {
            burrow.phase = Burrow.Phase.HELD;
            burrow.phaseTicks = 0;
            return;
        }
        settleOnto(entity, burrow);
        lookAbout(entity, burrow);
        burrow.phaseTicks--;
        if (burrow.phaseTicks <= 0) {
            snap(entity, burrow);
        }
    }

    /** Steers onto the middle of its maw, the last of the way in one tick. */
    private static void settleOnto(Entity entity, Burrow burrow) {
        Position at = POSITION.get(entity);
        float dx = burrow.mawX - at.x;
        float dy = burrow.mawY - at.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        Steering steering = STEERING.get(entity);
        if (distance <= EPSILON) {
            steering.set(0f, 0f);
            return;
        }
        float speed = SPEED.get(entity).value;
        steering.set(dx / distance * speed, dy / distance * speed);
    }

    /**
     * The maw closing: every man on his feet inside it takes its harm, once, and the swarm is spared. Then it stands in
     * its hole, solid and immovable as a rooted strain - the solver puts whoever is in the hole out of it.
     */
    private void snap(Entity entity, Burrow burrow) {
        float radius = BODY.get(entity).radius;
        for (int i = 0; i < men.size(); i++) {
            Entity man = men.get(i);
            if (!Drivers.huntable(man) || !DAMAGE.has(man)) {
                continue;
            }
            Position where = POSITION.get(man);
            float inside = radius + BODY.get(man).radius;
            float dx = where.x - burrow.mawX;
            float dy = where.y - burrow.mawY;
            if (dx * dx + dy * dy <= inside * inside) {
                Damage damage = DAMAGE.get(man);
                damage.land(0, burrow.harm);
                damage.cameFrom(burrow.mawX, burrow.mawY);
            }
        }
        burrow.phase = Burrow.Phase.STANDING;
        burrow.phaseTicks = burrow.standTicks;
        Collider collider = COLLIDER.get(entity);
        collider.solid = true;
        collider.avoided = true;
        STEERING.get(entity).set(0f, 0f);
    }

    /** In its hole until its stand runs out - longer under a declared stun - then down. */
    private void stand(Entity entity, Burrow burrow) {
        if (Drivers.held(entity)) {
            return;
        }
        STEERING.get(entity).set(0f, 0f);
        lookAbout(entity, burrow);
        burrow.phaseTicks--;
        if (burrow.phaseTicks <= 0) {
            dive(entity, burrow);
        }
    }

    /**
     * Up, it looks at its man, turning onto him at its hunting turn - or, with nobody, about it at its bend. Never
     * while a stun holds it: a stunned thing decides nothing.
     */
    private static void lookAbout(Entity entity, Burrow burrow) {
        Perception perception = PERCEPTION.get(entity);
        if (!perception.aggro) {
            turnLook(burrow, 1, burrow.turnCos, burrow.turnSin);
            return;
        }
        Position at = POSITION.get(entity);
        float toX = perception.targetX - at.x;
        float toY = perception.targetY - at.y;
        float distance = Meander.length(toX, toY);
        if (distance <= EPSILON) {
            return;
        }
        toX /= distance;
        toY /= distance;
        if (burrow.lookX * toX + burrow.lookY * toY >= burrow.huntCos) {
            burrow.lookX = toX;
            burrow.lookY = toY;
            return;
        }
        turnLook(burrow, burrow.lookX * toY - burrow.lookY * toX >= 0f ? 1 : -1, burrow.huntCos, burrow.huntSin);
    }

    private static void turnLook(Burrow burrow, int side, float cos, float sin) {
        float x = Meander.turnedX(burrow.lookX, burrow.lookY, side, cos, sin);
        float y = Meander.turnedY(burrow.lookX, burrow.lookY, side, cos, sin);
        float length = Meander.length(x, y);
        if (length > EPSILON) {
            burrow.lookX = x / length;
            burrow.lookY = y / length;
        }
    }

    /** Down again: off the field, and resting a drawn while before it may surface. */
    private void dive(Entity entity, Burrow burrow) {
        burrow.phase = Burrow.Phase.BURROWED;
        burrow.phaseTicks = 0;
        burrow.restTicks = Meander.drawSpan(rng, burrow.restMinTicks, burrow.restMaxTicks);
        Collider collider = COLLIDER.get(entity);
        collider.present = false;
        collider.solid = false;
        collider.avoided = false;
    }

    /** Its heading the first tick it moves: the way it was put down facing, east without one. */
    private static void startHeading(Entity entity, Burrow burrow) {
        if (burrow.headingX != 0f || burrow.headingY != 0f) {
            return;
        }
        burrow.headingX = 1f;
        burrow.headingY = 0f;
        if (FACING.has(entity)) {
            Facing facing = FACING.get(entity);
            if (facing.x != 0f || facing.y != 0f) {
                burrow.headingX = facing.x;
                burrow.headingY = facing.y;
            }
        }
        normalise(burrow);
    }

    /**
     * Whether the edge of its biome or of the field is within the way it goes while it turns a quarter round, and its
     * body's width - so, turning away now, it never goes out of its biome.
     */
    private boolean edgeAhead(Position at, float radius, Burrow burrow, float speed) {
        float ahead = radius + speed * burrow.quarterTurnTicks * tickSeconds;
        float x = at.x + burrow.headingX * ahead;
        float y = at.y + burrow.headingY * ahead;
        return !inItsBiome(burrow, x, y, radius);
    }

    /** Whether a body this big at this place is inside the field and in its own biome. */
    private boolean inItsBiome(Burrow burrow, float x, float y, float radius) {
        if (x < radius || y < radius || x > map.worldWidth() - radius || y > map.worldHeight() - radius) {
            return false;
        }
        return map.biomeAt(x, y) == map.biomeAt(burrow.homeX, burrow.homeY);
    }

    /** Which way home lies, 1 left or -1 right: away from an edge, home being always inside its biome. */
    private static int homeward(Position at, Burrow burrow) {
        return Meander.towardsHome(at.x, at.y, burrow.homeX, burrow.homeY, burrow.headingX, burrow.headingY) >= 0f
                ? 1
                : -1;
    }

    /** Turns onto where he stands at its hunting turn, straight at him once one tick's turn would take it past him. */
    private static void onto(Burrow burrow, Position at, float x, float y) {
        float toX = x - at.x;
        float toY = y - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        toX /= distance;
        toY /= distance;
        if (burrow.headingX * toX + burrow.headingY * toY >= burrow.huntCos) {
            burrow.headingX = toX;
            burrow.headingY = toY;
            return;
        }
        int side = burrow.headingX * toY - burrow.headingY * toX >= 0f ? 1 : -1;
        turn(burrow, side, burrow.huntCos, burrow.huntSin);
    }

    /** Back towards home past its roam; otherwise the bend it is in, picking another once that runs out. */
    private int bendOf(Position at, Burrow burrow) {
        if (Meander.pastRoam(at.x, at.y, burrow.homeX, burrow.homeY, burrow.roam)) {
            return homeward(at, burrow);
        }
        if (burrow.bendTicks <= 0) {
            burrow.bend = Meander.drawBend(rng);
            burrow.bendTicks = Meander.drawSpan(rng, burrow.bendMinTicks, burrow.bendMaxTicks);
        }
        burrow.bendTicks--;
        return burrow.bend;
    }

    private static void turn(Burrow burrow, int side, float cos, float sin) {
        if (side == 0) {
            return;
        }
        float x = Meander.turnedX(burrow.headingX, burrow.headingY, side, cos, sin);
        float y = Meander.turnedY(burrow.headingX, burrow.headingY, side, cos, sin);
        burrow.headingX = x;
        burrow.headingY = y;
        normalise(burrow);
    }

    private static void normalise(Burrow burrow) {
        float length = Meander.length(burrow.headingX, burrow.headingY);
        if (length > EPSILON) {
            burrow.headingX /= length;
            burrow.headingY /= length;
        }
    }
}
