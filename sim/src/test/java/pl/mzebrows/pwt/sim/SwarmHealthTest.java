package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MINE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RABBIT_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The swarm's health is one factor on every strain's and the nest's, read in one place, so a unit
 * spawned, a unit evolved and a check on the numbers all see the same thing (health-scope H3).
 */
class SwarmHealthTest {

    private static final int TWICE = 2;

    private static final int DOUBLE_PERCENT = 200;

    @Test
    void scalesEveryStrainItSpawns() {
        Simulation simulation = tougher();

        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, 50f, 50f);
        Entity brute = simulation.spawnUnit(UnitKind.BRUTE, 20f, 20f);

        assertThat(health(grunt)).isEqualTo(ENEMY_HEALTH * TWICE);
        assertThat(health(brute)).isEqualTo(BRUTE_HEALTH * TWICE);
    }

    @Test
    void scalesTheNestAsReadilyAsWhatComesOutOfIt() {
        Simulation simulation = tougher();

        Entity nest = simulation.spawnNest(UnitKind.GRUNT, 50f, 50f);

        assertThat(health(nest)).isEqualTo(NEST_HEALTH * TWICE);
    }

    /** A barrel, a mine and a rabbit are triggers at one health, and a turret is the players'. */
    @Test
    void leavesEverythingThatIsNotTheSwarmAlone() {
        Simulation simulation = tougher();

        Entity barrel = simulation.spawnBarrel(20f, 20f);
        Entity mine = simulation.spawnMine(30f, 30f);
        Entity rabbit = simulation.spawnRabbit(40f, 40f);
        Entity turret = simulation.spawnTurret(60f, 60f);

        assertThat(health(barrel)).isEqualTo(BARREL_HEALTH);
        assertThat(health(mine)).isEqualTo(MINE_HEALTH);
        assertThat(health(rabbit)).isEqualTo(RABBIT_HEALTH);
        assertThat(health(turret)).isEqualTo(TURRET_HEALTH);
    }

    @Test
    void arrivesAtTheScaledHealthOfWhatItEvolvesInto() {
        Simulation simulation = tougher();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, 50f, 50f);

        simulation.spawner().applyStrain(grunt, UnitKind.BRUTE);

        assertThat(health(grunt)).isEqualTo(BRUTE_HEALTH * TWICE);
    }

    private static Simulation tougher() {
        return new Simulation(config().withSwarm(new SimConfig.SwarmConfig(DOUBLE_PERCENT)));
    }

    private static float health(Entity entity) {
        return entity.getComponent(Health.class).current;
    }
}
