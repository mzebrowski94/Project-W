package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.WorkedSite;

/** Mainframe (G0111): a mainframe inside a house behind a shut door, and the squad outside. */
class MainframeScenarioTest {

    @Test
    void standsAMainframeInsideAWalledHouseBehindAShutDoor() {
        Scenario scenario = Scenarios.looselyNamed("mainframe");
        assertThat(scenario).isNotNull();

        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults());

        var mainframes =
                simulation.engine().getEntitiesFor(Family.all(WorkedSite.class).get());
        assertThat(mainframes.size()).isOne();
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Door.class).get())
                        .size())
                .isOne();
        Entity mainframe = mainframes.first();
        Position at = mainframe.getComponent(Position.class);
        TileMap map = simulation.map();
        int col = map.colAt(at.x);
        int row = map.rowAt(at.y);
        assertThat(map.terrainAtTile(col, row + 1))
                .as("against the wall north of it")
                .isEqualTo(Terrain.WALL);
        assertThat(map.terrainAtTile(col, row)).isNotEqualTo(Terrain.WALL);
    }
}
