package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * The one thing about this arena that is a claim rather than a layout: <strong>the last two points
 * are the same walk away</strong>, so it actually tests what happens at a tie.
 *
 * <p>Worth pinning because it is silent when it breaks. Move a wall a tile, or nudge a point, and the
 * arena still plays fine - it has just stopped being the experiment it was built for, and the squad
 * picking one point over the other would look like the tie-break working when it was never a tie.
 *
 * <p>Measured by route rather than by line, because that is what the troopers measure.
 */
class AdvanceScenarioTest {

    private static final String NAME = "Advance";

    @Test
    void putsTheLastTwoPointsTheSameWalkFromTheMiddleOne() {
        Simulation simulation = world();
        TileMap map = simulation.map();
        List<Position> points = pointsOf(simulation);
        Position middle = points.get(1);

        int toNorth = walk(map, simulation.config(), points.get(2), middle);
        int toSouth = walk(map, simulation.config(), points.get(3), middle);

        assertThat(toNorth).isPositive();
        assertThat(toNorth).isEqualTo(toSouth);
    }

    /** Four of them, and a squad, or the tie has nothing to happen to. */
    @Test
    void laysOutFourPointsAndASquadToTakeThem() {
        Simulation simulation = world();

        assertThat(pointsOf(simulation)).hasSize(4);
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(pl.mzebrows.pwt.sim.component.Trooper.class)
                                .get())
                        .size())
                .isEqualTo(simulation.config().trooper().squadSize());
    }

    /** How many tiles of walking it is to that point, for a body the size of a trooper. */
    private static int walk(TileMap map, SimConfig config, Position goal, Position from) {
        FlowField field = FlowField.over(map, config.trooper().radius());
        field.rebuildFor(map.colAt(goal.x), map.rowAt(goal.y));
        return field.stepsTo(map.colAt(from.x), map.rowAt(from.y));
    }

    /** Every control point, in the order the scenario spawned them - which is also the tie-break. */
    private static List<Position> pointsOf(Simulation simulation) {
        ImmutableArray<Entity> found = simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class, Position.class).get());
        List<Position> positions = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            positions.add(found.get(i).getComponent(Position.class));
        }
        return positions;
    }

    private static Simulation world() {
        return Scenarios.world(
                Scenarios.all().stream()
                        .filter(scenario -> NAME.equals(scenario.name()))
                        .findFirst()
                        .orElseThrow(),
                SimConfig.defaults());
    }
}
