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
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Decides, once a tick, which units are hunting the commander and which have not noticed him.
 *
 * <p>Everything downstream reads the answer and never asks the question again: navigation drives the
 * ones that are hunting, wandering drives the ones that are not, and the renderer shows which is
 * which. One place deciding is what stops two systems disagreeing about whether a unit is awake.
 *
 * <p><strong>The hysteresis is the mechanic.</strong> A unit notices him at one range and gives up at
 * a longer one, so the distance at which it wakes is not the distance at which it sleeps. With a
 * single range, a commander standing on the boundary would flip a whole pack between hunting and
 * idling every tick - and the boundary is exactly where a player edging forward stands.
 *
 * <p>A held unit is skipped entirely rather than re-evaluated. Being stunned does not make you forget
 * what you were chasing, and a corpse is not deciding anything.
 *
 * <p>An {@code EntitySystem} rather than an {@code IteratingSystem} because the commander is resolved
 * once per tick rather than once per unit.
 */
public final class PerceptionSystem extends EntitySystem {

    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Perception> PERCEPTION = ComponentMapper.getFor(Perception.class);

    private ImmutableArray<Entity> watchers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    @Override
    public void addedToEngine(Engine engine) {
        watchers = engine.getEntitiesFor(
                Family.all(Perception.class, Position.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        @Nullable Position target = Drivers.livingTarget(commanders);
        for (int i = 0; i < watchers.size(); i++) {
            Entity watcher = watchers.get(i);
            if (Drivers.held(watcher)) {
                continue;
            }
            Perception perception = PERCEPTION.get(watcher);
            if (perception.alwaysAggressive) {
                perception.aggro = true;
                continue;
            }
            if (target == null) {
                perception.aggro = false;
                continue;
            }
            Position position = POSITION.get(watcher);
            float toTargetX = target.x - position.x;
            float toTargetY = target.y - position.y;
            float range = perception.rangeInPlay();
            perception.aggro = toTargetX * toTargetX + toTargetY * toTargetY <= range * range;
        }
    }
}
