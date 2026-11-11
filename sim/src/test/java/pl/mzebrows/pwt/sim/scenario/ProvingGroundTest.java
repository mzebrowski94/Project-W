package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeView;

/** What a proving ground setup builds (G0060): the world is the setup, and nothing else. */
class ProvingGroundTest {

    @Test
    void putsHisSideOnTheLeftAndTheChosenStrainOnTheRight() {
        Simulation simulation =
                world(ProvingSetup.first().withStrain(UnitKind.SPITTER).withCount(5));
        float middle = simulation.map().worldWidth() * 0.5f;

        assertThat(position(commander(simulation)).x).isLessThan(middle);
        ImmutableArray<Entity> aliens = all(simulation, Strain.class);
        assertThat(aliens).hasSize(5);
        for (Entity alien : aliens) {
            assertThat(alien.getComponent(Strain.class).kind).isEqualTo(UnitKind.SPITTER);
            assertThat(position(alien).x).isGreaterThan(middle);
        }
    }

    @Test
    void armsHimWithTheChosenGun() {
        Simulation simulation = world(ProvingSetup.first().withGun(WeaponKind.LASER));

        assertThat(commander(simulation).getComponent(Weapon.class).kind).isEqualTo(WeaponKind.LASER);
    }

    @Test
    void sendsTheStrainOutOfANestInsteadWhenAsked() {
        Simulation simulation =
                world(ProvingSetup.first().withStrain(UnitKind.FROG).withNest(true));

        assertThat(all(simulation, Strain.class)).isEmpty();
        ImmutableArray<Entity> nests = all(simulation, Nest.class);
        assertThat(nests).hasSize(1);
        assertThat(nests.first().getComponent(Nest.class).kind).isEqualTo(UnitKind.FROG);
        assertThat(position(nests.first()).x).isGreaterThan(simulation.map().worldWidth() * 0.5f);
    }

    /** A strain that cannot walk is one, standing, however many were asked for - and never in a hole. */
    @Test
    void putsDownOneOfARootedStrainWhateverTheCount() {
        assertThat(all(
                        world(ProvingSetup.first()
                                .withStrain(UnitKind.BOSS_GRUNT)
                                .withCount(10)),
                        Strain.class))
                .hasSize(1);
        Simulation nested =
                world(ProvingSetup.first().withStrain(UnitKind.BOSS_GRUNT).withNest(true));
        assertThat(all(nested, Nest.class)).isEmpty();
        assertThat(all(nested, Strain.class)).hasSize(1);
    }

    @Test
    void bringsTheSquadOnlyWhenAsked() {
        assertThat(all(world(ProvingSetup.first()), Trooper.class)).isEmpty();
        assertThat(all(world(ProvingSetup.first().withSquad(true)), Trooper.class))
                .hasSize(SimConfig.defaults().trooper().squadSize());
    }

