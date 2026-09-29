package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * Impassable terrain is not a body and never enters the pairwise path (G0009). It is a constraint on
 * where a body may be, applied exactly the way the arena boundary already is - a clamp, not a
 * collision test - which is why this sits beside {@code ArenaBoundsTest} rather than inside
 * {@code CollisionSystemTest}.
 *
 * <p>The test that matters most here is the sliding one. Resolving a circle against each solid tile
 * independently makes a flat wall behave like a row of separate blocks, and a unit walking along it
 * catches on every seam - which no other test would notice and every player would.
 */
class TerrainCollisionTest {

    /** Rock fills this row of tiles, so the wall's lower face is at y = 50. */
    private static final int WALL_ROW = 10;

    private static final float WALL_BOTTOM = 50f;
    private static final float WALL_TOP = 55f;
    private static final float TOLERANCE = 0.01f;

    @Test
    void stopsAUnitWalkedIntoAWall() {
        Simulation simulation = on(wall());
        Entity commander = simulation.spawnCommander(50f, 45f);

        steps(simulation, 60, 0f, 1f);

        assertThat(commander.getComponent(Position.class).y)
                .isLessThanOrEqualTo(WALL_BOTTOM - COMMANDER_RADIUS + TOLERANCE);
    }

    /**
     * Held against the wall and pushed sideways. If tile seams snag, the unit stops early and this
     * distance collapses; the wall must behave as one surface rather than as ten blocks.
     */
    @Test
    void letsAUnitSlideAlongAWallWithoutSnaggingOnTileSeams() {
        Simulation simulation = on(wall());
        Entity commander = simulation.spawnCommander(20f, 48f);

        steps(simulation, 60, 1f, 1f);

        Position position = commander.getComponent(Position.class);
        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(position.x)
                    .as("travelled along the wall rather than catching on it")
                    .isGreaterThan(50f);
            soft.assertThat(position.y)
                    .as("stayed under the wall")
                    .isLessThanOrEqualTo(WALL_BOTTOM - COMMANDER_RADIUS + TOLERANCE);
        });
    }

    @Test
    void neverLeavesAUnitStandingInRock() {
        Simulation simulation = on(wall());
        Entity commander = simulation.spawnCommander(50f, 45f);

        for (int i = 0; i < 120; i++) {
            simulation.step(new CommanderInput(0f, 1f, 0f, 0f, false, false));
            assertThat(simulation
                            .map()
                            .passableAt(
                                    commander.getComponent(Position.class).x, commander.getComponent(Position.class).y))
                    .isTrue();
        }
    }

    @Test
    void leavesAUnitOnOpenGroundWhereItIs() {
        Simulation simulation = on(wall());
        Entity commander = simulation.spawnCommander(50f, 20f);

        simulation.step();

        Position position = commander.getComponent(Position.class);
        assertThat(position.x).isCloseTo(50f, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(position.y).isCloseTo(20f, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    /** Nothing should ever spawn inside rock, but a crowd can shove one in - it has to get back out. */
    @Test
    void pushesAUnitThatEndsUpInsideRockBackOutOfIt() {
        Simulation simulation = on(wall());
        Entity commander = simulation.spawnCommander(50f, 52.5f);

        steps(simulation, 5, 0f, 0f);

        Position position = commander.getComponent(Position.class);
        assertThat(position.y)
                .satisfiesAnyOf(
                        y -> assertThat(y).isLessThanOrEqualTo(WALL_BOTTOM - COMMANDER_RADIUS + TOLERANCE),
                        y -> assertThat(y).isGreaterThanOrEqualTo(WALL_TOP + COMMANDER_RADIUS - TOLERANCE));
    }

    /** An obstacle is scenery the level put there; terrain does not get to rearrange it. */
    @Test
    void leavesAnImmovableBodyWhereTheLevelPutIt() {
        Simulation simulation = on(wall());
        Entity obstacle = simulation.spawnObstacle(50f, 52.5f);

        steps(simulation, 5, 0f, 0f);

        assertThat(obstacle.getComponent(Position.class).y)
                .isCloseTo(52.5f, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void stopsAProjectileThatFliesIntoRock() {
        Simulation simulation = on(wall());
        simulation.spawnProjectile(50f, 45f, 0f, 1f);

        steps(simulation, 10, 0f, 0f);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void letsAProjectileCarryOnOverOpenGround() {
        Simulation simulation = on(wall());
        simulation.spawnProjectile(50f, 20f, 0f, 1f);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    /**
     * An isolated rock tile has four exposed corners, and a body overlapping one must be pushed out
     * along the diagonal. This is the case the internal-edge check has to keep <em>allowing</em>
     * while it suppresses the corners buried inside a wall.
     */
    @Test
    void pushesABodyOffTheExposedCornerOfAPillar() {
        Simulation simulation = on(ground().set(10, 10, Terrain.ROCK).build());
        Entity commander = simulation.spawnCommander(49.5f, 49.5f);

        steps(simulation, 2, 0f, 0f);

        Position position = commander.getComponent(Position.class);
        assertThat(distance(position, 50f, 50f))
                .as("cleared the pillar's corner")
                .isGreaterThanOrEqualTo(COMMANDER_RADIUS - TOLERANCE);
    }

    /**
     * The claim that terrain sits in the same tier as arena containment. Nine bodies driven into a
     * wall is an unsatisfiable set of constraints; what has to give is units overlapping each other,
     * never one of them ending up inside the rock.
     */
    @Test
    void keepsEveryoneOutOfRockWhenACrowdIsDrivenIntoAWall() {
        Simulation simulation = on(wall());
        simulation.spawnCommander(50f, 46f);
        for (int i = 0; i < 8; i++) {
            simulation.spawnEnemy(44f + i * 2f, 40f);
        }

        steps(simulation, 240, 0f, 1f);

        SoftAssertions.assertSoftly(soft -> {
            for (Entity unit : units(simulation)) {
                Position position = unit.getComponent(Position.class);
                soft.assertThat(simulation.map().passableAt(position.x, position.y))
                        .as("unit at %s,%s is standing in rock", position.x, position.y)
                        .isTrue();
            }
        });
    }

    /**
     * Why {@code assets/sim/*.properties} says a brute needs two tiles of clearance. A gap one tile wide is
     * five world units, and a body wider than that cannot be squeezed through it by any amount of
     * pushing - which is what makes a corridor a real decision when a fragment is laid out.
     */
    @Test
    void refusesAGapNarrowerThanTheBodyTryingToFitThrough() {
        Entity commander = drivenAtTheGap(2.6f);

        assertThat(commander.getComponent(Position.class).x)
                .as("never got into the gap")
                .isLessThan(50f);
    }

    @Test
    void letsABodyThroughAGapThatIsWideEnoughForIt() {
        Entity commander = drivenAtTheGap(2f);

        assertThat(commander.getComponent(Position.class).x)
                .as("came out the far side")
                .isGreaterThan(55f);
    }

    /** A wall down column 10 with a single tile missing, and a commander driven straight at the hole. */
    private static Entity drivenAtTheGap(float radius) {
        SimConfig config = AlphaWorlds.config();
        config = config.withCommander(config.commander().withRadius(radius));
        TileMap map = ground().rect(10, 0, 1, AlphaWorlds.ARENA_TILES, Terrain.ROCK)
                .set(10, 10, Terrain.PLAIN)
                .build();
        Simulation simulation = AlphaWorlds.on(config, map);

        Entity commander = simulation.spawnCommander(40f, 52.5f);
        steps(simulation, 60, 1f, 0f);
        return commander;
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

    private static TileMap wall() {
        return ground().rect(0, WALL_ROW, 20, 1, Terrain.ROCK).build();
    }

    private static void steps(Simulation simulation, int count, float moveX, float moveY) {
        for (int i = 0; i < count; i++) {
            simulation.step(new CommanderInput(moveX, moveY, 0f, 0f, false, false));
        }
    }
}
