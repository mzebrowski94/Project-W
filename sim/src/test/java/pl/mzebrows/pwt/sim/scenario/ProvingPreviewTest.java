package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.UnitKind;

/** The proving ground page's preview: one of the chosen strain going about its business on sand. */
class ProvingPreviewTest {

    /** Long enough for anything that walks to have set off across its patch. */
    private static final int TICKS = 600;

    @ParameterizedTest
    @EnumSource(UnitKind.class)
    void holdsOneOfTheStrainOnSandAndNobodyElse(UnitKind strain) {
        Simulation simulation = Scenarios.world(Scenarios.preview(strain), SimConfig.defaults());

        ImmutableArray<Entity> units =
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get());
        assertThat(units).hasSize(1);
        assertThat(units.first().getComponent(Strain.class).kind).isEqualTo(strain);
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Commander.class).get()))
                .isEmpty();
        Position at = units.first().getComponent(Position.class);
        assertThat(simulation.map().terrainAt(at.x, at.y)).isEqualTo(Terrain.SAND);
    }

    @ParameterizedTest
    @EnumSource(UnitKind.class)
    void keepsAnythingThatWalksMoving(UnitKind strain) {
        assumeFalse(SimConfig.defaults().units().of(strain).rooted(), "rooted: it never walks");
        Simulation simulation = Scenarios.world(Scenarios.preview(strain), SimConfig.defaults());
        Position at = simulation
                .engine()
                .getEntitiesFor(Family.all(Strain.class).get())
                .first()
                .getComponent(Position.class);
        float startX = at.x;
        float startY = at.y;

        boolean moved = false;
        for (int tick = 0; tick < TICKS; tick++) {
            simulation.step();
            moved |= at.x != startX || at.y != startY;
        }

        assertThat(moved).as("%s ambled", strain).isTrue();
    }
}
