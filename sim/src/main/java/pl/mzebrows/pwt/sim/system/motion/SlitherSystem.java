package pl.mzebrows.pwt.sim.system.motion;

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
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slither;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.SlitherDef;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * Drives a strain that slithers (G0087): on and on, never stopping, in wide arcs.
 *
 * <p><strong>It decides the whole of its course</strong>, so it writes its steering last of everything that steers
 * and {@code DriveSystem} reads it as any other answer (G0010). Nothing holds a slither still - no stun lands on it
 * - and a pace slows it only if it is not unslowed. It latches nothing as the swarm does; <strong>from time to time it picks a man</strong> in its biome
 * and turns onto him to go through him once, then goes back to bending about.
 *
 * <p><strong>In the open it bends</strong> one way or the other, or goes straight on, for a while drawn from the
 * strain's range, then picks again - slowly, so it goes round in wide arcs. <strong>Where its way is shut</strong> -
 * rock or a shut door within its look ahead, the edge of its biome, or something it ran up against and did not get
 * past - it turns hard towards whichever side is open, a quarter of the way round. Further from home than its roam, it
 * bends back towards home.
 *
 * <p><strong>Its parts follow its head</strong>, each kept its spacing behind the one in front, and how many it is in
 * is its health shared out between them, the last to go first.
 *
 * <p>Allocates nothing; one {@code isClearPath} a slithering unit a tick, two more on a tick its way is shut.
 */
public final class SlitherSystem extends EntitySystem {

