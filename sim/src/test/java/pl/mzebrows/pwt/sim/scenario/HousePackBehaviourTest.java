package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A house pack's jaguars (G0112): they wander inside their house until they notice somebody, and the shut doors hold
 * them in; then, with nobody they can reach, they leap the door down to get out (G0075, G0069).
 */
class HousePackBehaviourTest {

    /** The house: walls from here, nine tiles by eight, so its inside is seven by six. */
    private static final TileRect WALLS = new TileRect(6, 6, 9, 8);

    /** Its one way in, three tiles of its south wall. */
    private static final TileRect DOORWAY = new TileRect(9, 6, 3, 1);

    private static final House HOUSE = new House(WALLS, List.of(DOORWAY));

    /** Tiles across the ground the house stands on: wide enough for a corner past a jaguar's notice. */
    private static final int WIDE = 64;

    /** Further than a jaguar notices from the middle of the house, at the ground's far corner. */
    private static final float FAR_CORNER = (WIDE - 1) * TILE;

    /** South of the door, well inside a jaguar's detection range of the house. */
    private static final float OUTSIDE_Y = 12f;

    @Test
    void staysInsideItsHouseForAMinuteWhileItHasNoticedNobody() {
        Simulation simulation = housed();
        simulation.spawnCommander(FAR_CORNER, FAR_CORNER);
        List<Entity> jaguars = pack(simulation);

        boolean outside = false;
        for (int tick = 0; tick < ticksIn(60f) && !outside; tick++) {
            simulation.step();
            for (Entity jaguar : jaguars) {
                outside |= !inside(simulation, jaguar);
            }
        }

        assertThat(outside).isFalse();
        assertThat(count(simulation, Door.class)).isOne();
    }

    @Test
    void walksAboutTheHouseRatherThanStandingWhereItWasPut() {
        Simulation simulation = housed();
        simulation.spawnCommander(FAR_CORNER, FAR_CORNER);
        Entity jaguar = pack(simulation).getFirst();
        float x = jaguar.getComponent(Position.class).x;
        float y = jaguar.getComponent(Position.class).y;

        float furthest = 0f;
        for (int tick = 0; tick < ticksIn(20f); tick++) {
            simulation.step();
            float dx = jaguar.getComponent(Position.class).x - x;
            float dy = jaguar.getComponent(Position.class).y - y;
            furthest = Math.max(furthest, (float) Math.sqrt(dx * dx + dy * dy));
        }

        assertThat(furthest).isGreaterThan(simulation.map().tileSize());
    }

    @Test
    void leapsTheDoorDownAndGetsOutToAManOutsideWithinItsDetection() {
        Simulation simulation = housed();
        simulation.spawnCommander(DOORWAY.col() * simulation.map().tileSize() + 7f, OUTSIDE_Y);
        godMode(simulation);
        List<Entity> jaguars = pack(simulation);

        boolean out = false;
        for (int tick = 0; tick < ticksIn(40f) && !out; tick++) {
            simulation.step();
            for (Entity jaguar : jaguars) {
                out |= jaguar.getComponent(Position.class).y
                        < WALLS.row() * simulation.map().tileSize();
            }
        }

        assertThat(count(simulation, Door.class)).as("the door broken").isZero();
        assertThat(out).as("a jaguar out of the house").isTrue();
    }

    /** The fixture's world, the jaguar as it ships, on a map with the house in it, its door shut, everything put down unaware as a mission does. */
    private static Simulation housed() {
        SimConfig config = config().withUnit(
                        UnitKind.JAGUAR, jaguar -> SimConfig.defaults().units().of(UnitKind.JAGUAR));
        for (UnitKind kind : UnitKind.values()) {
            config = config.withUnit(
                    kind, strain -> strain.withPerception(strain.perception().withAlwaysAggressive(false)));
        }
        TileMap map = TileMap.builder(WIDE, WIDE, TILE)
                .fill(Terrain.PLAIN)
                .rect(WALLS.col(), WALLS.row(), WALLS.width(), WALLS.height(), Terrain.WALL)
                .rect(WALLS.col() + 1, WALLS.row() + 1, WALLS.width() - 2, WALLS.height() - 2, Terrain.PLAIN)
                .rect(DOORWAY.col(), DOORWAY.row(), DOORWAY.width(), DOORWAY.height(), Terrain.PLAIN)
                .build();
        Simulation simulation = new Simulation(config, map);
        simulation.spawnDoor(DOORWAY);
        return simulation;
    }

    /** Three jaguars of a house pack, about the inside. */
    private static List<Entity> pack(Simulation simulation) {
        TileMap map = simulation.map();
        TileRect inside = HOUSE.inside();
        List<Entity> jaguars = new ArrayList<>();
        int[][] at = {
            {inside.col() + 1, inside.row() + 1},
            {inside.col() + 3, inside.endRow() - 2},
            {inside.endCol() - 2, inside.row() + 2}
        };
        for (int[] tile : at) {
            jaguars.add(HousePacks.spawn(
                    simulation,
                    HousePacks.in(HOUSE, map, UnitKind.JAGUAR, map.tileCentreX(tile[0]), map.tileCentreY(tile[1]), 0)));
        }
        return jaguars;
    }

    private static boolean inside(Simulation simulation, Entity jaguar) {
        float tile = simulation.map().tileSize();
        Position at = jaguar.getComponent(Position.class);
        TileRect inside = HOUSE.inside();
        return at.x > inside.col() * tile
                && at.x < inside.endCol() * tile
                && at.y > inside.row() * tile
                && at.y < inside.endRow() * tile;
    }
}
