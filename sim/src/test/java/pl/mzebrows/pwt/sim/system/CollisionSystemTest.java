package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.OBSTACLE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

class CollisionSystemTest {

    @Test
    void pushesTwoOverlappingUnitsApart() {
        Simulation simulation = simulation();
        Entity left = simulation.spawnEnemy(50f, 50f);
        Entity right = simulation.spawnEnemy(50.5f, 50f);

        simulation.step();

        assertThat(separation(left, right)).isGreaterThan(0.5f);
    }

    @Test
    void neverMovesAnObstacle() {
        Simulation simulation = simulation();
        Entity obstacle = simulation.spawnObstacle(50f, 50f);
        simulation.spawnEnemy(50f + OBSTACLE_RADIUS, 50f);

        simulation.step();

        Position position = obstacle.getComponent(Position.class);
        assertThat(position.x).isEqualTo(50f);
        assertThat(position.y).isEqualTo(50f);
    }

    @Test
    void stopsAUnitWalkingIntoAnObstacle() {
        Simulation simulation = simulation();
        simulation.spawnObstacle(60f, 50f);
        Entity commander = simulation.spawnCommander(50f, 50f);

        for (int i = 0; i < 60; i++) {
            simulation.step(new CommanderInput(1f, 0f, 0f, 0f, false));
        }

        assertThat(commander.getComponent(Position.class).x).isLessThan(60f - OBSTACLE_RADIUS);
    }

    /** How much a projectile takes off, and how many it takes, is {@code CombatTest}'s business. */
    @Test
    void leavesAnEnemyStandingAfterASingleHit() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(50f, 50f);
        simulation.spawnProjectile(49.5f, 50f, 1f, 0f);

        simulation.step();

        assertThat(count(simulation, Enemy.class)).isEqualTo(1);
    }

    @Test
    void spendsTheProjectileThatKilledTheEnemy() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(50f, 50f);
        simulation.spawnProjectile(49.5f, 50f, 1f, 0f);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void stopsAProjectileAtAnObstacle() {
        Simulation simulation = simulation();
        simulation.spawnObstacle(50f, 50f);
        simulation.spawnProjectile(48f, 50f, 1f, 0f);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void letsAProjectilePassThroughTheCommanderThatFiredIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        simulation.spawnProjectile(48f, 50f, 1f, 0f);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    @Test
    void pushesApartTwoUnitsSpawnedAtExactlyTheSamePoint() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnEnemy(50f, 50f);
        Entity second = simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(separation(first, second)).isGreaterThan(0f);
    }

    @Test
    void leavesTwoOverlappingObstaclesWhereTheyAre() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnObstacle(50f, 50f);
        Entity second = simulation.spawnObstacle(51f, 50f);

        simulation.step();

        assertThat(first.getComponent(Position.class).x).isEqualTo(50f);
        assertThat(second.getComponent(Position.class).x).isEqualTo(51f);
    }

    @Test
    void letsTwoProjectilesPassThroughEachOther() {
        Simulation simulation = simulation();
        simulation.spawnProjectile(50f, 50f, 1f, 0f);
        simulation.spawnProjectile(50.2f, 50f, 1f, 0f);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(2);
    }

    private static float separation(Entity a, Entity b) {
        Position first = a.getComponent(Position.class);
        Position second = b.getComponent(Position.class);
        float dx = second.x - first.x;
        float dy = second.y - first.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