    private static final ComponentMapper<Slither> SLITHER = ComponentMapper.getFor(Slither.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Steering> STEERING = ComponentMapper.getFor(Steering.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);

    /** Of what it was going, how much a head must have covered on a tick not to have run up against something. */
    private static final float STUCK = 0.5f;

    private static final float EPSILON = 1e-4f;

    private final Rng rng;
    private final Solid solid;
    private final TileMap map;
    private final float tickSeconds;

    private ImmutableArray<Entity> slithering = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> men = new ImmutableArray<>(new Array<>());

    public SlitherSystem(Rng rng, Solid solid, float tickSeconds) {
        this.rng = rng;
        this.solid = solid;
        this.map = solid.map();
        this.tickSeconds = tickSeconds;
    }

    @Override
    public void addedToEngine(Engine engine) {
        slithering =
                engine.getEntitiesFor(Family.all(Slither.class, Position.class, Body.class, Speed.class, Steering.class)
                        .get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
        men = engine.getEntitiesFor(Family.all(Vitality.class, Position.class, Faction.class, Handle.class)
                .get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < slithering.size(); i++) {
            Entity entity = slithering.get(i);
            Slither slither = SLITHER.get(entity);
            if (!slither.enabled) {
                continue;
            }
            share(entity, slither);
            follow(entity, slither);
            release(entity, slither);
            if (Drivers.held(entity)) {
                continue;
            }
            steer(entity, slither);
        }
    }

    /** Frees a bite slot once its man is clear of the head - gone, or no longer touching it. */
    private void release(Entity entity, Slither slither) {
        Position head = POSITION.get(entity);
        float headRadius = BODY.get(entity).radius;
        for (int slot = 0; slot < SlitherDef.MOST; slot++) {
            long handle = slither.bitten(slot);
            if (handle == Handles.NOBODY) {
                continue;
            }
            Entity man = Handles.resolve(handled, handle);
            if (man == null || !POSITION.has(man) || !BODY.has(man)) {
                slither.clear(slot);
                continue;
            }
            Position at = POSITION.get(man);
            float clear = headRadius + BODY.get(man).radius;
            float dx = at.x - head.x;
            float dy = at.y - head.y;
            if (dx * dx + dy * dy >= clear * clear) {
                slither.clear(slot);
            }
        }
    }

    /** How many parts it is in: one part's worth of health a part, rounded up, so the head is the last to go. */
    private static void share(Entity entity, Slither slither) {
        if (!HEALTH.has(entity)) {
            return;
        }
        Health health = HEALTH.get(entity);
        if (health.max <= 0 || health.current <= 0) {
            slither.partsLeft = 1;
            return;
        }
        int left = (health.current * slither.parts + health.max - 1) / health.max;
        slither.partsLeft = Math.max(1, Math.min(slither.parts, left));
    }

    /** Each part kept its spacing behind the one in front of it, pulled straight along the line between them. */
    private static void follow(Entity entity, Slither slither) {
        Position head = POSITION.get(entity);
        float headRadius = BODY.get(entity).radius;
        float leadX = head.x;
        float leadY = head.y;
        float leadRadius = headRadius;
        for (int part = 1; part < slither.parts; part++) {
            float radius = slither.radiusOf(part, headRadius);
            float gap = slither.spacing * (leadRadius + radius);
            float x = slither.partX(part);
            float y = slither.partY(part);
            float dx = x - leadX;
            float dy = y - leadY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > gap) {
                float pull = gap / distance;
                x = leadX + dx * pull;
                y = leadY + dy * pull;
                slither.lay(part, x, y);
            }
            leadX = x;
            leadY = y;
            leadRadius = radius;
        }
    }

    private void steer(Entity entity, Slither slither) {
        Position at = POSITION.get(entity);
        float radius = BODY.get(entity).radius;
        if (slither.headingX == 0f && slither.headingY == 0f) {
            slither.headingX = 1f;
            slither.headingY = 0f;
            if (FACING.has(entity)) {
                Facing facing = FACING.get(entity);
                if (facing.x != 0f || facing.y != 0f) {
                    slither.headingX = facing.x;
                    slither.headingY = facing.y;
                }
            }
            normalise(slither);
        }
        if (slither.tightTicks <= 0 && (shut(at, radius, slither) || ranUpAgainst(entity, at, slither))) {
            slither.tightSide = openSide(at, radius, slither);
            slither.tightTicks = slither.tightSpan;
        }
        Entity prey = slither.hunts ? prey(at, slither) : null;
        if (slither.tightTicks > 0) {
            slither.tightTicks--;
            turn(slither, slither.tightSide, slither.tightCos, slither.tightSin);
        } else if (prey != null) {
            onto(slither, at, POSITION.get(prey));
        } else {
            turn(slither, bendOf(at, slither), slither.turnCos, slither.turnSin);
        }
        slither.lastX = at.x;
        slither.lastY = at.y;
        float speed = SPEED.get(entity).value;
        STEERING.get(entity).set(slither.headingX * speed, slither.headingY * speed);
    }

    /**
     * The man it is going for this tick, or null (G0087). It goes for one until its head has bitten him, he is down or
     * gone, or its time runs out; then it waits a drawn while and picks again - the nearest man on his feet in its own
     * biome within its range, or, with nobody there, waits again.
     */
    private @Nullable Entity prey(Position at, Slither slither) {
        if (slither.prey != Handles.NOBODY) {
            Entity man = Handles.resolve(handled, slither.prey);
            slither.preyTicks--;
            if (man != null && Drivers.huntable(man) && slither.preyTicks > 0 && !bitHim(slither)) {
                return man;
            }
            slither.prey = Handles.NOBODY;
            wait(slither);
            return null;
        }
        if (slither.waitTicks < 0) {
            wait(slither);
        }
        if (slither.waitTicks > 0) {
            slither.waitTicks--;
            return null;
        }
        Entity man = nearest(at, slither);
        if (man == null) {
            wait(slither);
            return null;
        }
        slither.prey = Handles.of(man);
        slither.preyTicks = slither.huntTicks;
        return man;
    }

    private static boolean bitHim(Slither slither) {
        for (int slot = 0; slot < SlitherDef.MOST; slot++) {
            if (slither.bitten(slot) == slither.prey) {
                return true;
            }
        }
        return false;
    }

    private void wait(Slither slither) {
        slither.waitTicks = slither.huntMinTicks + rng.nextInt(slither.huntMaxTicks - slither.huntMinTicks + 1);
    }

    /**
     * The nearest man on his feet in its home biome within its range. A scan once per pick, every few seconds a snake,
     * not a pair loop a tick: the player's side is a handful of men (as {@code Drivers.nearestLivingTarget}).
     */
    private @Nullable Entity nearest(Position at, Slither slither) {
        int home = map.biomeAt(slither.homeX, slither.homeY);
        float best = slither.huntRange * slither.huntRange;
        Entity nearest = null;
        for (int i = 0; i < men.size(); i++) {
            Entity man = men.get(i);
            if (!Drivers.huntable(man)) {
                continue;
            }
            Position where = POSITION.get(man);
            if (map.biomeAt(where.x, where.y) != home) {
                continue;
            }
            float dx = where.x - at.x;
            float dy = where.y - at.y;
            float distance = dx * dx + dy * dy;
            if (distance <= best) {
                best = distance;
                nearest = man;
            }
        }
        return nearest;
    }

    /** Turns onto him at its hunting turn, straight at him once one tick's turn would take it past him. */
    private static void onto(Slither slither, Position at, Position him) {
        float toX = him.x - at.x;
        float toY = him.y - at.y;
        float distance = (float) Math.sqrt(toX * toX + toY * toY);
        if (distance <= EPSILON) {
            return;
        }
        toX /= distance;
        toY /= distance;
        if (slither.headingX * toX + slither.headingY * toY >= slither.huntCos) {
            slither.headingX = toX;
            slither.headingY = toY;
            return;
        }
        int side = slither.headingX * toY - slither.headingY * toX >= 0f ? 1 : -1;
        turn(slither, side, slither.huntCos, slither.huntSin);
    }

    /** Rock, a shut door or the edge of its biome within its look ahead. */
    private boolean shut(Position at, float radius, Slither slither) {
        float aheadX = at.x + slither.headingX * slither.lookAhead;
        float aheadY = at.y + slither.headingY * slither.lookAhead;
        return closed(at, radius, aheadX, aheadY, slither);
    }

    private boolean closed(Position at, float radius, float toX, float toY, Slither slither) {
        if (toX < radius || toY < radius || toX > map.worldWidth() - radius || toY > map.worldHeight() - radius) {
            return true;
        }
        if (map.biomeAt(toX, toY) != map.biomeAt(slither.homeX, slither.homeY)) {
            return true;
        }
        return !solid.isClearPath(at.x, at.y, toX, toY, radius);
    }

    /**
     * Whether it was driven last tick and its head did not get half as far as it was going - against a stone, a
     * boulder, a trunk: scenery it is held off and does not see coming.
     */
    private boolean ranUpAgainst(Entity entity, Position at, Slither slither) {
        if (!VELOCITY.has(entity)) {
            return false;
        }
        Velocity velocity = VELOCITY.get(entity);
        float going = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y) * tickSeconds;
        if (going <= EPSILON) {
            return false;
        }
        float movedX = at.x - slither.lastX;
        float movedY = at.y - slither.lastY;
        return movedX * movedX + movedY * movedY < going * going * STUCK * STUCK;
    }

