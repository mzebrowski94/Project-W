package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Limpet;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.system.level.Rouse;

/**
 * Flies what a detonator throws, sticks it, carries it, calibrates it, and sets it off when what it hangs on is gone
 * (G0100).
 *
 * <p><strong>Sticking is loud, once</strong>: the enemy within {@code rouseRadius} goes to look at the place.
 *
 * <p><strong>In the air</strong> it spends its reach as a grenade spends its throw - along the aim, never to the aim
 * point (G0019) - and ends where it <strong>sticks</strong>: on what contact resolution says it touched this tick, at
 * the face of the rock or door it flew into, at the arena's edge, or on the ground where its reach runs out.
 * <strong>Stuck</strong>, it rides the edge of what it struck, on the side it struck it, and calibrates.
 *
 * <p><strong>It explodes by dying</strong> (G0018), in whatever state it is in, and here is where three of the four
 * ways it dies are decided: its owner no longer holds the detonator - another gun, or killed; what it is stuck to has
 * died or left the field; the wall it is stuck to has opened. His press is his control system's, and a blast that
 * catches it is the damage path's, as for anything with health. Going
 * down keeps it.
 *
 * <p>Immediately after {@code GrenadeSystem}, so this tick's contacts are fresh and what it rides has finished
 * moving; inside the tick's death window, so a limpet that dies here goes off this tick.
 */
public final class LimpetSystem extends EntitySystem {

    private static final ComponentMapper<Limpet> LIMPET = ComponentMapper.getFor(Limpet.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);
    private static final ComponentMapper<Weapon> WEAPON = ComponentMapper.getFor(Weapon.class);
    private static final ComponentMapper<Dead> DEAD = ComponentMapper.getFor(Dead.class);

    /** Below this two middles are on top of each other and give no side to sit on. */
    private static final float EPSILON = 1e-4f;

    private final Spawner spawner;
    private final Solid solid;
    private final float width;
    private final float height;

    private ImmutableArray<Entity> limpets = new ImmutableArray<>(new Array<>());

