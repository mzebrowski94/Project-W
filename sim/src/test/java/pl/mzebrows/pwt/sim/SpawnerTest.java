package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.UnitKind;

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

    /**
     * The melee half of the no-hitscan rule (G0005). A swing is a body in the world like anything else,
     * so collision has one path and the player can see what is about to hit him.
     */
    @Test
    void givesEveryStrikeAPositionAndABodySoThereIsNoMeleeHitscan() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(10f, 10f);

        simulation.step(new CommanderInput(0f, 0f, 20f, 10f, false, true));

        Entity strike = simulation
                .engine()
                .getEntitiesFor(Family.all(Strike.class).get())
                .first();
        assertThat(strike.getComponent(Position.class)).isNotNull();
        assertThat(strike.getComponent(Body.class)).isNotNull();
        assertThat(strike.getComponent(Strike.class).owner).isSameAs(commander);
    }

    /**
     * Every strain is built from the same parts and differs only in its numbers, so nothing chasing,
     * leaping or taking a bullet ever has to know which strain it is looking at.
     */
    @Test
    void givesEveryStrainTheSameComponentsSoNothingHasToTellThemApart() {
        Simulation simulation = simulation();

        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, 10f, 10f);
        Entity brute = simulation.spawnUnit(UnitKind.BRUTE, 20f, 20f);

        assertThat(brute.getComponent(Enemy.class)).isNotNull();
        assertThat(brute.getComponent(Body.class)).isNotNull();
        assertThat(brute.getComponent(Leap.class)).isNotNull();
        assertThat(grunt.getComponent(Strain.class).kind).isEqualTo(UnitKind.GRUNT);
        assertThat(brute.getComponent(Strain.class).kind).isEqualTo(UnitKind.BRUTE);
    }

    /** The look follows the strain, so an evolved unit cannot end up drawn as what it used to be. */
    @Test
    void drawsAUnitAsWhateverItsStrainSays() {
        Simulation simulation = simulation();

        Entity unit = simulation.spawnUnit(UnitKind.GRUNT, 10f, 10f);
        assertThat(look(unit)).isEqualTo(Look.GRUNT);

        simulation.spawner().applyStrain(unit, UnitKind.BRUTE);

        assertThat(look(unit)).isEqualTo(Look.BRUTE);
        assertThat(unit.getComponent(Body.class).radius)
                .isEqualTo(simulation.config().unit(UnitKind.BRUTE).radius());
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

        assertThat(look(simulation.spawnCommander(10f, 10f))).isEqualTo(Look.COMMANDER);
        assertThat(look(simulation.spawnEnemy(20f, 20f))).isEqualTo(Look.GRUNT);
        assertThat(look(simulation.spawnObstacle(30f, 30f))).isEqualTo(Look.OBSTACLE);
        assertThat(look(simulation.spawnProjectile(40f, 40f, 1f, 0f))).isEqualTo(Look.PROJECTILE);
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

    private static Look look(Entity entity) {
        return entity.getComponent(Appearance.class).look;
    }
}
