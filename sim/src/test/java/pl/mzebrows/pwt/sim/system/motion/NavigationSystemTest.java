package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LOSE_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.UnitKind;

class NavigationSystemTest {

    private static final int WALL_COL = 10;
    private static final int GAP_ROW = 2;
    private static final int LANE_ROW = 10;
    private static final int LONG_ENOUGH_TO_ROUND_THE_CORNER = 150;

    /** Wider than a tile, so a one-tile crack is a wall to it and not a shortcut. */
    private static final float BROAD = 2.6f;

    @Test
    void movesAnEnemyTowardsTheCommander() {
        Simulation simulation = simulation();
        simulation.spawnCommander(90f, 50f);
        Entity enemy = simulation.spawnEnemy(10f, 50f);

        simulation.step();

        assertThat(enemy.getComponent(Position.class).x).isGreaterThan(10f);
    }

    @Test
    void closesBothAxesAtOnceWhenTheCommanderIsDiagonal() {
        Simulation simulation = simulation();
        simulation.spawnCommander(90f, 90f);
        Entity enemy = simulation.spawnEnemy(10f, 10f);

        simulation.step();

        Position position = enemy.getComponent(Position.class);
        assertThat(position.x).isGreaterThan(10f);
        assertThat(position.y).isGreaterThan(10f);
    }

    @Test
    void standsStillOnceItIsExactlyOnTopOfTheCommander() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isZero();
        assertThat(velocity.y).isZero();
    }

    @Test
    void walksAnEnemyRoundAWallRatherThanIntoIt() {
        Simulation simulation = on(walledWithAGapAtTheTop());
        simulation.spawnCommander(centre(WALL_COL + 4), centre(LANE_ROW));
        Entity enemy = simulation.spawnEnemy(centre(WALL_COL - 3), centre(LANE_ROW));

        steps(simulation, LONG_ENOUGH_TO_ROUND_THE_CORNER);

        assertThat(enemy.getComponent(Position.class).y).isLessThan(centre(LANE_ROW - 2));
    }

    /**
     * The same wall, and the man on the far side of it is a <em>trooper</em>.
     *
     * <p>Routes used to be built for the commander alone, so anything hunting anybody else walked
     * straight at them - which on a level with rock in it meant walking into the rock and staying
     * there for the rest of the game. The field is flooded from every man on the player's side now,
     * so it leads to whichever is nearest by route, which is what G0026 already said happens.
     */
    @Test
    void walksAnEnemyRoundAWallToATrooperJustAsReadily() {
        Simulation simulation = on(walledWithAGapAtTheTop());
        // Far away and behind it, so nothing here is routing to him by accident.
        simulation.spawnCommander(centre(1), centre(LANE_ROW));
        simulation.spawnTrooper(centre(WALL_COL + 4), centre(LANE_ROW));
        Entity enemy = simulation.spawnEnemy(centre(WALL_COL - 3), centre(LANE_ROW));

        steps(simulation, LONG_ENOUGH_TO_ROUND_THE_CORNER);

        assertThat(enemy.getComponent(Position.class).y).isLessThan(centre(LANE_ROW - 2));
    }

    @Test
    void takesAGruntThroughACrackAndLeavesABruteOnTheWrongSideOfIt() {
        SimConfig config = config().withUnit(UnitKind.BRUTE, brute -> brute.withRadius(BROAD));
        Simulation simulation = on(config, walledWithAGapAtTheTop());
        simulation.spawnCommander(centre(WALL_COL + 4), centre(GAP_ROW));
        Entity grunt = simulation.spawnEnemy(centre(WALL_COL - 3), centre(GAP_ROW));
        Entity brute = simulation.spawnBrute(centre(WALL_COL - 3), centre(GAP_ROW + 4));

        steps(simulation, LONG_ENOUGH_TO_ROUND_THE_CORNER);

        assertThat(grunt.getComponent(Position.class).x).isGreaterThan(centre(WALL_COL));
        assertThat(brute.getComponent(Position.class).x).isLessThan(centre(WALL_COL));
    }

    @Test
    void leavesAnEnemyThatHasNotNoticedHimWhereItIs() {
        Simulation simulation = watching();
        simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f + LOSE_RANGE + 1f, 50f);

        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isZero();
        assertThat(velocity.y).isZero();
    }

    @Test
    void leavesEnemiesStandingWhenThereIsNoCommander() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(10f, 50f);

        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isZero();
        assertThat(velocity.y).isZero();
    }

    /** One wall across the middle with its only gap far from the line between the two of them. */
    private static TileMap walledWithAGapAtTheTop() {
        TileMap.Builder builder = ground();
        return builder.rect(WALL_COL, 0, 1, builder.rows(), Terrain.ROCK)
                .set(WALL_COL, GAP_ROW, Terrain.PLAIN)
                .build();
    }

    private static float centre(int tile) {
        return tile * TILE + TILE * 0.5f;
    }
}
