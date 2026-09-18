package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_REINFORCEMENTS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.POINT_REINFORCEMENT_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Where a level puts the men it is offering.
 *
 * <p>Beside the ground you have to take, because that is what makes the squad the reward for the
 * fighting rather than litter on the way to it (pillar 3). This is level layout, so it is settled
 * once when the world is assembled and is a fact about every level rather than a line each scenario
 * remembers to write.
 */
class ReinforcementDeployTest {

    private static final float FIRST_X = 30f;
    private static final float FIRST_Y = 30f;
    private static final float SECOND_X = 70f;
    private static final float SECOND_Y = 70f;

    @Test
    void laysAHandfulOfReinforcementsBesideEveryControlPoint() {
        Simulation simulation = world(true);

        assertThat(count(simulation, Pickup.class)).isEqualTo(2 * POINT_REINFORCEMENTS);
    }

    @Test
    void laysNoneOnALevelThatNamesNoPoints() {
        Simulation simulation = world(false);

        assertThat(count(simulation, Pickup.class)).isZero();
    }

    /** A step off the ground you are holding, not something that lands in your lap for standing on it. */
    @Test
    void keepsItOffThePointItself() {
        Simulation simulation = world(true);

        for (Entity crate : all(simulation, Pickup.class)) {
            assertThat(nearestPoint(simulation, crate)).isGreaterThan(POINT_RADIUS);
        }
    }

    /** And near enough to be the point's own: within the reach the level configured. */
    @Test
    void keepsItWithinReachOfThePointItBelongsTo() {
        Simulation simulation = world(true);
        TileMap map = simulation.map();

        for (Entity crate : all(simulation, Pickup.class)) {
            Position at = crate.getComponent(Position.class);
            assertThat(besideAPoint(simulation, map, at)).isTrue();
        }
    }

    private static Simulation world(boolean withPoints) {
        return Scenarios.world(
                new Scenario() {
                    @Override
                    public String name() {
                        return "reinforcement layout";
                    }

                    @Override
                    public String description() {
                        return "control points, and the men a level offers beside them";
                    }

                    @Override
                    public void populate(Simulation world) {
                        world.spawnCommander(10f, 10f);
                        if (withPoints) {
                            world.spawnControlPoint(FIRST_X, FIRST_Y);
                            world.spawnControlPoint(SECOND_X, SECOND_Y);
                        }
                    }
                },
                config());
    }

    private static ImmutableArray<Entity> all(Simulation simulation, Class<Pickup> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get());
    }

    private static float nearestPoint(Simulation simulation, Entity crate) {
        Position at = crate.getComponent(Position.class);
        float nearest = Float.MAX_VALUE;
        for (Entity point : simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class).get())) {
            Position where = point.getComponent(Position.class);
            float dx = at.x - where.x;
            float dy = at.y - where.y;
            nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
        }
        return nearest;
    }

    private static boolean besideAPoint(Simulation simulation, TileMap map, Position at) {
        for (Entity point : simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class).get())) {
            Position where = point.getComponent(Position.class);
            TileRect beside = TileRect.around(map, where.x, where.y, POINT_REINFORCEMENT_RADIUS);
            if (beside.contains(map.colAt(at.x), map.rowAt(at.y))) {
                return true;
            }
        }
        return false;
    }
}
