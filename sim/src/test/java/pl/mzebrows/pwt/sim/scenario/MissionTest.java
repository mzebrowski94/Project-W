package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.Wave;

/**
 * A mission is built whole from its seed and tier (G0065): the seed deals the order, lays the ground and chooses
 * where things stand; the tier moves only how many creatures there are. What the sweep holds every seed to is in
 * core's {@code MissionSweepTest}; these are the rules one build already shows.
 */
class MissionTest {

    private static final int SEEDS = 64;

    /** Deals are cheap: enough of them that a biome the deal can never place at a position is seen to be missing. */
    private static final int DEALS = 512;

    @Test
    void dealsItsBiomesNoneTwiceAndNeverTwoOfOneBaseSideBySide() {
        SimConfig shipped = SimConfig.defaults();
        for (long seed = 0; seed < SEEDS; seed++) {
            List<BiomeKind> order = Mission.deal(shipped.withSeed(seed));

            assertThat(order).as("seed %d", seed).hasSize(shipped.mission().biomes());
            assertThat(order).as("seed %d", seed).doesNotHaveDuplicates();
            for (int p = 1; p < order.size(); p++) {
                assertThat(base(shipped, order.get(p)))
                        .as("seed %d, positions %d and %d", seed, p - 1, p)
                        .isNotEqualTo(base(shipped, order.get(p - 1)));
            }
        }
    }

    /** A biome the deal can never place at some position is one that fails the build, not a quiet hole. */
    @Test
    void dealsEveryBiomeAtEveryPosition() {
        SimConfig shipped = SimConfig.defaults();
        Set<String> dealt = new HashSet<>();
        for (long seed = 0; seed < DEALS; seed++) {
            List<BiomeKind> order = Mission.deal(shipped.withSeed(seed));
            for (int p = 0; p < order.size(); p++) {
                dealt.add(order.get(p) + " at " + p);
            }
        }

        List<String> never = new ArrayList<>();
        for (BiomeKind kind : BiomeKind.values()) {
            for (int p = 0; p < shipped.mission().biomes(); p++) {
                if (!dealt.contains(kind + " at " + p)) {
                    never.add(kind + " at " + p);
                }
            }
        }
        assertThat(never).as("never dealt in %d seeds", DEALS).isEmpty();
    }

    /** The tier never moves the map (G0065): one seed lays the same ground, and the same points, on every tier. */
    @Test
    void laysTheSameGroundAndPointsOnEveryTier() {
        for (long seed = 0; seed < 4; seed++) {
            Mission medium = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
            for (TierKind tier : List.of(TierKind.EASY, TierKind.HARD)) {
                Mission other = Mission.build(SimConfig.defaults().withSeed(seed), tier);

                assertThat(other.order()).as("seed %d, %s", seed, tier).isEqualTo(medium.order());
                assertThat(terrain(other.laid().map()))
                        .as("seed %d, %s", seed, tier)
                        .isEqualTo(terrain(medium.laid().map()));
                assertThat(other.laid().houses())
                        .as("seed %d, %s", seed, tier)
                        .isEqualTo(medium.laid().houses());
                assertThat(points(other)).as("seed %d, %s", seed, tier).isEqualTo(points(medium));
            }
        }
    }

