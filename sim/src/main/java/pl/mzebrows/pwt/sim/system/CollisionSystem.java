package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.collision.ConstraintSolver;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * Runs the constraint solver, then decides what this tick's contacts mean.
 *
 * <p>The split matters: the solver knows geometry and nothing else, and this class knows the game
 * and no geometry at all. When damage arrives, only this class changes.
 *
 * <p>Deciding what a contact means does require asking what the two entities are, and this is the
 * one place in the simulation where that is the right shape - a collision is inherently about the
 * pair, so the alternative is a system per combination.
 */
public final class CollisionSystem extends EntitySystem implements ConstraintSolver.ContactListener {

    private static final ComponentMapper<Projectile> PROJECTILE = ComponentMapper.getFor(Projectile.class);
    private static final ComponentMapper<Enemy> ENEMY = ComponentMapper.getFor(Enemy.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);

    private final ConstraintSolver solver;

    private ImmutableArray<Entity> bodies = new ImmutableArray<>(new Array<>());

    public CollisionSystem(SimConfig config) {
        this.solver = new ConstraintSolver(config, this);
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Collider.class).get());
    }

    @Override
    public void update(float deltaTime) {
        solver.solve(bodies);
    }

    @Override
    public void onContact(Entity a, Entity b) {
        boolean firstIsProjectile = PROJECTILE.has(a);
        boolean secondIsProjectile = PROJECTILE.has(b);
        if (firstIsProjectile == secondIsProjectile) {
            return;
        }
        if (firstIsProjectile) {
            resolveHit(a, b);
        } else {
            resolveHit(b, a);
        }
    }

    /**
     * A projectile is spent on whatever stops it. Removing the enemy is alpha-phase scaffolding
     * standing in for damage, not a decision that enemies die in one shot - it is replaced by the
     * health and damage systems, and until then nothing should be balanced around it. See {@code
     * docs/design/alpha-phase.md}.
     */
    private void resolveHit(Entity projectile, Entity target) {
        if (COMMANDER.has(target) || projectile.isScheduledForRemoval()) {
            return;
        }
        getEngine().removeEntity(projectile);
        if (ENEMY.has(target)) {
            getEngine().removeEntity(target);
        }
    }
}