    /** Every enemy that can be called to a place. */
    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());

    /** Every entity with a handle, for finding a limpet's owner and what it rides. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    public LimpetSystem(Solid solid, Spawner spawner) {
        this.solid = solid;
        this.spawner = spawner;
        this.width = solid.map().worldWidth();
        this.height = solid.map().worldHeight();
    }

    @Override
    public void addedToEngine(Engine engine) {
        limpets = engine.getEntitiesFor(Family.all(
                        Limpet.class,
                        Position.class,
                        PreviousPosition.class,
                        Velocity.class,
                        Body.class,
                        BlastsOnDeath.class)
                .get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
        watchers = engine.getEntitiesFor(
                Family.all(Enemy.class, Perception.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = limpets.size() - 1; i >= 0; i--) {
            Entity entity = limpets.get(i);
            if (entity.isScheduledForRemoval() || DEAD.has(entity)) {
                continue;
            }
            Limpet limpet = LIMPET.get(entity);
            if (!stillHeld(limpet)) {
                spawner.markDead(entity);
                continue;
            }
            if (!limpet.stuck) {
                fly(entity, limpet, deltaTime);
                if (limpet.stuck) {
                    // Once, the tick it sticks: whatever is within earshot goes to see what landed (G0016).
                    Position at = POSITION.get(entity);
                    Rouse.within(watchers, at.x, at.y, limpet.rouseRadius);
                }
            } else if (!hangOn(entity, limpet)) {
                spawner.markDead(entity);
                continue;
            }
            calibrate(entity, limpet);
        }
    }

    /** Whether the man who threw it is on the field, alive, and still holding a gun that throws limpets. */
    private boolean stillHeld(Limpet limpet) {
        @Nullable Entity owner = Handles.resolve(handled, limpet.owner);
        if (owner == null || DEAD.has(owner) || Life.dying(owner) || !WEAPON.has(owner)) {
            return false;
        }
        return WEAPON.get(owner).limpet.enabled();
    }

    /** A tick of flight, ending where it sticks if anything stops it. */
    private void fly(Entity entity, Limpet limpet, float deltaTime) {
        Position at = POSITION.get(entity);
        Velocity velocity = VELOCITY.get(entity);
        float radius = BODY.get(entity).radius;
        if (limpet.touched) {
            stickTo(limpet, at, velocity, radius);
            return;
        }
        if (!solid.passableAt(at.x, at.y)) {
            // Into rock or a shut door: stuck at its face, remembering a point inside it, which a door opened leaves
            // open.
            limpet.onWall = true;
            limpet.wallX = at.x;
            limpet.wallY = at.y;
            Bounce.toTheFace(solid, at, PREVIOUS.get(entity), velocity);
            settle(limpet, velocity);
            return;
        }
        if (at.x - radius < 0f || at.x + radius > width || at.y - radius < 0f || at.y + radius > height) {
            at.set(Bounce.clamp(at.x, radius, width - radius), Bounce.clamp(at.y, radius, height - radius));
            settle(limpet, velocity);
            return;
        }
        float step = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y) * deltaTime;
        limpet.flown += step;
        limpet.remainingDistance -= step;
        if (limpet.remainingDistance <= 0f) {
            // Its reach run out over open ground: it lands and sticks there, laid like a mine.
            settle(limpet, velocity);
        }
    }

    /**
     * Sticks to what it touched: on the edge of it, on the side it came from, riding it from here on. Something it
     * touched that has already gone leaves it on the ground where it was.
     */
    private void stickTo(Limpet limpet, Position at, Velocity velocity, float radius) {
        @Nullable Entity host = Handles.resolve(handled, limpet.touchedHost);
        float bearingX = at.x - limpet.touchedX;
        float bearingY = at.y - limpet.touchedY;
        float apart = (float) Math.sqrt(bearingX * bearingX + bearingY * bearingY);
        if (apart <= EPSILON) {
            // Dead centre: on the side it came from.
            bearingX = -velocity.x;
            bearingY = -velocity.y;
            apart = (float) Math.sqrt(bearingX * bearingX + bearingY * bearingY);
        }
        settle(limpet, velocity);
        if (host == null || apart <= EPSILON) {
            return;
        }
        limpet.host = limpet.touchedHost;
        limpet.bearingX = bearingX / apart;
        limpet.bearingY = bearingY / apart;
        ride(at, limpet, host, radius);
    }

    /** Stops it where it is, stuck, and starts its calibrating. */
    private static void settle(Limpet limpet, Velocity velocity) {
        limpet.stuck = true;
        limpet.touched = false;
        velocity.set(0f, 0f);
    }

    /**
     * Keeps a stuck limpet on what it is stuck to, and says whether that is still there: a host on the field and not
     * yet dying, a wall still shut. Ground and the arena's edge never go.
     */
    private boolean hangOn(Entity entity, Limpet limpet) {
        if (limpet.onWall && solid.passableAt(limpet.wallX, limpet.wallY)) {
            return false;
        }
        if (limpet.host == Handle.NOBODY) {
            return true;
        }
        @Nullable Entity host = Handles.resolve(handled, limpet.host);
        if (host == null || DEAD.has(host) || Life.dying(host)) {
            return false;
        }
        ride(POSITION.get(entity), limpet, host, BODY.get(entity).radius);
        return true;
    }

    /** On the host's edge, on the side it struck: by position, so nothing that holds the host still takes it off. */
    private static void ride(Position at, Limpet limpet, Entity host, float radius) {
        Position where = POSITION.get(host);
        float reach = radius + (BODY.has(host) ? BODY.get(host).radius : 0f);
        at.set(where.x + limpet.bearingX * reach, where.y + limpet.bearingY * reach);
    }

    /** Counts a stuck limpet's calibrating down, and readies its big blast once it has run out. */
    private static void calibrate(Entity entity, Limpet limpet) {
        if (!limpet.stuck || limpet.calibrated) {
            return;
        }
        if (limpet.remainingTicks > 0) {
            limpet.remainingTicks--;
        }
        if (limpet.remainingTicks <= 0) {
            limpet.calibrated = true;
            BlastsOnDeath reaction = REACTION.get(entity);
            reaction.radius = limpet.calibratedBlastRadius;
            reaction.bittenRadius = limpet.calibratedBlastRadius;
            reaction.harm = limpet.calibratedBlastHarm;
        }
    }
}
