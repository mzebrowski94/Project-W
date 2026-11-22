package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.UnitKind;

/** Jaguar House (G0112): three jaguars in a shut house, the squad outside. */
class JaguarHouseScenarioTest {

    @Test
    void shutsThreeJaguarsInAHouseBehindAShutDoor() {
        Scenario scenario = Scenarios.looselyNamed("jaguar-house");
        assertThat(scenario).isNotNull();

        Simulation simulation = Scenarios.world(scenario, SimConfig.defaults());

        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Door.class).get())
                        .size())
                .isOne();
        ImmutableArray<Entity> strains =
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get());
        int jaguars = 0;
        for (Entity unit : strains) {
            if (unit.getComponent(Strain.class).kind != UnitKind.JAGUAR) {
                continue;
            }
            jaguars++;
            Position at = unit.getComponent(Position.class);
            assertThat(simulation
                            .map()
                            .terrainAtTile(
                                    simulation.map().colAt(at.x),
                                    simulation.map().rowAt(at.y)))
                    .isNotEqualTo(Terrain.WALL);
        }
        assertThat(jaguars).isEqualTo(3);
    }
}
