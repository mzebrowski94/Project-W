package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * Throws away projectiles that have left the arena or flown into rock.
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
 * <p>Marks rather than removes: only {@code ReaperSystem} takes an entity out of the world.
 */
public final class ProjectileBoundsSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private final Spawner spawner;
    private final TileMap map;
    private final float width;
    private final float height;

    private ImmutableArray<Entity> projectiles = new ImmutableArray<>(new Array<>());

    public ProjectileBoundsSystem(TileMap map, Spawner spawner) {
        this.spawner = spawner;
        this.map = map;
        this.width = map.worldWidth();
        this.height = map.worldHeight();
    }

    @Override
    public void addedToEngine(Engine engine) {
        projectiles = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Projectile.class).get());
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
            if (position.x + radius < 0f
                    || position.x - radius > width
                    || position.y + radius < 0f
                    || position.y - radius > height
                    || !map.passableAt(position.x, position.y)) {
                spawner.markDead(projectile);
            }
        }
    }
}
