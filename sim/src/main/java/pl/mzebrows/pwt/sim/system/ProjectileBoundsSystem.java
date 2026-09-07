package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * Throws away projectiles that have left the arena.
 *
 * <p>Units are kept inside by the constraint solver; projectiles deliberately are not, because a
 * projectile that stops at the wall and sits there is worse than one that flies off and disappears.
 * This system is what stops that becoming a leak.
 */
public final class ProjectileBoundsSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);

    private final float width;
    private final float height;

    private ImmutableArray<Entity> projectiles = new ImmutableArray<>(new Array<>());

    public ProjectileBoundsSystem(SimConfig.ArenaConfig arena) {
        this.width = arena.width();
        this.height = arena.height();
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
                    || position.y - radius > height) {
                getEngine().removeEntity(projectile);
            }
        }
    }
}
