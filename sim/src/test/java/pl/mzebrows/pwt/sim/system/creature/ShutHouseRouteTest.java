package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.LeapDef;

/**
 * A creature hunting a man in the open does not jam at a shut door of a house that stands between them (playtest
 * ticket 02, G0075): the way through the house is shut while there is a way round it, and it goes round.
 */
class ShutHouseRouteTest {

    /** The house's walls: most of the arena's width, open ground either side of it. */
    private static final TileRect WALLS = new TileRect(3, 8, 14, 5);

    private static final TileRect NEAR_DOOR = new TileRect(9, 8, 3, 1);

    private static final TileRect FAR_DOOR = new TileRect(9, 12, 3, 1);

    private static final float MIDDLE_X = (NEAR_DOOR.col() + NEAR_DOOR.width() * 0.5f) * TILE;

    @Test
    void goesRoundAShutHouseToAManInTheOpen() {
        Simulation simulation = on(harmless(), houseAcrossTheArena());
        simulation.spawnDoor(NEAR_DOOR);
        simulation.spawnCommander(30f, 85f);
        godMode(simulation);
        Entity creature = simulation.spawnEnemy(MIDDLE_X, 20f);

        steps(simulation, ticksIn(20f));

        assertThat(creature.getComponent(Position.class).y)
                .as("past the house, round it")
                .isGreaterThan(WALLS.endRow() * TILE);
    }

    @Test
    void goesRoundWhenBothDoorwaysLineUpWithTheMan() {
        Simulation simulation = on(harmless(), houseAcrossTheArena());
        simulation.spawnDoor(NEAR_DOOR);
        simulation.spawnCommander(MIDDLE_X, 85f);
        godMode(simulation);
        Entity creature = simulation.spawnEnemy(MIDDLE_X, 20f);

        steps(simulation, ticksIn(20f));

        assertThat(creature.getComponent(Position.class).y)
                .as("the line through both doorways is no way while a door shuts it")
                .isGreaterThan(WALLS.endRow() * TILE);
    }

    @Test
    void goesThroughTheHouseOnceItsDoorIsOpen() {
        Simulation simulation = on(harmless(), houseAcrossTheArena());
        Entity door = simulation.spawnDoor(NEAR_DOOR);
        simulation.spawnCommander(30f, 85f);
        godMode(simulation);
        Entity creature = simulation.spawnEnemy(MIDDLE_X, 20f);
        steps(simulation, 1);

        door.getComponent(Door.class).open = true;
        Position position = creature.getComponent(Position.class);
        float furthestAside = 0f;
        for (int tick = 0; tick < ticksIn(20f) && position.y < WALLS.endRow() * TILE; tick++) {
            simulation.step();
            furthestAside = Math.max(furthestAside, Math.abs(position.x - MIDDLE_X));
        }

        assertThat(position.y).as("past the house").isGreaterThanOrEqualTo(WALLS.endRow() * TILE);
        assertThat(furthestAside).as("through its doorways, never round").isLessThan(NEAR_DOOR.width() * TILE);
    }

    private static SimConfig harmless() {
        return everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(LeapDef.off()));
    }

    /** A walled house across the middle with a doorway in each long wall, both kept open in the terrain. */
    private static TileMap houseAcrossTheArena() {
        TileMap.Builder builder = ground().rect(WALLS.col(), WALLS.row(), WALLS.width(), WALLS.height(), Terrain.WALL)
                .rect(WALLS.col() + 1, WALLS.row() + 1, WALLS.width() - 2, WALLS.height() - 2, Terrain.PLAIN);
        return builder.rect(NEAR_DOOR.col(), NEAR_DOOR.row(), NEAR_DOOR.width(), 1, Terrain.PLAIN)
                .rect(FAR_DOOR.col(), FAR_DOOR.row(), FAR_DOOR.width(), 1, Terrain.PLAIN)
                .build();
    }
}
