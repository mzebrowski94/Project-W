package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.FlowField;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The claims this level makes that a glance at it would not catch.
 *
 * <p>{@code ScenariosTest} already says nothing is inside rock and everything is reachable, for every
 * scenario there is. What is only true of this one is its <strong>shape</strong>: four biomes in
 * order, two points in each, and three walls whose gaps are the only way east. Every one of those is
 * silent when it breaks - move a wall a tile and the level still plays, it has just stopped being a
 * march and become a corridor with scenery.
 */
@SuppressWarnings("EnumOrdinal")
class AdventureScenarioTest {

    private static final String NAME = "Adventure";

    /** Where each biome ends, in tile columns. The bands the whole level is built out of. */
    private static final int[] BIOME_END = {30, 60, 90, 120};

    /** Every kind of weapons cache there is. One per gun, and this list must grow with that enum. */
    private static final EnumSet<PickupKind> CACHES =
            EnumSet.of(PickupKind.ARMOURY_RAILGUN, PickupKind.ARMOURY_FLAMER, PickupKind.ARMOURY_SHOTGUN);

    @Test
    void laysOutTwoPointsInEveryBiome() {
        List<Position> points = pointsOf(world());
        TileMap map = world().map();

        int[] perBiome = new int[BIOME_END.length];
        for (Position point : points) {
            perBiome[biomeOf(map.colAt(point.x))]++;
        }

        assertThat(points).hasSize(2 * BIOME_END.length);
        assertThat(perBiome).containsOnly(2);
    }

    /** Different ground in different biomes, or they are four names for the same walk. */
    @Test
    void givesTheBiomesGroundThatDiffers() {
        TileMap map = world().map();

        EnumSet<Terrain> grounds = EnumSet.noneOf(Terrain.class);
        for (int biome = 0; biome < BIOME_END.length; biome++) {
            grounds.add(commonestGroundIn(map, biome));
        }

        assertThat(grounds).doesNotContain(Terrain.ROCK);
        assertThat(grounds).hasSizeGreaterThanOrEqualTo(2);
    }

    /** Patches of another ground inside a biome, which is most of what stops it reading as a sheet. */
    @Test
    void breaksEachBiomeUpWithGroundThatIsNotItsOwn() {
        TileMap map = world().map();

        for (int biome = 0; biome < BIOME_END.length; biome++) {
            Terrain commonest = commonestGroundIn(map, biome);
            assertThat(groundsIn(map, biome))
                    .describedAs("grounds in biome " + biome)
                    .anyMatch(ground -> ground != commonest && ground != Terrain.ROCK);
        }
    }

    /**
     * The walk east exists at all. Three walls with one gap each is exactly the layout where a
     * one-tile slip seals a biome off, and the level would still start, still draw, and be
     * unfinishable.
     */
    @Test
    void leavesAWayThroughEveryWall() {
        Simulation simulation = world();
        TileMap map = simulation.map();
        FlowField field = FlowField.over(map, simulation.config().trooper().radius());
        field.rebuildFor(map.cols() - 3, map.rows() / 2);

        for (int biome = 0; biome < BIOME_END.length; biome++) {
            int[] tile = someWalkableTileIn(map, biome);
            assertThat(field.stepsTo(tile[0], tile[1]))
                    .describedAs("walk east from biome " + biome)
                    .isNotNegative();
        }
    }

    private static int biomeOf(int col) {
        for (int biome = 0; biome < BIOME_END.length; biome++) {
            if (col < BIOME_END[biome]) {
                return biome;
            }
        }
        return BIOME_END.length - 1;
    }

    /** The first column of a biome that is not one of its walls. */
    private static int startOf(int biome) {
        return biome == 0 ? 1 : BIOME_END[biome - 1] + 1;
    }

    /** Every kind of ground this biome has anywhere in it. */
    private static EnumSet<Terrain> groundsIn(TileMap map, int biome) {
        EnumSet<Terrain> found = EnumSet.noneOf(Terrain.class);
        for (int col = startOf(biome); col < BIOME_END[biome]; col++) {
            for (int row = 1; row < map.rows() - 1; row++) {
                found.add(map.terrainAtTile(col, row));
            }
        }
        return found;
    }

