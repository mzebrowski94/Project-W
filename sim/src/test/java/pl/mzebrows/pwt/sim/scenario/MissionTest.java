package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Clearing;
import pl.mzebrows.pwt.map.House;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.FinaleDef;
import pl.mzebrows.pwt.sim.FinaleRules;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WaveLine;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Burrow;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Emplacement;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.WorkedSite;
import pl.mzebrows.pwt.sim.def.Arrival;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.def.FinaleKind;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.Wave;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * A mission is built whole from its seed and tier (G0065): the seed deals the order, lays the ground and chooses
 * where things stand; the tier moves only how many creatures there are. What the sweep holds every seed to is in
 * core's {@code MissionSweepTest}; these are the rules one build already shows.
 */
class MissionTest {

    private static final int SEEDS = 64;

    /** Deals are cheap: enough of them that a biome the deal can never place at a position is seen to be missing. */
    private static final int DEALS = 512;

    /** Enough missions for some hundreds of biomes with a house, so the share is a few points either side. */
    private static final int MAINFRAME_SEEDS = 120;

    private static final int SHIPPED_MAINFRAME_CHANCE = 50;

    private static final float MAINFRAME_TOLERANCE = 0.1f;

    /**
     * G0111: a biome that lays a house of its own stands a mainframe in one on about the shipped chance, in place of
     * one of its points - and a biome with no house of its own never does.
     */
    @Test
    void standsAMainframeInAboutHalfTheBiomesThatLayAHouse() {
        int housed = 0;
        int withAMainframe = 0;
        for (long seed = 0; seed < MAINFRAME_SEEDS; seed++) {
            Mission mission = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                boolean house = mission.laid().houses().stream()
                        .anyMatch(h -> ownHouse(mission.laid().biomes().get(position), h));
                long mainframes = mission.placed().stream()
                        .filter(placed -> placed.biome() == position
                                && placed instanceof Placed.Worked worked
                                && worked.kind() == WorkedSiteKind.MAINFRAME)
                        .count();
                assertThat(mainframes).as("seed %d biome %d", seed, p).isLessThanOrEqualTo(house ? 1L : 0L);
                housed += house ? 1 : 0;
                withAMainframe += (int) mainframes;
            }
        }

