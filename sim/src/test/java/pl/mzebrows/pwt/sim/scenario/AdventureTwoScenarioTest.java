package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.PickupKind;
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

    /** A tile just through the keep's west gap, clear of everything laid in it. */
    private static final int KEEP_ENTRANCE_COL = 183;

    private static final int KEEP_ENTRANCE_ROW = 14;

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

    /**
     * The end of the march (G0048): nothing of the boss is on the field until every player is inside the
     * keep, so pulling the view back from the brake shows an empty one.
     */
    @Test
    void keepsTheBossOffTheFieldUntilEveryPlayerIsInTheKeep() {
        Simulation world = world();
        TileMap map = world.map();
        world.step();

        assertThat(bosses(world)).isEmpty();

        enterTheKeep(world);
        world.step();

        List<Entity> bosses = bosses(world);
        assertThat(bosses).hasSize(1);
        assertThat(biomeOf(map.colAt(bosses.getFirst().getComponent(Position.class).x)))
                .isEqualTo(BIOME_END.length - 1);
    }

    /**
     * Six holes, one of every strain that walks, and a cache of every gun, round a boss - none of it in
     * rock, on top of anything, or sealed off.
     */
    @Test
    void raisesTheBossAmongANestOfEveryStrainAndEveryGunClearOfRockAndOfEachOther() {
        Simulation world = world();
        TileMap map = world.map();
        world.step();
        List<Entity> before = nests(world);
        List<Entity> cachesBefore = caches(world);

        enterTheKeep(world);
        world.step();

        List<Entity> risen = nests(world);
        risen.removeAll(before);
        assertThat(risen.stream().map(nest -> nest.getComponent(Nest.class).kind))
                .containsExactlyInAnyOrder(
                        UnitKind.GRUNT, UnitKind.SPITTER, UnitKind.RUNNER, UnitKind.FROG, UnitKind.ANT, UnitKind.BRUTE);
        List<Entity> caches = caches(world);
        caches.removeAll(cachesBefore);
        assertThat(caches.stream().map(cache -> cache.getComponent(Pickup.class).kind))
                .containsExactlyInAnyOrder(
                        PickupKind.ARMOURY_SHOTGUN,
                        PickupKind.ARMOURY_RAILGUN,
                        PickupKind.ARMOURY_LASER,
                        PickupKind.ARMOURY_FLAMER);
        risen.addAll(caches);
        risen.addAll(bosses(world));
        Position entrance = world.engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first()
                .getComponent(Position.class);
        boolean[] reachable = Maps.reachableFrom(map, map.colAt(entrance.x), map.rowAt(entrance.y));
        for (int i = 0; i < risen.size(); i++) {
            Entity entity = risen.get(i);
            Position at = entity.getComponent(Position.class);
            float radius = entity.getComponent(Body.class).radius;
            assertThat(map.isClear(at.x, at.y, radius))
                    .as("clear of rock at %s,%s", at.x, at.y)
                    .isTrue();
            assertThat(reachable[map.rowAt(at.y) * map.cols() + map.colAt(at.x)])
                    .as("reachable at %s,%s", at.x, at.y)
                    .isTrue();
            for (int j = i + 1; j < risen.size(); j++) {
                Entity other = risen.get(j);
                Position there = other.getComponent(Position.class);
                float apart = (float) Math.hypot(there.x - at.x, there.y - at.y);
                assertThat(apart)
                        .as("room between %s,%s and %s,%s", at.x, at.y, there.x, there.y)
                        .isGreaterThan(radius + other.getComponent(Body.class).radius);
            }
        }
    }

    /** Every commander, stood just inside the keep's west gap. */
    private static void enterTheKeep(Simulation world) {
        TileMap map = world.map();
        float x = map.tileCentreX(KEEP_ENTRANCE_COL);
        float y = map.tileCentreY(KEEP_ENTRANCE_ROW);
        for (Entity commander :
                world.engine().getEntitiesFor(Family.all(Commander.class).get())) {
            commander.getComponent(Position.class).set(x, y);
            commander.getComponent(PreviousPosition.class).set(x, y);
        }
    }

    private static List<Entity> bosses(Simulation world) {
        List<Entity> bosses = new ArrayList<>();
        for (Entity entity :
                world.engine().getEntitiesFor(Family.all(Strain.class).get())) {
            if (entity.getComponent(Strain.class).kind == UnitKind.BOSS_GRUNT) {
                bosses.add(entity);
            }
        }
        return bosses;
    }

    /** Every gun lying on the field waiting to be picked up. */
    private static List<Entity> caches(Simulation world) {
        List<Entity> caches = new ArrayList<>();
        for (Entity entity :
                world.engine().getEntitiesFor(Family.all(Pickup.class).get())) {
            if (entity.getComponent(Pickup.class).kind.name().startsWith("ARMOURY_")) {
                caches.add(entity);
            }
        }
        return caches;
    }

    private static List<Entity> nests(Simulation world) {
        List<Entity> nests = new ArrayList<>();
        for (Entity entity :
                world.engine().getEntitiesFor(Family.all(Nest.class).get())) {
            nests.add(entity);
        }
        return nests;
    }

    /**
     * The map knows the biomes {@link #BIOME_END} draws, and a wall - standing on each of those columns -
     * belongs to the biome it lets you into: a nest wakes when you come through the gap, not from behind
     * the rock (G0062).
     */
    @Test
    void putsEachWallInTheBiomeToItsEastAndHasSevenBiomes() {
        TileMap map = world().map();

        for (int col = 0; col < map.cols(); col++) {
            for (int row = 0; row < map.rows(); row++) {
                assertThat(map.biomeAtTile(col, row)).as("tile %d,%d", col, row).isEqualTo(biomeOf(col));
            }
        }
        assertThat(map.biomeCount()).isEqualTo(BIOME_END.length);
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