    @Test
    void putsMoreCreaturesDownOnAHarderTier() {
        for (long seed = 0; seed < 4; seed++) {
            int easy = hostile(Mission.build(SimConfig.defaults().withSeed(seed), TierKind.EASY));
            int medium = hostile(Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM));
            int hard = hostile(Mission.build(SimConfig.defaults().withSeed(seed), TierKind.HARD));

            assertThat(easy).as("seed %d", seed).isLessThan(medium);
            assertThat(medium).as("seed %d", seed).isLessThan(hard);
        }
    }

    @Test
    void standsAShutDoorInEveryDoorwayOfEveryHouse() {
        int laid = 0;
        for (long seed = 0; seed < 4; seed++) {
            Simulation simulation = Scenarios.world(
                    Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(seed));
            Mission mission = Mission.build(simulation.config(), TierKind.MEDIUM);
            simulation.step();

            int doorways = 0;
            for (House house : mission.laid().houses()) {
                for (TileRect doorway : house.doorways()) {
                    doorways++;
                    assertThat(simulation.solid().passableAtTile(doorway.col(), doorway.row()))
                            .as("seed %d, doorway at %d,%d", seed, doorway.col(), doorway.row())
                            .isFalse();
                }
            }
            assertThat(simulation
                            .engine()
                            .getEntitiesFor(Family.all(Door.class).get())
                            .size())
                    .as("seed %d", seed)
                    .isEqualTo(doorways);
            laid += doorways;
        }
        assertThat(laid).as("doorways across the seeds").isPositive();
    }

    @Test
    void buildsTheSameMissionTwiceFromTheSameSeed() {
        Mission once = Mission.build(SimConfig.defaults().withSeed(7L), TierKind.HARD);
        Mission twice = Mission.build(SimConfig.defaults().withSeed(7L), TierKind.HARD);

        assertThat(twice.order()).isEqualTo(once.order());
        assertThat(terrain(twice.laid().map())).isEqualTo(terrain(once.laid().map()));
        assertThat(twice.placed()).isEqualTo(once.placed());
    }

    @Test
    void putsBetweenItsFewestAndMostControlPointsInEveryBiome() {
        SimConfig shipped = SimConfig.defaults();
        for (long seed = 0; seed < 8; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                long points = mission.placed().stream()
                        .filter(placed -> placed instanceof Placed.Point && placed.biome() == position)
                        .count();
                var furniture = shipped.biomes().of(mission.order().get(p)).furniture();

                assertThat(points).as("seed %d, biome %d", seed, p).isBetween((long) furniture.pointsMin(), (long)
                        furniture.pointsMax());
            }
        }
    }

    /**
     * One bit flips the finale north-south (G0064): every tile of its ground and everything standing on it mirrored
     * about the anchor's row, and nothing moved east-west, so west stays the way in.
     */
    @Test
    void flipsTheFinaleNorthSouthAndNeverEastWest() {
        Mission plain = null;
        Mission flipped = null;
        for (long seed = 0; seed < SEEDS && (plain == null || flipped == null); seed++) {
            Mission mission = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
            if (mission.finale().flipped()) {
                flipped = mission;
            } else {
                plain = mission;
            }
        }
        assertThat(plain).as("an unflipped finale in %d seeds", SEEDS).isNotNull();
        assertThat(flipped).as("a flipped finale in %d seeds", SEEDS).isNotNull();

        assertThat(finaleTiles(flipped, true)).isEqualTo(finaleTiles(plain, false));
        assertThat(finaleThings(flipped, true)).isEqualTo(finaleThings(plain, false));
        assertThat(finaleThings(plain, false)).contains("Hold 0 0", "Turret -5 -5", "Pickup -3 0");
    }

    /** The tier moves how many come in a wave and nothing else of the finale: not its layout, timing or mouths. */
    @Test
    void endsOnTheSameFinaleOnEveryTierButForHowManyComeInAWave() {
        for (long seed = 0; seed < 4; seed++) {
            Mission easy = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.EASY);
            Mission medium = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
            Mission hard = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.HARD);

            for (Mission other : List.of(easy, hard)) {
                assertThat(other.finale().kind()).isEqualTo(medium.finale().kind());
                assertThat(other.finale().flipped()).isEqualTo(medium.finale().flipped());
                assertThat(finaleThings(other, false)).isEqualTo(finaleThings(medium, false));
                assertThat(other.finale().waves())
                        .extracting(Wave::afterSeconds, Wave::kind, Wave::where)
                        .isEqualTo(medium.finale().waves().stream()
                                .map(wave -> tuple(wave.afterSeconds(), wave.kind(), wave.where()))
                                .toList());
            }
            assertThat(sent(easy)).as("seed %d", seed).isLessThan(sent(medium));
            assertThat(sent(medium)).as("seed %d", seed).isLessThan(sent(hard));
        }
    }

    /**
     * A mission is won by holding its finale's post alone (G0064): one point that holds afterwards, its watch, its
     * guns and mines on the field, and the whole schedule held back until that ground is taken.
     */
    @Test
    void holdsTheFinalesWavesBackAndAsksOnlyThatItsPostBeHeld() {
        Simulation simulation = Scenarios.world(
                Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(5L));
        Mission mission = Mission.build(simulation.config(), TierKind.MEDIUM);
        simulation.step();

        List<ControlPoint> held = new ArrayList<>();
        for (var entity : simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class).get())) {
            ControlPoint point = entity.getComponent(ControlPoint.class);
            if (point.holdsAfterwards) {
                held.add(point);
            }
        }
        assertThat(held).hasSize(1);
        assertThat(held.getFirst().watchRadius).isEqualTo(118f);
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Turret.class).get())
                        .size())
                .isEqualTo(4);
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Mine.class).get())
                        .size())
                .isEqualTo(20);
        assertThat(simulation.waves().armed()).isFalse();
        assertThat(simulation.waves().total())
                .isEqualTo(mission.finale().waves().size());
        assertThat(simulation.level().requires(Objective.HOLD_POINT)).isTrue();
        assertThat(simulation.level().requires(Objective.CAPTURE_POINTS)).isFalse();
        assertThat(simulation.level().requires(Objective.CLEAR_ENEMIES)).isFalse();
    }

    /** A session names a mission {@code mission <tier>} and carries its seed as its own (G0065). */
    @Test
    void namesAMissionByItsTierForASession() {
        for (TierKind tier : TierKind.values()) {
            Scenario named = Scenarios.named(Scenarios.mission(tier).name());

            assertThat(named.name()).isEqualTo("mission " + tier.name().toLowerCase(java.util.Locale.ROOT));
            assertThat(terrain(Scenarios.world(named, SimConfig.defaults().withSeed(3L))
                            .map()))
                    .isEqualTo(terrain(Mission.build(SimConfig.defaults().withSeed(3L), tier)
                            .laid()
                            .map()));
        }
    }

    private static Terrain base(SimConfig config, BiomeKind kind) {
        return config.biomes().of(kind).ground().base();
    }

    private static List<Placed> points(Mission mission) {
        return mission.placed().stream()
                .filter(placed -> placed instanceof Placed.Point)
                .toList();
    }

    private static int hostile(Mission mission) {
        return (int) mission.placed().stream()
                .filter(placed -> placed instanceof Placed.Loose || placed instanceof Placed.Nest)
                .count();
    }

    private static int sent(Mission mission) {
        return mission.finale().waves().stream().mapToInt(Wave::count).sum();
    }

    /** The finale's ground, tile by tile from its anchor, its rows read south to north - or north to south. */
    private static List<String> finaleTiles(Mission mission, boolean mirrored) {
        TileRect finale = mission.laid().biomes().getLast();
        TileRect anchor = Strip.anchor(finale);
        // The rows the anchor's mirror keeps inside the finale: one fewer north of it than south.
        int reach = Math.min(anchor.row() - finale.row(), finale.endRow() - 1 - anchor.row());
        List<String> tiles = new ArrayList<>();
        for (int dy = -reach; dy <= reach; dy++) {
            for (int col = finale.col(); col < finale.endCol(); col++) {
                int row = anchor.row() + (mirrored ? -dy : dy);
                tiles.add(col - anchor.col() + " " + dy + " "
                        + mission.laid().map().terrainAtTile(col, row));
            }
        }
        return tiles;
    }

    /** Everything the finale stands, as its kind and its tile offset from the anchor, sorted. */
    private static List<String> finaleThings(Mission mission, boolean mirrored) {
        TileMap map = mission.laid().map();
        TileRect anchor = Strip.anchor(mission.laid().biomes().getLast());
        return mission.placed().stream()
                .filter(placed -> placed.biome() == mission.order().size())
                .map(placed -> {
                    int dy = map.rowAt(placed.y()) - anchor.row();
                    return placed.getClass().getSimpleName() + " " + (map.colAt(placed.x()) - anchor.col()) + " "
                            + (mirrored ? -dy : dy);
                })
                .sorted()
                .toList();
    }

    private static Terrain[] terrain(TileMap map) {
        Terrain[] tiles = new Terrain[map.cols() * map.rows()];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = map.terrainAtTile(i % map.cols(), i / map.cols());
        }
        return tiles;
    }
}