        assertThat(withAMainframe / (float) housed)
                .isCloseTo(SHIPPED_MAINFRAME_CHANCE / 100f, Offset.offset(MAINFRAME_TOLERANCE));
    }

    /**
     * G0113: every fen stands its fewest to most spires and no other biome stands one; each was dealt its cycle as it
     * went down, the same on every build of one seed.
     */
    @Test
    void standsItsSpiresInEveryFenAndNowhereElseEachWithTheCycleItsSeedDeals() {
        SimConfig shipped = SimConfig.defaults();
        var spires = shipped.biomes().of(BiomeKind.FEN).furniture().spires();
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            Mission again = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                long standing = mission.placed().stream()
                        .filter(placed -> placed.biome() == position && placed instanceof Placed.Emplacement)
                        .count();
                if (mission.order().get(p) == BiomeKind.FEN) {
                    assertThat(standing).as("seed %d", seed).isBetween((long) spires.min(), (long) spires.max());
                } else {
                    assertThat(standing).as("seed %d biome %d", seed, p).isZero();
                }
            }
            assertThat(emplacements(again)).as("seed %d", seed).isEqualTo(emplacements(mission));
        }
    }

    @Test
    void spawnsAFensSpiresAsEmplacements() {
        long seed = 0;
        while (!Mission.deal(SimConfig.defaults().withSeed(seed)).contains(BiomeKind.FEN)) {
            seed++;
        }

        Simulation world = Scenarios.world(
                Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(seed));

        long spires = 0;
        for (Entity emplacement :
                world.engine().getEntitiesFor(Family.all(Emplacement.class).get())) {
            spires += emplacement.getComponent(Emplacement.class).kind == EmplacementKind.SPIRE ? 1 : 0;
        }
        assertThat(spires).isBetween(3L, 5L);
    }

    /**
     * G0114: every desert lays its fewest to most relics among its sites, each in a point's place, and no other biome
     * lays one.
     */
    @Test
    void laysOneOrTwoRelicsAmongTheSitesOfEveryDesertAndNoneElsewhere() {
        SimConfig shipped = SimConfig.defaults();
        var relics = shipped.biomes().of(BiomeKind.DESERT).furniture().relics();
        int deserts = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                long standing = mission.placed().stream()
                        .filter(placed -> placed.biome() == position
                                && placed instanceof Placed.Worked worked
                                && worked.kind() == WorkedSiteKind.RELIC)
                        .count();
                if (mission.order().get(p) == BiomeKind.DESERT) {
                    deserts++;
                    assertThat(standing).as("seed %d", seed).isBetween((long) relics.min(), (long) relics.max());
                } else {
                    assertThat(standing).as("seed %d biome %d", seed, p).isZero();
                }
            }
        }
        assertThat(deserts).as("the seeds deal a desert").isPositive();
    }

    @Test
    void spawnsADesertsRelicWithItsFourWardensSilent() {
        long seed = 0;
        while (!Mission.deal(SimConfig.defaults().withSeed(seed)).contains(BiomeKind.DESERT)) {
            seed++;
        }
        Mission mission = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
        long relics = mission.placed().stream()
                .filter(placed -> placed instanceof Placed.Worked worked && worked.kind() == WorkedSiteKind.RELIC)
                .count();

        Simulation world = Scenarios.world(
                Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(seed));

        long wardens = 0;
        for (Entity emplacement :
                world.engine().getEntitiesFor(Family.all(Emplacement.class).get())) {
            Emplacement warden = emplacement.getComponent(Emplacement.class);
            if (warden.kind == EmplacementKind.WARDEN) {
                wardens++;
                assertThat(warden.awake).isFalse();
            }
        }
        assertThat(relics).isPositive();
        assertThat(wardens).isEqualTo(relics * 4);
    }

    @Test
    void spawnsAMissionsMainframeAsAWorkedSite() {
        long seed = 0;
        while (Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM).placed().stream()
                .noneMatch(placed -> placed instanceof Placed.Worked)) {
            seed++;
        }

        Simulation world = Scenarios.world(
                Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(seed));

        assertThat(world.engine()
                        .getEntitiesFor(Family.all(WorkedSite.class).get())
                        .size())
                .isPositive();
    }

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

    /** Past the pool the bag is dealt again: no biome twice before every one has come, none twice side by side (G0102). */
    @Test
    void dealsFromABagWhenTheLengthRunsPastThePool() {
        SimConfig shipped = SimConfig.defaults();
        int pool = BiomeKind.values().length;
        int length = shipped.mission().biomesMax();
        SimConfig config = shipped.withMission(shipped.mission().withBiomes(length));
        for (long seed = 0; seed < SEEDS; seed++) {
            List<BiomeKind> order = Mission.deal(config.withSeed(seed));

            assertThat(order).as("seed %d", seed).hasSize(length);
            for (int from = 0; from < length; from += pool) {
                List<BiomeKind> bag = order.subList(from, Math.min(from + pool, length));
                assertThat(bag).as("seed %d bag from %d", seed, from).doesNotHaveDuplicates();
            }
            for (int p = 1; p < length; p++) {
                assertThat(base(shipped, order.get(p)))
                        .as("seed %d, positions %d and %d", seed, p - 1, p)
                        .isNotEqualTo(base(shipped, order.get(p - 1)));
            }
        }
    }

    /** A length chosen is a strip of that many biomes, and a session names it so (G0102). */
    @Test
    void laysAsManyBiomesAsItsLengthAndNamesItForASession() {
        for (int length : new int[] {3, 9, 15}) {
            Scenario named =
                    Scenarios.named(Scenarios.mission(TierKind.HARD, length).name());

            assertThat(named.name()).isEqualTo("mission hard " + length);
            Mission mission = Mission.build(named.configure(SimConfig.defaults().withSeed(4L)), TierKind.HARD);
            assertThat(mission.order()).hasSize(length);
            assertThat(mission.laid().biomes()).hasSize(length + 1);
            assertThat(mission.shortfalls()).isEmpty();
        }
        assertThatThrownBy(() -> Scenarios.mission(TierKind.HARD, 2).configure(SimConfig.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
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

    /** A finale the draw never ends on, or never flips, is one that fails the build, not a quiet hole (G0064). */
    @Test
    void endsOnEveryFinaleFlippedAndNot() {
        Set<String> drawn = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            Mission.Finale finale = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM)
                    .finale();
            drawn.add(finale.kind() + " " + finale.flipped());
        }

        for (FinaleKind kind : FinaleKind.values()) {
            assertThat(drawn).as("in %d seeds", SEEDS).contains(kind + " false", kind + " true");
        }
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
                    .as("seed %d, a door in every doorway and ice across every iced way", seed)
                    .isEqualTo(doorways + mission.laid().ice().size());
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
    void putsBetweenItsFewestAndMostSitesInEveryBiome() {
        SimConfig shipped = SimConfig.defaults();
        for (long seed = 0; seed < 8; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                long points = mission.placed().stream()
                        .filter(placed -> (placed instanceof Placed.Point || placed instanceof Placed.Worked)
                                && placed.biome() == position)
                        .count();
                var furniture = shipped.biomes().of(mission.order().get(p)).furniture();

                assertThat(points).as("seed %d, biome %d", seed, p).isBetween((long) furniture.sitesMin(), (long)
                        furniture.sitesMax());
            }
        }
    }

    /**
     * A biome's turrets number between its fewest and most, each within {@code pointReach} of one of its points: a
     * gun where the fight is, and somewhere the level tells you to stand (G0082). Only the plain asks for any.
     */
    @Test
    void putsBetweenItsFewestAndMostTurretsInEveryBiomeEachByAPoint() {
        SimConfig shipped = SimConfig.defaults();
        float reach = shipped.mission().placement().pointReach();
        int seen = 0;
        for (long seed = 0; seed < 16; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                List<Placed> turrets = mission.placed().stream()
                        .filter(placed -> placed instanceof Placed.Turret && placed.biome() == position)
                        .toList();
                var furniture = shipped.biomes().of(mission.order().get(p)).furniture();

                assertThat(turrets.size())
                        .as("seed %d, biome %d", seed, p)
                        .isBetween(furniture.turretsMin(), furniture.turretsMax());
                for (Placed turret : turrets) {
                    assertThat(mission.placed().stream()
                                    .filter(placed -> placed instanceof Placed.Point && placed.biome() == position)
                                    .anyMatch(point ->
                                            Math.hypot(point.x() - turret.x(), point.y() - turret.y()) <= reach))
                            .as("seed %d, %s by a point", seed, turret)
                            .isTrue();
                }
                seen += turrets.size();
            }
        }
        assertThat(seen).as("turrets across the seeds").isPositive();
    }

    /**
     * A biome's geysers number between its fewest and most, each as wide as a geyser may be and spawned as one: ground
     * that erupts in fire on a cycle of its own (G0089). Only the cut asks for any.
     */
    @Test
    void putsBetweenItsFewestAndMostGeysersInEveryBiomeEachInItsRange() {
        SimConfig shipped = SimConfig.defaults();
        var geyser = shipped.geyser();
        int seen = 0;
        for (long seed = 0; seed < 16; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                int position = p;
                List<Placed.Geyser> geysers = mission.placed().stream()
                        .filter(placed -> placed instanceof Placed.Geyser && placed.biome() == position)
                        .map(Placed.Geyser.class::cast)
                        .toList();
                var furniture = shipped.biomes().of(mission.order().get(p)).furniture();

                assertThat(geysers.size())
                        .as("seed %d, biome %d", seed, p)
                        .isBetween(furniture.geysersMin(), furniture.geysersMax());
                for (Placed.Geyser one : geysers) {
                    assertThat(one.radius()).isBetween(geyser.radiusMin(), geyser.radiusMax());
                }
                seen += geysers.size();
            }
        }
        assertThat(seen).as("geysers across the seeds").isPositive();
    }

    /**
     * Ice across every way the strip iced (ADR 0025): each a door nobody opens, its tiles solid ground from the first tick,
     * the ground under it still open on the map - and none on a way in.
     */
    @Test
    void standsIceAcrossEveryWayTheStripIcedAndNoneOnAWayIn() {
        int iced = 0;
        for (long seed = 0; seed < 64 && iced == 0; seed++) {
            SimConfig config = SimConfig.defaults().withSeed(seed);
            if (!Mission.deal(config).contains(BiomeKind.RIFT)) {
                continue;
            }
            Simulation simulation = Scenarios.world(Scenarios.mission(TierKind.MEDIUM), config);
            Mission mission = Mission.build(simulation.config(), TierKind.MEDIUM);
            simulation.step();

            for (TileRect run : mission.laid().ice()) {
                for (int row = run.row(); row < run.endRow(); row++) {
                    for (int col = run.col(); col < run.endCol(); col++) {
                        assertThat(simulation.solid().passableAtTile(col, row))
                                .as("seed %d, ice at %d,%d", seed, col, row)
                                .isFalse();
                        assertThat(mission.laid().map().passableAtTile(col, row))
                                .as("seed %d, the ground under it", seed)
                                .isTrue();
                        assertThat(mission.laid().kept(col, row))
                                .as("seed %d, on a way in", seed)
                                .isFalse();
                    }
                }
                iced++;
            }
        }
        assertThat(iced).as("ice across the seeds").isPositive();
    }

    /** The rift's sites are in its clearings (G0115): a control point on the centre tile of each, in every rift dealt. */
    @Test
    void standsAControlPointAtTheCentreOfEachOfTheRiftsClearings() {
        SimConfig shipped = SimConfig.defaults();
        int seen = 0;
        for (long seed = 0; seed < 64; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            TileMap map = mission.laid().map();
            for (int p = 0; p < mission.order().size(); p++) {
                if (mission.order().get(p) != BiomeKind.RIFT) {
                    continue;
                }
                TileRect rect = mission.laid().biomes().get(p);
                List<Clearing> clearings = mission.laid().clearings().stream()
                        .filter(clearing -> rect.contains(clearing.col(), clearing.row()))
                        .toList();
                List<Placed> points = points(mission).stream()
                        .filter(point -> rect.contains(map.colAt(point.x()), map.rowAt(point.y())))
                        .toList();

                assertThat(clearings).as("seed %d", seed).hasSize(2);
                for (Clearing clearing : clearings) {
                    assertThat(points)
                            .as("seed %d, %s", seed, clearing)
                            .anyMatch(point ->
                                    map.colAt(point.x()) == clearing.col() && map.rowAt(point.y()) == clearing.row());
                }
                seen++;
            }
        }
        assertThat(seen).as("rifts across the seeds").isPositive();
    }

    /**
     * Seed 2777720 (EHJD-YK39) deals the rift last, with no corridor through any of its nooks: its jaguar's hole still
     * goes down, in a nook, on every tier (finale-variety ticket 01; G0070 - a short biome is a fault).
     */
    @Test
    void laysTheRiftsNestInANookTheMazeLeftBare() {
        for (TierKind tier : TierKind.values()) {
            Mission mission = Mission.build(SimConfig.defaults().withSeed(2_777_720L), tier);
            int last = mission.order().size() - 1;
            TileMap map = mission.laid().map();

            assertThat(mission.order().get(last)).isEqualTo(BiomeKind.RIFT);
            assertThat(mission.shortfalls()).as("%s", tier).isEmpty();
            assertThat(mission.placed())
                    .as("%s", tier)
                    .anyMatch(hole -> hole instanceof Placed.Nest
                            && hole.biome() == last
                            && mission.laid().nooks().stream()
                                    .anyMatch(nook -> nook.contains(map.colAt(hole.x()), map.rowAt(hole.y()))));
        }
    }

    /**
     * The rift's holes go in its nooks first (G0115, amended 2026-11-24): over every rift dealt, most of its nests stand
     * in a nook - one goes elsewhere only when the keep-offs leave no nook room, and the maze between seldom has any.
     */
    @Test
    void putsTheRiftsNestsInItsNooksFirst() {
        SimConfig shipped = SimConfig.defaults();
        int inNooks = 0;
        int elsewhere = 0;
        for (long seed = 0; seed < 64; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            TileMap map = mission.laid().map();
            for (int p = 0; p < mission.order().size(); p++) {
                if (mission.order().get(p) != BiomeKind.RIFT) {
                    continue;
                }
                TileRect rect = mission.laid().biomes().get(p);
                for (Placed hole : mission.placed()) {
                    int col = map.colAt(hole.x());
                    int row = map.rowAt(hole.y());
                    if (!(hole instanceof Placed.Nest) || !rect.contains(col, row)) {
                        continue;
                    }
                    if (mission.laid().nooks().stream().anyMatch(nook -> nook.contains(col, row))) {
                        inNooks++;
                    } else {
                        elsewhere++;
                    }
                }
            }
        }
        assertThat(inNooks).as("rift nests in a nook").isGreaterThan(2 * elsewhere);
    }

    /**
     * More of the swarm in the rift's clearings than in the maze between them (G0115): loose creatures to a tile of open
     * ground, counted over every rift dealt.
     */
    @Test
    void putsMoreOfTheSwarmToATileInTheRiftsClearingsThanInItsMaze() {
        SimConfig shipped = SimConfig.defaults();
        long[] inClearings = new long[2];
        long[] inMaze = new long[2];
        for (long seed = 0; seed < 64; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            TileMap map = mission.laid().map();
            for (int p = 0; p < mission.order().size(); p++) {
                if (mission.order().get(p) != BiomeKind.RIFT) {
                    continue;
                }
                TileRect rect = mission.laid().biomes().get(p);
                for (int row = rect.row(); row < rect.endRow(); row++) {
                    for (int col = rect.col(); col < rect.endCol(); col++) {
                        if (map.passableAtTile(col, row)) {
                            (inClearing(mission, col, row) ? inClearings : inMaze)[0]++;
                        }
                    }
                }
                int position = p;
                for (Placed placed : mission.placed()) {
                    if (placed instanceof Placed.Loose && placed.biome() == position) {
                        boolean clear = inClearing(mission, map.colAt(placed.x()), map.rowAt(placed.y()));
                        (clear ? inClearings : inMaze)[1]++;
                    }
                }
            }
        }
        assertThat(inClearings[0]).as("open tiles in the rifts' clearings").isPositive();
        assertThat(inClearings[1] / (double) inClearings[0])
                .as("loose creatures to an open tile, clearings against maze")
                .isGreaterThan(inMaze[1] / (double) inMaze[0]);
    }

    /** A biome's trees number between its fewest and most, each trunk as big as a trunk may be (G0072). */
    @Test
    void putsBetweenItsFewestAndMostTreesInEveryBiomeEachTrunkInItsRange() {
        SimConfig shipped = SimConfig.defaults();
        int seen = 0;
        for (long seed = 0; seed < 8; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.MEDIUM);
            for (int p = 0; p < mission.order().size(); p++) {
                List<Placed.Tree> trees = trees(mission, p);
                var furniture = shipped.biomes().of(mission.order().get(p)).furniture();

                assertThat(trees.size())
                        .as("seed %d, biome %d", seed, p)
                        .isBetween(furniture.treesMin(), furniture.treesMax());
                for (Placed.Tree tree : trees) {
                    assertThat(tree.trunk())
                            .as("seed %d, %s", seed, tree)
                            .isBetween(
                                    shipped.obstacle().trunkMin(),
                                    shipped.obstacle().trunkMax());
                }
                seen += trees.size();
            }
        }
        assertThat(seen).as("trees across the seeds").isPositive();
    }

    /** The first trees of a biome that asks for them stand within a few tiles of each of its nests (G0072). */
    @Test
    void standsTreesBesideEveryNestOfABiomeThatAsksForThem() {
        SimConfig shipped = SimConfig.defaults();
        float reach = shipped.mission().placement().treeReach();
        int nests = 0;
        for (long seed = 0; seed < 16; seed++) {
            Mission mission = Mission.build(shipped.withSeed(seed), TierKind.HARD);
            for (Placed placed : mission.placed()) {
                if (!(placed instanceof Placed.Nest nest)
                        || nest.biome() >= mission.order().size()) {
                    continue;
                }
                int wanted = shipped.biomes()
                        .of(mission.order().get(nest.biome()))
                        .furniture()
                        .treesByNestMin();
                if (wanted == 0) {
                    continue;
                }
                long beside = trees(mission, nest.biome()).stream()
                        .filter(tree -> Math.hypot(tree.x() - nest.x(), tree.y() - nest.y()) <= reach)
                        .count();

                assertThat(beside).as("seed %d, %s", seed, nest).isGreaterThanOrEqualTo(wanted);
                nests++;
            }
        }
        assertThat(nests).as("nests asking for trees across the seeds").isPositive();
    }

    /** A tree stands as a boulder does - an obstacle - its body the trunk it was placed with, and drawn as a tree. */
    @Test
    void spawnsEveryTreeAsAnObstacleTheSizeOfItsTrunk() {
        int seen = 0;
        for (long seed = 0; seed < 8; seed++) {
            Simulation simulation = Scenarios.world(
                    Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(seed));
            Mission mission = Mission.build(simulation.config(), TierKind.MEDIUM);
            List<Float> placed = new ArrayList<>();
            for (int p = 0; p < mission.order().size(); p++) {
                for (Placed.Tree tree : trees(mission, p)) {
                    placed.add(tree.trunk());
                }
            }
            List<Float> spawned = new ArrayList<>();
            for (Entity entity : simulation
                    .engine()
                    .getEntitiesFor(Family.all(Obstacle.class, Body.class, Appearance.class)
                            .get())) {
                if (entity.getComponent(Appearance.class).look == Look.TREE) {
                    spawned.add(entity.getComponent(Body.class).radius);
                }
            }

            assertThat(spawned).as("seed %d", seed).containsExactlyInAnyOrderElementsOf(placed);
            seen += spawned.size();
        }
        assertThat(seen).as("trees across the seeds").isPositive();
    }

    /**
     * One bit flips the finale's skeleton north-south (G0064, G0120): its post where it was, its lair's spots and its
     * mouths mirrored about the anchor's row, nothing moved east-west, so west stays the way in.
     */
    @Test
    void flipsTheFinalesSkeletonNorthSouthAndNeverEastWest() {
        for (FinaleKind kind : FinaleKind.values()) {
            Mission plain = endingOn(kind, false, 0);
            Mission flipped = endingOn(kind, true, 0);

            assertThat(skeleton(flipped, true)).as("%s", kind).isEqualTo(skeleton(plain, false));
            assertThat(skeleton(plain, false)).as("%s", kind).contains("Hold 0 0");
        }
    }

    /**
     * A finale's cover is dealt by its rules (G0120), afresh for every seed, and laid as ground: every tile of every
     * piece rock on the strip, and two missions on one finale two grounds.
     */
    @Test
    void dealsTheFinalesCoverByItsRulesAndLaysItAsGround() {
        for (FinaleKind kind : FinaleKind.values()) {
            Set<List<String>> grounds = new HashSet<>();
            for (int nth = 0; nth < 3; nth++) {
                Mission mission = endingOn(kind, false, nth);
                TileMap map = mission.laid().map();
                TileRect anchor = Strip.anchor(mission.laid().biomes().getLast());

                assertThat(mission.finale().cover()).as("%s", kind).isNotEmpty();
                for (TileRect piece : mission.finale().cover()) {
                    for (int row = piece.row(); row < piece.endRow(); row++) {
                        for (int col = piece.col(); col < piece.endCol(); col++) {
                            assertThat(map.passableAtTile(anchor.col() + col, anchor.row() + row))
                                    .as("%s piece %s", kind, piece)
                                    .isFalse();
                        }
                    }
                }
                grounds.add(finaleTiles(mission, false));
            }
            assertThat(grounds).as("%s", kind).hasSize(3);
        }
    }

    /**
     * Everything a finale's rules put down comes in mirrored north-south pairs (G0120): each thing round the post and
     * each of the lair's caches has its mirror about the anchor's row, and so has each piece of cover.
     */
    @Test
    void placesEverythingRoundTheFinaleInMirroredPairs() {
        for (FinaleKind kind : FinaleKind.values()) {
            Mission mission = endingOn(kind, true, 1);
            TileMap map = mission.laid().map();
            float row = map.tileCentreY(
                    Strip.anchor(mission.laid().biomes().getLast()).row());
            List<float[]> things = new ArrayList<>();
            for (Placed placed : mission.placed()) {
                if (placed.biome() == mission.order().size() && !(placed instanceof Placed.Hold)) {
                    things.add(new float[] {placed.x(), placed.y()});
                }
            }
            for (Arrival arrival : mission.finale().lair()) {
                if (arrival.form() == Arrival.Form.PICKUP) {
                    things.add(new float[] {arrival.x(), arrival.y()});
                }
            }

            assertThat(things).as("%s", kind).isNotEmpty();
            for (float[] one : things) {
                assertThat(things)
                        .as("%s: the mirror of %f %f", kind, one[0], one[1])
                        .anyMatch(other -> Math.abs(other[0] - one[0]) < 1e-3f
                                && Math.abs((other[1] - row) + (one[1] - row)) < 1e-3f);
            }
            assertThat(mission.finale().cover())
                    .as("%s", kind)
                    .containsExactlyInAnyOrderElementsOf(mission.finale().cover().stream()
                            .map(pl.mzebrows.pwt.sim.Offset::flipped)
                            .toList());
        }
    }

    /**
     * The keep's two guns stand between the post and the boss, inside his barrage (G0120): a tool that costs exposure
     * to use, never a seat behind the post.
     */
    @Test
    void standsTheKeepsTwoGunsInsideTheBossesBarrage() {
        SimConfig shipped = SimConfig.defaults();
        float reach = shipped.weapon(WeaponKind.SPEW).projectileRange();
        for (int nth = 0; nth < 4; nth++) {
            Mission mission = endingOn(FinaleKind.KEEP, nth % 2 == 1, nth / 2);
            Arrival boss = mission.finale().lair().getLast();
            List<Placed> guns = mission.placed().stream()
                    .filter(placed -> placed instanceof Placed.Turret
                            && placed.biome() == mission.order().size())
                    .toList();

            assertThat(boss.strain()).isEqualTo(UnitKind.BOSS_GRUNT);
            assertThat(guns).hasSize(2);
            for (Placed gun : guns) {
                float dx = gun.x() - boss.x();
                float dy = gun.y() - boss.y();
                assertThat((float) Math.sqrt(dx * dx + dy * dy)).isLessThanOrEqualTo(reach);
                assertThat(gun.x()).isLessThan(boss.x());
            }
        }
    }

    /**
     * Each redoubt wave is dealt one member of its role (G0120): its strain one the role names, the same on every
     * build of one seed, and over a few seeds the same place in the schedule sent more than one strain.
     */
    @Test
    void dealsEachWaveOneMemberOfItsRole() {
        FinaleDef redoubt = SimConfig.defaults().finales().of(FinaleKind.REDOUBT);
        List<Set<UnitKind>> seen = new ArrayList<>();
        for (int w = 0; w < redoubt.waves().size(); w++) {
            seen.add(new HashSet<>());
        }
        for (int nth = 0; nth < 6; nth++) {
            Mission mission = endingOn(FinaleKind.REDOUBT, nth % 2 == 1, nth / 2);
            Mission again = Mission.build(SimConfig.defaults().withSeed(mission.seed()), TierKind.MEDIUM);

            assertThat(again.finale().waves())
                    .extracting(Wave::kind)
                    .isEqualTo(mission.finale().waves().stream().map(Wave::kind).toList());
            for (int w = 0; w < redoubt.waves().size(); w++) {
                UnitKind strain = mission.finale().waves().get(w).kind();
                assertThat(redoubt.roles().of(redoubt.waves().get(w).role()))
                        .as("wave %d", w)
                        .extracting(WaveLine.Member::strain)
                        .contains(strain);
                seen.get(w).add(strain);
            }
        }
        assertThat(seen).anySatisfy(strains -> assertThat(strains).hasSizeGreaterThan(1));
    }

    /**
     * The keep's six holes are each dealt a strain of its pool (G0120), no strain more than twice, and its lair's guns
     * from its list, none twice; two keeps deal two different lairs.
     */
    @Test
    void dealsTheKeepsHolesFromItsPoolAndItsGunsFromItsList() {
        FinaleDef keep = SimConfig.defaults().finales().of(FinaleKind.KEEP);
        Set<UnitKind> pool = new HashSet<>();
        for (FinaleDef.Pooled pooled : keep.pool().strains()) {
            pool.add(pooled.strain());
        }
        Set<List<UnitKind>> lairs = new HashSet<>();
        for (int nth = 0; nth < 6; nth++) {
            Mission mission = endingOn(FinaleKind.KEEP, nth % 2 == 1, nth / 2);
            List<UnitKind> holes = new ArrayList<>();
            List<PickupKind> guns = new ArrayList<>();
            for (Arrival arrival : mission.finale().lair()) {
                if (arrival.form() == Arrival.Form.NEST) {
                    holes.add(arrival.strain());
                } else if (arrival.form() == Arrival.Form.PICKUP) {
                    guns.add(arrival.pickup());
                }
            }

            assertThat(holes).hasSize(6).allMatch(pool::contains);
            for (UnitKind strain : holes) {
                assertThat(holes.stream().filter(strain::equals).count())
                        .as("%s", strain)
                        .isLessThanOrEqualTo(2);
            }
            assertThat(guns)
                    .hasSize(keep.rules().lair().max())
                    .doesNotHaveDuplicates()
                    .allMatch(gun -> keep.rules().lairKinds().contains(new FinaleRules.Pick(gun)));
            lairs.add(holes);
        }
        assertThat(lairs).hasSizeGreaterThan(1);
    }

    /** A wave from two mouths is one wave of the schedule, a lot from each, each its own draw (G0087). */
    @Test
    void sendsAWaveFromEachMouthItNamesAsOneWave() {
        Mission mission = endingOn(FinaleKind.REDOUBT, false, 0);
        List<WaveLine> lines =
                SimConfig.defaults().finales().of(FinaleKind.REDOUBT).waves();

        assertThat(mission.finale().waves()).hasSameSizeAs(lines);
        for (int w = 0; w < lines.size(); w++) {
            assertThat(mission.finale().waves().get(w).lots())
                    .as("wave %d", w)
                    .hasSize(lines.get(w).mouthNames().size());
        }
        assertThat(mission.finale().waves())
                .anySatisfy(wave -> assertThat(wave.lots())
                        .hasSize(2)
                        .extracting(Wave.Lot::where)
                        .doesNotHaveDuplicates());
    }

    /**
     * The keep's lair (G0048, G0064): nothing of it on the field until every commander stands in the finale, and then
     * all of it at once - the boss facing the players, never his back to the way in.
     */
    @Test
    void raisesTheKeepsLairOnlyOnceEveryCommanderIsInTheFinale() {
        long seed = 0;
        while (Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM)
                        .finale()
                        .kind()
                != FinaleKind.KEEP) {
            seed++;
        }
        Simulation simulation = Scenarios.world(
                Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(seed));
        Mission mission = Mission.build(simulation.config(), TierKind.MEDIUM);
        TileRect finale = mission.laid().biomes().getLast();
        simulation.step();

        assertThat(simulation.lair().waiting()).isTrue();
        assertThat(simulation.lair().ground()).isEqualTo(finale);
        assertThat(simulation.lair().size()).isEqualTo(mission.finale().lair().size());
        assertThat(bosses(simulation)).isEmpty();

        TileRect anchor = Strip.anchor(finale);
        TileMap map = mission.laid().map();
        for (Entity commander :
                simulation.engine().getEntitiesFor(Family.all(Commander.class).get())) {
            commander.getComponent(Position.class).set(map.tileCentreX(anchor.col()), map.tileCentreY(anchor.row()));
        }
        simulation.step();

        assertThat(bosses(simulation)).hasSize(1);
        assertThat(bosses(simulation).getFirst().getComponent(Facing.class).x).isNegative();
    }

    /**
     * A mission puts every strain down unaware (G0063), and the worm's biome is its range (G0096): a commander who
     * stands in the desert beside one is gone after and bitten, as on the proving ground.
     */
    @Test
    void sendsAWormAfterACommanderInItsBiome() {
        Simulation simulation = Scenarios.world(
                Scenarios.mission(TierKind.MEDIUM), SimConfig.defaults().withSeed(0L));
        Entity worm = null;
        for (Entity unit :
                simulation.engine().getEntitiesFor(Family.all(Burrow.class).get())) {
            if (unit.getComponent(Burrow.class).enabled) {
                worm = unit;
            }
        }
        assertThat(worm).as("seed 0's mission deals a desert with a worm in it").isNotNull();
        Entity commander = simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first();
        Position at = worm.getComponent(Position.class);
        commander.getComponent(Position.class).set(at.x + 10f, at.y);

        for (int tick = 0; tick < 600 && commander.getComponent(Vitality.class).wounds == 0; tick++) {
            simulation.step();
        }

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** The tier moves how many come in a wave and nothing else of the finale: not its cover, timing or mouths. */
    @Test
    void endsOnTheSameFinaleOnEveryTierButForHowManyComeInAWave() {
        int withWaves = 0;
        for (long seed = 0; seed < 4; seed++) {
            Mission easy = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.EASY);
            Mission medium = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
            Mission hard = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.HARD);

            for (Mission other : List.of(easy, hard)) {
                assertThat(other.finale().kind()).isEqualTo(medium.finale().kind());
                assertThat(other.finale().flipped()).isEqualTo(medium.finale().flipped());
                assertThat(other.finale().cover()).isEqualTo(medium.finale().cover());
                assertThat(finaleThings(other, false)).isEqualTo(finaleThings(medium, false));
                assertThat(other.finale().waves())
                        .extracting(Wave::afterSeconds, Wave::kind, MissionTest::mouths)
                        .isEqualTo(medium.finale().waves().stream()
                                .map(wave -> tuple(wave.afterSeconds(), wave.kind(), mouths(wave)))
                                .toList());
            }
            if (!medium.finale().waves().isEmpty()) {
                assertThat(sent(easy)).as("seed %d", seed).isLessThan(sent(medium));
                assertThat(sent(medium)).as("seed %d", seed).isLessThan(sent(hard));
                withWaves++;
            }
        }
        assertThat(withWaves).as("a finale with waves among the seeds").isPositive();
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
                .isEqualTo(mission.finale()
                                .def(simulation.config())
                                .rules()
                                .turrets()
                                .max()
                        + mission.placed().stream()
                                .filter(placed -> placed instanceof Placed.Turret
                                        && placed.biome() < mission.order().size())
                                .count());
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Mine.class).get())
                        .size())
                .isEqualTo(mission.placed().stream()
                        .filter(placed -> placed instanceof Placed.Mine)
                        .count());
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

    private static List<Entity> bosses(Simulation simulation) {
        List<Entity> bosses = new ArrayList<>();
        for (Entity entity :
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get())) {
            if (entity.getComponent(Strain.class).kind == UnitKind.BOSS_GRUNT) {
                bosses.add(entity);
            }
        }
        return bosses;
    }

    private static Terrain base(SimConfig config, BiomeKind kind) {
        return config.biomes().of(kind).ground().base();
    }

    private static boolean inClearing(Mission mission, int col, int row) {
        for (Clearing clearing : mission.laid().clearings()) {
            if (clearing.contains(col, row)) {
                return true;
            }
        }
        return false;
    }

    private static List<Placed> points(Mission mission) {
        return mission.placed().stream()
                .filter(placed -> placed instanceof Placed.Point)
                .toList();
    }

    private static List<Placed.Tree> trees(Mission mission, int biome) {
        List<Placed.Tree> trees = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Tree tree && tree.biome() == biome) {
                trees.add(tree);
            }
        }
        return trees;
    }

    private static int hostile(Mission mission) {
        return (int) mission.placed().stream()
                .filter(placed -> placed instanceof Placed.Loose || placed instanceof Placed.Nest)
                .count();
    }

    /** The {@code nth} seed, from nought, whose mission ends on this finale, flipped or not. */
    private static Mission endingOn(FinaleKind kind, boolean flipped, int nth) {
        int found = 0;
        for (long seed = 0; seed < SEEDS * 8L; seed++) {
            Mission.Finale drawn = Mission.drawn(SimConfig.defaults().withSeed(seed));
            if (drawn.kind() == kind && drawn.flipped() == flipped && found++ == nth) {
                return Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
            }
        }
        throw new AssertionError(
                "no " + nth + "th seed of " + SEEDS * 8L + " ends on " + kind + (flipped ? " flipped" : ""));
    }

    /** The finale's skeleton - its post, its lair's hole spots and boss, its waves' mouths - by tile offset, sorted. */
    private static List<String> skeleton(Mission mission, boolean mirrored) {
        TileMap map = mission.laid().map();
        TileRect anchor = Strip.anchor(mission.laid().biomes().getLast());
        List<String> things = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Hold) {
                int dy = map.rowAt(placed.y()) - anchor.row();
                things.add("Hold " + (map.colAt(placed.x()) - anchor.col()) + " " + (mirrored ? -dy : dy));
            }
        }
        for (Arrival arrival : mission.finale().lair()) {
            if (arrival.form() != Arrival.Form.PICKUP) {
                int dy = map.rowAt(arrival.y()) - anchor.row();
                // A hole's spot is the skeleton's; its strain the pool's (G0120).
                Object who = arrival.form() == Arrival.Form.UNIT ? arrival.strain() : "";
                things.add("Lair " + arrival.form() + " " + who + " " + (map.colAt(arrival.x()) - anchor.col()) + " "
                        + (mirrored ? -dy : dy));
            }
        }
        for (Wave wave : mission.finale().waves()) {
            for (Wave.Lot lot : wave.lots()) {
                TileRect where = lot.where();
                int south = where.row() - anchor.row();
                int dy = mirrored ? -(south + where.height() - 1) : south;
                things.add("Mouth " + (where.col() - anchor.col()) + " " + dy);
            }
        }
        return things.stream().sorted().toList();
    }

    private static List<TileRect> mouths(Wave wave) {
        return wave.lots().stream().map(Wave.Lot::where).toList();
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
        List<String> things = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed.biome() == mission.order().size()) {
                int dy = map.rowAt(placed.y()) - anchor.row();
                things.add(placed.getClass().getSimpleName() + " " + (map.colAt(placed.x()) - anchor.col()) + " "
                        + (mirrored ? -dy : dy));
            }
        }
        for (Arrival arrival : mission.finale().lair()) {
            int dy = map.rowAt(arrival.y()) - anchor.row();
            things.add("Lair " + arrival.form() + " " + arrival.strain() + " " + arrival.pickup() + " "
                    + (map.colAt(arrival.x()) - anchor.col()) + " " + (mirrored ? -dy : dy));
        }
        return things.stream().sorted().toList();
    }

    private static Terrain[] terrain(TileMap map) {
        Terrain[] tiles = new Terrain[map.cols() * map.rows()];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = map.terrainAtTile(i % map.cols(), i / map.cols());
        }
        return tiles;
    }

    /** Whether a house stands wholly inside a biome: its own, never a gatehouse in a ridge. */
    private static boolean ownHouse(TileRect biome, House house) {
        TileRect walls = house.walls();
        return biome.contains(walls.col(), walls.row()) && biome.contains(walls.endCol() - 1, walls.endRow() - 1);
    }

    private static List<Placed> emplacements(Mission mission) {
        return mission.placed().stream()
                .filter(placed -> placed instanceof Placed.Emplacement)
                .toList();
    }
}
