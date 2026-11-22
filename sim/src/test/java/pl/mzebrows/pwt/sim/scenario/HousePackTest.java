package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * House packs (G0112): each house of a biome that lays them - never a gatehouse - holds a pack of jaguars on
 * {@code housePackChance}, laid inside it.
 */
class HousePackTest {

    private static final int SEEDS = 64;

    @Test
    void putsAPackOfJaguarsInAboutHalfTheHouses() {
        int houses = 0;
        int held = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = mission(seed);
            // Neither the first biome, whose houses near the start hold none, nor the last, whose may reach the watch.
            for (int p = 1; p < mission.order().size() - 1; p++) {
                for (House house : housesOf(mission, p)) {
                    houses++;
                    held += packIn(mission, house).isEmpty() ? 0 : 1;
                }
            }
        }

        assertThat(houses).as("houses seen").isGreaterThan(SEEDS);
        assertThat((float) held / houses).isBetween(0.4f, 0.6f);
    }

    @Test
    void putsOneToThreeJaguarsInAHouseOnMediumAndOnlyJaguars() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = mission(seed);
            for (int p = 1; p < mission.order().size() - 1; p++) {
                for (House house : housesOf(mission, p)) {
                    List<Placed.Housed> pack = packIn(mission, house);
                    if (pack.isEmpty()) {
                        continue;
                    }
                    // Scaled by the ramp as a loose line is (G0063), so a later biome may hold a few more.
                    assertThat(pack.size()).as("seed %d biome %d", seed, p).isBetween(1, 5);
                    assertThat(pack).allMatch(housed -> housed.strain() == UnitKind.JAGUAR);
                }
            }
        }
    }

    @Test
    void laysEveryHousePackInsideAHouseOfItsOwnBiomeAndNeverAGatehouse() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = mission(seed);
            TileMap map = mission.laid().map();
            for (Placed placed : mission.placed()) {
                if (!(placed instanceof Placed.Housed housed)) {
                    continue;
                }
                House house = houseAround(mission, housed);
                assertThat(house).as("seed %d: %s in a house", seed, housed).isNotNull();
                assertThat(housesOf(mission, housed.biome()))
                        .as("seed %d: %s in a house of its biome, not a gatehouse", seed, housed)
                        .contains(house);
                float radius = SimConfig.defaults().unit(housed.strain()).radius();
                TileRect inside = house.inside();
                float tile = map.tileSize();
                assertThat(housed.x() - radius).isGreaterThanOrEqualTo(inside.col() * tile);
                assertThat(housed.x() + radius).isLessThanOrEqualTo(inside.endCol() * tile);
                assertThat(housed.y() - radius).isGreaterThanOrEqualTo(inside.row() * tile);
                assertThat(housed.y() + radius).isLessThanOrEqualTo(inside.endRow() * tile);
            }
        }
    }

    @Test
    void neverPutsAnythingElseHostileInAHouse() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = mission(seed);
            for (Placed placed : mission.placed()) {
                if (placed instanceof Placed.Loose || placed instanceof Placed.Nest) {
                    assertThat(houseAround(mission, placed))
                            .as("seed %d: %s", seed, placed)
                            .isNull();
                }
            }
        }
    }

    private static Mission mission(long seed) {
        SimConfig config = Scenarios.mission(TierKind.MEDIUM)
                .configure(SimConfig.defaults().withSeed(seed));
        return Mission.build(config, TierKind.MEDIUM);
    }

    /** The houses standing wholly inside one biome: its own, never a gatehouse in a ridge. */
    private static List<House> housesOf(Mission mission, int position) {
        TileRect rect = mission.laid().biomes().get(position);
        List<House> houses = new ArrayList<>();
        for (House house : mission.laid().houses()) {
            TileRect walls = house.walls();
            if (rect.contains(walls.col(), walls.row()) && rect.contains(walls.endCol() - 1, walls.endRow() - 1)) {
                houses.add(house);
            }
        }
        return houses;
    }

    private static List<Placed.Housed> packIn(Mission mission, House house) {
        List<Placed.Housed> pack = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Housed housed && house.equals(houseAround(mission, housed))) {
                pack.add(housed);
            }
        }
        return pack;
    }

    private static House houseAround(Mission mission, Placed placed) {
        TileMap map = mission.laid().map();
        int col = map.colAt(placed.x());
        int row = map.rowAt(placed.y());
        for (House house : mission.laid().houses()) {
            if (house.inside().contains(col, row)) {
                return house;
            }
        }
        return null;
    }
}
