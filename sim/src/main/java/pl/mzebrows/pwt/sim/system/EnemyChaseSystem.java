package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Points every enemy straight at the commander, at its own speed. The whole of enemy behaviour for
 * now, deliberately - obstacles are avoided by bumping into them, not by pathing. Listed as
 * scaffolding in docs/design/alpha-phase.md.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because the target is resolved
 * once per tick rather than once per enemy.
 */
public final class EnemyChaseSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);

    private static final float EPSILON = 1e-4f;

    private ImmutableArray<Entity> enemies = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        enemies = engine.getEntitiesFor(Family.all(Enemy.class, Position.class, Velocity.class, Speed.class)
                .get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (commanders.size() == 0) {
            halt();
            return;
        }
        Position target = POSITION.get(commanders.first());
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            Position position = POSITION.get(enemy);
            Velocity velocity = VELOCITY.get(enemy);
            float toTargetX = target.x - position.x;
            float toTargetY = target.y - position.y;
            float distance = (float) Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY);
            if (distance <= EPSILON) {
                velocity.set(0f, 0f);
                continue;
            }
            float speed = SPEED.get(enemy).value;
            velocity.set(toTargetX / distance * speed, toTargetY / distance * speed);
        }
    }

    /** With nothing to chase, enemies stand still rather than drifting on their last velocity. */
    private void halt() {
        for (int i = 0; i < enemies.size(); i++) {
            VELOCITY.get(enemies.get(i)).set(0f, 0f);
        }
    }
}
