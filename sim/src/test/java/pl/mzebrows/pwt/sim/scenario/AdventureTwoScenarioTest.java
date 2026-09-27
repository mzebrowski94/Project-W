package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The claims the second march makes that a glance would not catch. {@code ScenariosTest} already says
 * nothing is in rock and everything is reachable; what is only true of this one is <strong>where the
 * ants are</strong> - in every biome but the first - and that it keeps the first march's shape.
 */
class AdventureTwoScenarioTest {

    private static final String NAME = "Adventure 2";

    /** Where each biome ends, in tile columns. */
    private static final int[] BIOME_END = {30, 60, 90, 120, 150, 180, 216};

    /** Ants standing, or holes that send them out, in every biome but the first - and none in it. */
    @Test
    void putsAntsInEveryBiomeButTheFirst() {
        Simulation world = world();
        TileMap map = world.map();

        int[] perBiome = new int[BIOME_END.length];
        for (Entity entity : world.engine()
                .getEntitiesFor(Family.all(Strain.class, Position.class).get())) {
            if (entity.getComponent(Strain.class).kind == UnitKind.ANT) {
                perBiome[biomeOf(map.colAt(entity.getComponent(Position.class).x))]++;
            }
        }
        for (Entity entity : world.engine()
                .getEntitiesFor(Family.all(Nest.class, Position.class).get())) {
            if (entity.getComponent(Nest.class).kind == UnitKind.ANT) {
                perBiome[biomeOf(map.colAt(entity.getComponent(Position.class).x))]++;
            }
        }

        assertThat(perBiome[0]).isZero();
        for (int biome = 1; biome < BIOME_END.length; biome++) {
            assertThat(perBiome[biome]).as("ants in biome %s", biome + 1).isPositive();
        }
    }

    @Test
    void laysOutTwoPointsInEveryBiomeItMarchesThroughAndOneToHold() {
        Simulation world = world();
        TileMap map = world.map();

        int[] perBiome = new int[BIOME_END.length];
        int held = 0;
        for (Entity entity : world.engine()
                .getEntitiesFor(Family.all(ControlPoint.class, Position.class).get())) {
            perBiome[biomeOf(map.colAt(entity.getComponent(Position.class).x))]++;
            if (entity.getComponent(ControlPoint.class).holdsAfterwards) {
                held++;
            }
        }

        assertThat(perBiome).containsExactly(2, 2, 2, 2, 2, 2, 1);
        assertThat(held).isEqualTo(1);
    }

    /** As long as the first march, which is the brief. */
    @Test
    void isAsLongAsTheFirstMarch() {
        assertThat(world().map().cols())
                .isEqualTo(Scenarios.world(Scenarios.named("Adventure"), SimConfig.defaults())
                        .map()
                        .cols());
    }

    private static int biomeOf(int col) {
        for (int biome = 0; biome < BIOME_END.length; biome++) {
            if (col < BIOME_END[biome]) {
                return biome;
            }
        }
        return BIOME_END.length - 1;
    }

    private static Simulation world() {
        return Scenarios.world(Scenarios.named(NAME), SimConfig.defaults());
    }
}
