package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;

/**
 * Enforces the composition invariants in {@code docs/design/README.md}: everything that can be hit
 * has a body, every projectile is a real entity rather than a ray, and obstacles are entities like
 * anything else. These are one-line assertions guarding rules that would otherwise be prose.
 */
class SpawnerTest {

    @Test
    void givesEveryProjectileAPositionAndABody() {
        Simulation simulation = simulation();

        Entity projectile = simulation.spawnProjectile(10f, 10f, 1f, 0f);

        assertThat(projectile.getComponent(Position.class)).isNotNull();
        assertThat(projectile.getComponent(Body.class)).isNotNull();
    }

    @Test
    void givesEveryUnitABodySoItCanBeHit() {
        Simulation simulation = simulation();

        Entity commander = simulation.spawnCommander(10f, 10f);
        Entity enemy = simulation.spawnEnemy(20f, 20f);

        assertThat(commander.getComponent(Body.class).radius).isPositive();
        assertThat(enemy.getComponent(Body.class).radius).isPositive();
    }

    @Test
    void makesAnObstacleAnOrdinaryEntityWithAPositionAndABody() {
        Simulation simulation = simulation();

        Entity obstacle = simulation.spawnObstacle(30f, 30f);

        assertThat(obstacle.getComponent(Position.class)).isNotNull();
        assertThat(obstacle.getComponent(Body.class).radius).isPositive();
    }

    @Test
    void tellsTheRendererWhatEverySpawnedEntityIs() {
        Simulation simulation = simulation();

        assertThat(look(simulation.spawnCommander(10f, 10f))).isEqualTo(Appearance.Look.COMMANDER);
        assertThat(look(simulation.spawnEnemy(20f, 20f))).isEqualTo(Appearance.Look.ENEMY);
        assertThat(look(simulation.spawnObstacle(30f, 30f))).isEqualTo(Appearance.Look.OBSTACLE);
        assertThat(look(simulation.spawnProjectile(40f, 40f, 1f, 0f))).isEqualTo(Appearance.Look.PROJECTILE);
    }

    @Test
    void makesUnitsSolidAndMovable() {
        Simulation simulation = simulation();

        Collider commander = simulation.spawnCommander(10f, 10f).getComponent(Collider.class);

        assertThat(commander.solid).isTrue();
        assertThat(commander.movable).isTrue();
    }

    @Test
    void makesObstaclesSolidButImmovable() {
        Simulation simulation = simulation();

        Collider obstacle = simulation.spawnObstacle(30f, 30f).getComponent(Collider.class);

        assertThat(obstacle.solid).isTrue();
        assertThat(obstacle.movable).isFalse();
    }

    @Test
    void makesProjectilesPassThroughRatherThanShove() {
        Simulation simulation = simulation();

        Collider projectile = simulation.spawnProjectile(40f, 40f, 1f, 0f).getComponent(Collider.class);

        assertThat(projectile.solid).isFalse();
    }

    @Test
    void seedsPositionHistoryAtTheSpawnPointSoNothingStreaksIn() {
        Simulation simulation = simulation();

        PreviousPosition previous = simulation.spawnEnemy(12f, 34f).getComponent(PreviousPosition.class);

        assertThat(previous.x).isEqualTo(12f);
        assertThat(previous.y).isEqualTo(34f);
    }

    private static Appearance.Look look(Entity entity) {
        return entity.getComponent(Appearance.class).look;
    }
}