    /**
     * Which way to turn hard: towards whichever side is open, a look ahead out; both open, towards home; neither,
     * on round the way it last turned.
     */
    private int openSide(Position at, float radius, Slither slither) {
        float reach = slither.lookAhead;
        boolean left = !closed(at, radius, at.x - slither.headingY * reach, at.y + slither.headingX * reach, slither);
        boolean right = !closed(at, radius, at.x + slither.headingY * reach, at.y - slither.headingX * reach, slither);
        if (left && right) {
            return towardsHome(at, slither) >= 0 ? 1 : -1;
        }
        if (left) {
            return 1;
        }
        if (right) {
            return -1;
        }
        return slither.tightSide == 0 ? 1 : slither.tightSide;
    }

    /** Back towards home past its roam; otherwise the bend it is in, picking another once that runs out. */
    private int bendOf(Position at, Slither slither) {
        float awayX = at.x - slither.homeX;
        float awayY = at.y - slither.homeY;
        if (awayX * awayX + awayY * awayY > slither.roam * slither.roam) {
            float side = towardsHome(at, slither);
            return side >= 0f ? 1 : -1;
        }
        if (slither.bendTicks <= 0) {
            slither.bend = rng.nextInt(3) - 1;
            slither.bendTicks = slither.bendMinTicks + rng.nextInt(slither.bendMaxTicks - slither.bendMinTicks + 1);
        }
        slither.bendTicks--;
        return slither.bend;
    }

    /** Which side home lies on: above zero to the left of its heading, below to the right. */
    private static float towardsHome(Position at, Slither slither) {
        float toX = slither.homeX - at.x;
        float toY = slither.homeY - at.y;
        return slither.headingX * toY - slither.headingY * toX;
    }

    /** Turns its heading one tick's worth towards {@code side}: 1 left, -1 right, 0 not at all. */
    private static void turn(Slither slither, int side, float cos, float sin) {
        if (side == 0) {
            return;
        }
        float s = side * sin;
        float x = slither.headingX * cos - slither.headingY * s;
        float y = slither.headingX * s + slither.headingY * cos;
        slither.headingX = x;
        slither.headingY = y;
        normalise(slither);
    }

    private static void normalise(Slither slither) {
        float length = (float) Math.sqrt(slither.headingX * slither.headingX + slither.headingY * slither.headingY);
        if (length > EPSILON) {
            slither.headingX /= length;
            slither.headingY /= length;
        }
    }
}
