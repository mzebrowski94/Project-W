package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.badlogic.ashley.core.Entity;
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
import pl.mzebrows.pwt.sim.FinaleDef;
import pl.mzebrows.pwt.sim.Hold;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WaveLine;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Burrow;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Arrival;
import pl.mzebrows.pwt.sim.def.BiomeKind;
import pl.mzebrows.pwt.sim.def.FinaleKind;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
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
     * One bit flips the finale north-south (G0064): every tile of its ground and everything standing on it mirrored
     * about the anchor's row, and nothing moved east-west, so west stays the way in - in every layout (G0087).
     */
    @Test
    void flipsTheFinaleNorthSouthAndNeverEastWest() {
        for (FinaleKind kind : FinaleKind.values()) {
            int layouts = SimConfig.defaults().finales().of(kind).layoutCount();
            for (int layout = 0; layout < layouts; layout++) {
                Mission plain = endingOn(kind, false, layout);
                Mission flipped = endingOn(kind, true, layout);

                assertThat(finaleTiles(flipped, true))
                        .as("%s layout %d", kind, layout)
                        .isEqualTo(finaleTiles(plain, false));
                assertThat(finaleThings(flipped, true))
                        .as("%s layout %d", kind, layout)
                        .isEqualTo(finaleThings(plain, false));
                assertThat(finaleThings(plain, false)).contains("Hold 0 0", "Pickup -3 0");
            }
        }
    }

    /**
     * A finale's stream deals one of its layouts a mission (G0087), each of them on some seed, and lays it: the
     * finale's rock is the dealt layout's, so two layouts of one finale are two grounds.
     */
    @Test
    void dealsEveryLayoutOfEveryFinaleAndLaysTheOneDealt() {
        for (FinaleKind kind : FinaleKind.values()) {
            FinaleDef def = SimConfig.defaults().finales().of(kind);
            assertThat(def.layoutCount()).as("%s", kind).isGreaterThan(1);
            Set<List<String>> grounds = new HashSet<>();
            for (int layout = 0; layout < def.layoutCount(); layout++) {
                Mission mission = endingOn(kind, false, layout);

                assertThat(mission.finale().def(SimConfig.defaults()).ground().pieces())
                        .isEqualTo(def.laid(layout).ground().pieces());
                grounds.add(finaleTiles(mission, false));
            }
            assertThat(grounds).as("%s", kind).hasSize(def.layoutCount());
        }
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

    /** The tier moves how many come in a wave and nothing else of the finale: not its layout, timing or mouths. */
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
                assertThat(other.finale().layout()).isEqualTo(medium.finale().layout());
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
        Hold hold = mission.finale().def(simulation.config()).hold();
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Turret.class).get())
                        .size())
                .isEqualTo(hold.turrets().size()
                        + mission.placed().stream()
                                .filter(placed -> placed instanceof Placed.Turret
                                        && placed.biome() < mission.order().size())
                                .count());
        assertThat(simulation
                        .engine()
                        .getEntitiesFor(Family.all(Mine.class).get())
                        .size())
                .isEqualTo(hold.mines().size());
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

    /** The first seed whose mission ends on this finale, flipped or not, with this layout dealt. */
    private static Mission endingOn(FinaleKind kind, boolean flipped, int layout) {
        for (long seed = 0; seed < SEEDS * 8L; seed++) {
            Mission.Finale drawn = Mission.drawn(SimConfig.defaults().withSeed(seed));
            if (drawn.kind() == kind && drawn.flipped() == flipped && drawn.layout() == layout) {
                Mission mission = Mission.build(SimConfig.defaults().withSeed(seed), TierKind.MEDIUM);
                assertThat(mission.finale().layout()).isEqualTo(layout);
                return mission;
            }
        }
        throw new AssertionError(
                "no seed of " + SEEDS * 8L + " ends on " + kind + " layout " + layout + (flipped ? " flipped" : ""));
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
}
