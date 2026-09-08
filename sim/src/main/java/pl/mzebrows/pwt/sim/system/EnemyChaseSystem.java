package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Points every enemy straight at the commander, at its own speed. The whole of walking-toward-you for
 * now, deliberately - obstacles are avoided by bumping into them, not by pathing. Listed as
 * scaffolding in docs/design/alpha-phase.md.
 *
 * <p>Closing the last of the distance is {@code EnemyLeapSystem}'s business, and it runs after this
 * one and overwrites what this wrote for anything mid-leap. Neither system knows about the other.
 *
 * <p>A commander lying dead on the field is not a target: the pack stops rather than piling onto a
 * corpse, which is what makes the three seconds after a kill read as a beat rather than a scrum.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because the target is resolved
 * once per tick rather than once per enemy.
 */
public final class EnemyChaseSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Speed> SPEED = ComponentMapper.getFor(Speed.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

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
        Position target = livingTarget();
        if (target == null) {
            halt();
            return;
        }
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (held(enemy)) {
                continue;
            }
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

    /** The first commander still on his feet. A body on the ground is not chased. */
    private @Nullable Position livingTarget() {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            if (DYING.has(commander) && DYING.get(commander).begun) {
                continue;
            }
            return POSITION.get(commander);
        }
        return null;
    }

    /**
     * Held still by a blow, or dead on the ground. Neither drives itself: a stunned unit's velocity
     * belongs to {@code StunSystem} until it comes round, and a corpse's belongs to nobody.
     */
    private static boolean held(Entity enemy) {
        if (STUNNED.has(enemy) && STUNNED.get(enemy).active()) {
            return true;
        }
        return DYING.has(enemy) && DYING.get(enemy).begun;
    }

    /** With nothing to chase, enemies stand still rather than drifting on their last velocity. */
    private void halt() {
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemy = enemies.get(i);
            if (held(enemy)) {
                continue;
            }
            VELOCITY.get(enemy).set(0f, 0f);
        }
    }
}