    /** What this biome is mostly made of - its own ground, whatever is patched into it. */
    private static Terrain commonestGroundIn(TileMap map, int biome) {
        int[] counted = new int[Terrain.values().length];
        for (int col = startOf(biome); col < BIOME_END[biome]; col++) {
            for (int row = 1; row < map.rows() - 1; row++) {
                counted[map.terrainAtTile(col, row).ordinal()]++;
            }
        }
        Terrain commonest = Terrain.values()[0];
        for (Terrain ground : Terrain.values()) {
            if (counted[ground.ordinal()] > counted[commonest.ordinal()]) {
                commonest = ground;
            }
        }
        return commonest;
    }

    /** A tile in this biome something can actually stand on, for the tests that walk from one. */
    private static int[] someWalkableTileIn(TileMap map, int biome) {
        for (int col = startOf(biome); col < BIOME_END[biome]; col++) {
            for (int row = 1; row < map.rows() - 1; row++) {
                if (map.terrainAtTile(col, row).passable()) {
                    return new int[] {col, row};
                }
            }
        }
        throw new IllegalStateException("biome " + biome + " has nowhere to stand");
    }

    /**
     * The march gets harder because the ground fills in behind you faster, not because the numbers on
     * the creatures went up. One more nest in each biome than the last is the whole of that claim,
     * and it is exactly the sort of thing that survives a careless edit while stopping being true.
     */
    @Test
    void putsOneMoreNestInEachBiomeThanTheLast() {
        Simulation world = world();
        TileMap map = world.map();

        int[] perBiome = new int[BIOME_END.length];
        for (Entity nest : world.engine()
                .getEntitiesFor(Family.all(Nest.class, Position.class).get())) {
            perBiome[biomeOf(map.colAt(nest.getComponent(Position.class).x))]++;
        }

        assertThat(perBiome).containsExactly(1, 2, 3, 4);
    }

    /** Every strain a biome teaches has a hole sending more of it out, somewhere in that biome. */
    @Test
    void givesEachBiomeNestsOfWhatItTeaches() {
        Simulation world = world();

        EnumSet<UnitKind> hatching = EnumSet.noneOf(UnitKind.class);
        for (Entity nest : world.engine().getEntitiesFor(Family.all(Nest.class).get())) {
            hatching.add(nest.getComponent(Nest.class).kind);
        }

        assertThat(hatching)
                .containsExactlyInAnyOrder(UnitKind.GRUNT, UnitKind.SPITTER, UnitKind.RUNNER, UnitKind.BRUTE);
    }

    private static List<Position> pointsOf(Simulation simulation) {
        ImmutableArray<Entity> found = simulation
                .engine()
                .getEntitiesFor(Family.all(ControlPoint.class, Position.class).get());
        List<Position> positions = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            positions.add(found.get(i).getComponent(Position.class));
        }
        return positions;
    }

    /**
     * A better gun exists on this march, and only in the half of it that needs one. The plain and the
     * dust are what a sidearm is tuned for; a cache in either is a shortcut past the part that teaches
     * you to aim, and it is exactly the kind of thing that gets added later without anyone noticing.
     */
    @Test
    void offersABetterGunOnlyInTheLastTwoBiomes() {
        Simulation world = world();
        TileMap map = world.map();

        List<Position> caches = armouriesOf(world);

        assertThat(caches).isNotEmpty();
        for (Position cache : caches) {
            assertThat(biomeOf(map.colAt(cache.x))).isGreaterThanOrEqualTo(2);
        }
    }

    /**
     * Every crate, of whichever gun. Asking about one kind would have quietly stopped covering the
     * other two the day they were added, which is the failure this whole class exists to catch.
     */
    private static List<Position> armouriesOf(Simulation world) {
        List<Position> found = new ArrayList<>();
        ImmutableArray<Entity> pickups = world.engine()
                .getEntitiesFor(Family.all(Pickup.class, Position.class).get());
        for (Entity pickup : pickups) {
            if (CACHES.contains(pickup.getComponent(Pickup.class).kind)) {
                found.add(pickup.getComponent(Position.class));
            }
        }
        return found;
    }

    private static Simulation world() {
        return Scenarios.world(
                Scenarios.all().stream()
                        .filter(scenario -> NAME.equals(scenario.name()))
                        .findFirst()
                        .orElseThrow(),
                SimConfig.defaults());
    }
}
