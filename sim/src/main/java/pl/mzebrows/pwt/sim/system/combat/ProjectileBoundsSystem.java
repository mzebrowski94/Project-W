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
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Throws away projectiles that have left the arena, flown into rock, or flown as far as their gun
 * lets them.
 *
 * <p>Units are kept inside by the constraint solver; projectiles deliberately are not, because a
 * projectile that stops at the wall and sits there is worse than one that flies off and disappears.
 * This system is what stops that becoming a leak.
 *
 * <p>Terrain stops a shot here rather than in the solver, for the same reason contact damage is not
 * applied there: the solver knows where a body may be and nothing about what any of it means. A wall
 * is checked against the projectile's centre rather than its edge, so a shot visibly reaches the rock
 * before it goes out - the same way a shot leaving the arena is only dropped once it is fully past.
 *
 * <p><strong>A gun with a reach is one whose shots expire</strong>, and expiring is dying like any
 * other end - so a shot that carries a blast leaves one where it runs out, through the reaction
 * everything explodes through (G0018), exactly as a grenade at the end of its throw does. Zero range
 * is "until it hits something", which is what almost every gun in the game declares.
 *
 * <p><strong>A shot that flies into rock ends at its face</strong>, not inside it: it is put back
 * along the way it came to where it met the wall before it dies. Whatever it leaves - a blast, fire, a
 * spatter - is then on this side of the wall, where a blast's clear line can reach whoever stands beside
 * it; left inside, the line started in rock and the blast caught nobody.
 *
 * <p><strong>A shot that bounces comes off rock and the arena's edge instead</strong>, the way a grenade
 * does ({@link Bounce}), for as long as it has bounces left; the last one it has not got ends it here like
 * any other.
 *
 * <p><strong>A shut door is a wall here too</strong> (ADR 0022): a shot ends at its face. A heavy gun's shot lands
 * its blow on the door as it does (G0069); every other shot leaves it whole.
 *
 * <p>Marks rather than removes: only {@code ReaperSystem} takes an entity out of the world.
 */
public final class ProjectileBoundsSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Projectile> PROJECTILE = ComponentMapper.getFor(Projectile.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<ContactDamage> BLOW = ComponentMapper.getFor(ContactDamage.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);

    /** How far short of the face a shot is put, so the point it ends on is on the open side of it. */
    private static final float SHORT_OF_THE_FACE = 1e-3f;

    private final Spawner spawner;
    private final Solid map;
    private final float width;
    private final float height;

    private ImmutableArray<Entity> projectiles = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());

    public ProjectileBoundsSystem(Solid map, Spawner spawner) {
        this.spawner = spawner;
        this.map = map;
        this.width = map.map().worldWidth();
        this.height = map.map().worldHeight();
    }

    @Override
    public void addedToEngine(Engine engine) {
        projectiles = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Projectile.class).get());
        doors = engine.getEntitiesFor(
                Family.all(Door.class, Damage.class, Handle.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Entity projectile = projectiles.get(i);
            if (projectile.isScheduledForRemoval()) {
                continue;
            }
            Position position = POSITION.get(projectile);
            float radius = BODY.get(projectile).radius;
            Projectile shot = PROJECTILE.get(projectile);
            if (shot.spent(position.x, position.y)) {
                spawner.markDead(projectile);
                continue;
            }
            if (shot.bounces() && comesOff(projectile, shot, position, radius)) {
                continue;
            }
            if (position.x + radius < 0f
                    || position.x - radius > width
                    || position.y + radius < 0f
                    || position.y - radius > height) {
                spawner.markDead(projectile);
            } else if (!map.passableAt(position.x, position.y)) {
                if (shot.breaksDoors) {
                    breakAt(projectile, map.doorAt(position.x, position.y));
                }
                backToTheFace(projectile, position);
                spawner.markDead(projectile);
            }
        }
    }

    /** Lands a heavy shot's blow on the shut door it flew into, if it was a door and not rock (G0069). */
    private void breakAt(Entity projectile, long door) {
        @Nullable Entity struck = Handles.resolve(doors, door);
        if (struck != null && BLOW.has(projectile)) {
            DAMAGE.get(struck).land(BLOW.get(projectile));
        }
    }

    /** Turns a shot with a bounce left off rock or the arena's edge, spending it, and says whether it did. */
    private boolean comesOff(Entity projectile, Projectile shot, Position position, float radius) {
        if (!PREVIOUS.has(projectile) || !VELOCITY.has(projectile)) {
            return false;
        }
        Velocity velocity = VELOCITY.get(projectile);
        boolean offRock = Bounce.offRock(map, position, PREVIOUS.get(projectile), velocity);
        boolean offEdge = Bounce.offEdge(position, radius, width, height, velocity);
        if (!offRock && !offEdge) {
            return false;
        }
        shot.bounceAt(position.x, position.y);
        return true;
    }

    /**
     * Puts a shot that has flown into rock back where its path met the face. It is walked from where it
     * stood last tick - or, for one fired with its muzzle already in the wall, from a tile further back
     * along its line, which is inside whoever fired it. Left where it is when no open ground lies behind.
     */
    private void backToTheFace(Entity projectile, Position position) {
        if (!PREVIOUS.has(projectile) || !VELOCITY.has(projectile)) {
            return;
        }
        Velocity velocity = VELOCITY.get(projectile);
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        if (speed <= 0f) {
            return;
        }
        float directionX = velocity.x / speed;
        float directionY = velocity.y / speed;
        PreviousPosition previous = PREVIOUS.get(projectile);
        float fromX = previous.x;
        float fromY = previous.y;
        if (!map.passableAt(fromX, fromY)) {
            fromX -= directionX * map.map().tileSize();
            fromY -= directionY * map.map().tileSize();
        }
        float spanX = position.x - fromX;
        float spanY = position.y - fromY;
        float open = map.passableFraction(fromX, fromY, position.x, position.y);
        if (open <= 0f) {
            return;
        }
        position.set(
                fromX + spanX * open - directionX * SHORT_OF_THE_FACE,
                fromY + spanY * open - directionY * SHORT_OF_THE_FACE);
    }
}