    @ParameterizedTest
    @EnumSource(value = ProvingSetup.Others.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void standsThreeOfTheOthersBetweenHimAndTheMiddle(ProvingSetup.Others others) {
        Simulation simulation = world(ProvingSetup.first().withOthers(others));
        Class<? extends Component> marker =
                switch (others) {
                    case TURRETS -> Turret.class;
                    case MINES -> Mine.class;
                    case CRATES -> Crate.class;
                    default -> Pickup.class;
                };
        float him = position(commander(simulation)).x;
        float middle = simulation.map().worldWidth() * 0.5f;

        ImmutableArray<Entity> placed = all(simulation, marker);
        assertThat(placed).hasSize(3);
        for (Entity entity : placed) {
            assertThat(position(entity).x).isBetween(him, middle);
        }
    }

    @Test
    void standsNoOthersUnlessAsked() {
        Simulation simulation = world(ProvingSetup.first());

        assertThat(all(simulation, Turret.class)).isEmpty();
        assertThat(all(simulation, Mine.class)).isEmpty();
        assertThat(all(simulation, Pickup.class)).isEmpty();
    }

    /**
     * Ranks are in the ledger from the first tick, held as a draft would hold them: a shared upgrade by
     * the side, a personal one by slot zero, an adaptation by the swarm - and never past its most.
     */
    @Test
    void holdsTheChosenRanksFromTheFirstTick() {
        Simulation simulation = world(ProvingSetup.first()
                .withRank(UpgradeKind.MARKSMEN, 2)
                .withRank(UpgradeKind.SELF_TREATMENT, 1)
                .withRank(UpgradeKind.RAPID_SPIT, 99));
        UpgradeView view = simulation.upgradeView();

        assertThat(view.heldCount()).isEqualTo(3);
        assertThat(view.heldKind(0)).isEqualTo(UpgradeKind.MARKSMEN);
        assertThat(view.heldSlot(0)).isEqualTo(UpgradeView.SHARED);
        assertThat(view.heldRanks(0)).isEqualTo(2);
        assertThat(view.heldKind(1)).isEqualTo(UpgradeKind.SELF_TREATMENT);
        assertThat(view.heldSlot(1)).isZero();
        assertThat(view.heldKind(2)).isEqualTo(UpgradeKind.RAPID_SPIT);
        assertThat(view.heldAlien(2)).isTrue();
        assertThat(view.heldRanks(2)).isEqualTo(view.ranksOf(UpgradeKind.RAPID_SPIT));
    }

    /** Practice earns nothing (G0060) - not even the boss, which earns a draft everywhere else. */
    @Test
    void earnsNoDraftForKillingTheBoss() {
        Simulation simulation = world(ProvingSetup.first().withStrain(UnitKind.BOSS_GRUNT));
        Entity boss = all(simulation, Strain.class).first();

        boss.getComponent(Damage.class).land(Integer.MAX_VALUE / 2, Harm.KILL);
        for (int tick = 0; tick < 10; tick++) {
            simulation.step();
        }

        assertThat(Life.dying(boss)).isTrue();
        assertThat(simulation.upgradeView().draftsOf(0)).isZero();
    }

    @Test
    void laysARoundPatchOfSandRingedByThicketWithRockOutside() {
        TileMap map = world(ProvingSetup.first()).map();
        int middle = map.cols() / 2;

        assertThat(map.terrainAtTile(0, 0)).isEqualTo(Terrain.ROCK);
        assertThat(map.terrainAtTile(map.cols() - 1, map.rows() - 1)).isEqualTo(Terrain.ROCK);
        // Walking in from the right-hand edge on the middle row: rock, then thicket, then sand.
        int col = map.cols() - 1;
        while (map.terrainAtTile(col, middle) == Terrain.ROCK) {
            col--;
        }
        int rim = col;
        assertThat(map.terrainAtTile(col, middle)).isEqualTo(Terrain.THICKET);
        while (map.terrainAtTile(col, middle) == Terrain.THICKET) {
            col--;
        }
        assertThat(map.terrainAtTile(col, middle)).isEqualTo(Terrain.SAND);
        // Round, not square: the disc's diagonal reaches rock long before the corner does.
        int diagonal = middle + (rim - middle) * 3 / 4 + 1;
        assertThat(map.terrainAtTile(diagonal, diagonal)).isEqualTo(Terrain.ROCK);
    }

    /** Rock to fight round: a few lone tiles and a few clumps, inside the sand, never sealing anything off. */
    @Test
    void laysLoneRocksAndClumpsInsideTheSand() {
        TileMap map = world(ProvingSetup.first()).map();
        int lone = 0;
        int clumped = 0;
        for (int row = 1; row < map.rows() - 1; row++) {
            for (int col = 1; col < map.cols() - 1; col++) {
                if (map.terrainAtTile(col, row) != Terrain.ROCK || !surroundedByGround(map, col, row)) {
                    continue;
                }
                if (rockNeighbours(map, col, row) == 0) {
                    lone++;
                } else {
                    clumped++;
                }
            }
        }

        assertThat(lone).isPositive();
        assertThat(clumped).isPositive();
    }

    /** Rock inside the disc: some tile near it, in each direction, is sand - not the rim's own rock. */
    private static boolean surroundedByGround(TileMap map, int col, int row) {
        int middle = map.cols() / 2;
        float dx = col + 0.5f - middle;
        float dy = row + 0.5f - middle;
        float rim = middle - 1;
        return dx * dx + dy * dy < (rim - 4) * (rim - 4);
    }

    private static int rockNeighbours(TileMap map, int col, int row) {
        int count = 0;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] step : steps) {
            if (map.terrainAtTile(col + step[0], row + step[1]) == Terrain.ROCK) {
                count++;
            }
        }
        return count;
    }

    static Simulation world(ProvingSetup setup) {
        return Scenarios.world(Scenarios.provingGround(setup), SimConfig.defaults());
    }

    private static Entity commander(Simulation simulation) {
        return all(simulation, Commander.class).first();
    }

    private static Position position(Entity entity) {
        return entity.getComponent(Position.class);
    }

    private static ImmutableArray<Entity> all(Simulation simulation, Class<? extends Component> type) {
        return simulation.engine().getEntitiesFor(Family.all(type).get());
    }
}
