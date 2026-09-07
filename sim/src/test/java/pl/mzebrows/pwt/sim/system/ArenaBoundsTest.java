package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.OBSTACLE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * The arena boundary is not a body (ADR 0007) - it is a constraint the solver applies alongside
 * overlap resolution, repeatedly, so satisfying one cannot silently break the other.
 *
 * <p>The two constraints are not equal, and these tests pin down which is which. Containment inside
 * the arena is absolute: it is applied last on every pass, so nothing ever escapes. Staying out of
 * an obstacle is best-effort, because a crowd squeezed into a dead end is a genuinely unsatisfiable
 * set of constraints and something has to give. It is the obstacle that gives.
 */
class ArenaBoundsTest {

    /** Overlap this small is float noise from repeated separation, not a failure to resolve. */
    private static final float TOLERANCE = 0.01f;

    @Test
    void keepsTheCommanderInsideTheArena() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(2f, 50f);

        for (int i = 0; i < 60; i++) {
            simulation.step(new CommanderInput(-1f, 0f, 0f, 0f, false));
        }

        assertThat(commander.getComponent(Position.class).x).isGreaterThanOrEqualTo(COMMANDER_RADIUS - TOLERANCE);
    }

    @Test
    void keepsTheCommanderInsideTheFarEdgeToo() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(ARENA - 2f, 50f);

        for (int i = 0; i < 60; i++) {
            simulation.step(new CommanderInput(1f, 0f, 0f, 0f, false));
        }

        assertThat(commander.getComponent(Position.class).x).isLessThanOrEqualTo(ARENA - COMMANDER_RADIUS + TOLERANCE);
    }

    /**
     * The reason the solver iterates. Pushing a unit out of an obstacle can shove it through a wall,
     * and clamping it back puts it inside the obstacle again - one pass of each only satisfies
     * whichever ran last. Here both constraints are satisfiable at once, so both must hold.
     */
    @Test
    void keepsAUnitOutOfAnObstacleWhilePressedIntoACorner() {
        Simulation simulation = simulation();
        simulation.spawnObstacle(5f, 5f);
        Entity commander = simulation.spawnCommander(20f, 20f);

        for (int i = 0; i < 240; i++) {
            simulation.step(new CommanderInput(-1f, -1f, 0f, 0f, false));
        }

        Position position = commander.getComponent(Position.class);
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(position.x).isGreaterThanOrEqualTo(COMMANDER_RADIUS - TOLERANCE);
            soft.assertThat(position.y).isGreaterThanOrEqualTo(COMMANDER_RADIUS - TOLERANCE);
            soft.assertThat(distance(position, 5f, 5f))
                    .as("distance from the obstacle it was pressed against")
                    .isGreaterThanOrEqualTo(OBSTACLE_RADIUS + COMMANDER_RADIUS - TOLERANCE);
        });
    }

    /**
     * Nine units driven into a channel narrower than they collectively fit. The constraints cannot
     * all be met, and the guarantee that survives is containment: whatever else happens, nothing
     * leaves the arena. Units will be squeezed into the obstacle here, and that is accepted
     * behaviour, not a bug to fix by raising the pass count.
     */
    @Test
    void neverLetsACrowdSqueezeAnythingOutOfTheArena() {
        Simulation simulation = simulation();
        simulation.spawnObstacle(OBSTACLE_RADIUS + COMMANDER_RADIUS * 2f + 1f, 50f);
        simulation.spawnCommander(20f, 50f);
        for (int i = 0; i < 8; i++) {
            simulation.spawnEnemy(22f + i * ENEMY_RADIUS, 50f);
        }

        for (int i = 0; i < 240; i++) {
            simulation.step(new CommanderInput(-1f, 0f, 0f, 0f, false));
        }

        SoftAssertions.assertSoftly(soft -> {
            for (Entity unit : units(simulation)) {
                Position position = unit.getComponent(Position.class);
                float radius = unit.getComponent(Body.class).radius;
                soft.assertThat(position.x).isBetween(radius - TOLERANCE, ARENA - radius + TOLERANCE);
                soft.assertThat(position.y).isBetween(radius - TOLERANCE, ARENA - radius + TOLERANCE);
            }
        });
    }

    @Test
    void removesAProjectileThatLeavesTheArena() {
        Simulation simulation = simulation();
        simulation.spawnProjectile(ARENA - 1f, 50f, 1f, 0f);

        for (int i = 0; i < 3; i++) {
            simulation.step();
        }

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void keepsAProjectileStillInsideTheArena() {
        Simulation simulation = simulation();
        simulation.spawnProjectile(50f, 50f, 1f, 0f);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    private static ImmutableArray<Entity> units(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Position.class, Body.class)
                        .one(Commander.class, Enemy.class)
                        .get());
    }

    private static float distance(Position position, float x, float y) {
        float dx = position.x - x;
        float dy = position.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
