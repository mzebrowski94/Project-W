package pl.mzebrows.pwt.sim.system.level;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CRATE_TROOPERS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESERVE_MAX;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESERVE_START;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SQUAD_SIZE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * What a crate of men actually becomes.
 *
 * <p>The pickup half of this is {@code PickupSystemTest}'s: a supply drop lands a level-scope
 * grant on the level entity. This is the other half - the grant becoming men standing at the rally
 * point - and the cap that keeps pillar 3 honest, because a field that can be topped up without limit
 * is not a resource. What the field has no room for goes into the reserve (G0055); the reserve's
 * own muster is {@code ReserveTest}'s.
 */
class ReinforcementSystemTest {

    private static final float X = 50f;
    private static final float Y = 50f;

    @Test
    void turnsARescuedTrooperIntoAManOnTheField() {
        Simulation simulation = world(config());
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        assertThat(count(simulation, Trooper.class)).isEqualTo(1);
    }

    /**
     * He comes onto the field at the rally point, as every man from outside the fight does (G0055) -
     * with no point taken, where the level started the side; not where the crate was.
     */
    @Test
    void putsHimDownAtTheRallyPoint() {
        Simulation simulation = world(config());
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        TileMap map = simulation.map();
        Position at = all(simulation).get(0).getComponent(Position.class);
        TileRect spawn = simulation.config().spawn().on(map);

        assertThat(spawn.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
    }

    /**
     * A crate is worth several men and they arrive a man a tick, so the queue drains and then stops.
     * A grant that kept paying out would fill the arena in a second.
     */
    @Test
    void spendsAGrantOnceRatherThanEveryTick() {
        Simulation simulation = world(config());
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        steps(simulation, 30);

        assertThat(count(simulation, Trooper.class)).isEqualTo(CRATE_TROOPERS);
        assertThat(simulation.level().pendingReinforcements).isZero();
    }

    /** One a tick, so the work this does per tick is the same whatever a crate was worth. */
    @Test
    void putsOneManDownPerTickRatherThanTheWholeCrateAtOnce() {
        Simulation simulation = world(config());
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();
        assertThat(count(simulation, Trooper.class)).isEqualTo(1);

        simulation.step();
        assertThat(count(simulation, Trooper.class)).isEqualTo(2);
    }

    /** Pillar 3: the field is a resource, so there is a ceiling, and what a crate has past it waits in reserve. */
    @Test
    void neverPutsMoreMenOnTheFieldThanTheSquadAllows() {
        SimConfig base = config();
        SimConfig capped = base.withTrooper(base.trooper().withSquadGrowth(1));
        Simulation simulation = world(capped);
        simulation.spawnCommander(X, Y);
        for (int i = 0; i < SQUAD_SIZE + 1; i++) {
            simulation.spawnTrooper(X + 20f + i * 3f, Y);
        }
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE + 1);
    }

    @Test
    void banksWhatTheFieldHasNoRoomForInTheReserve() {
        SimConfig base = config();
        Simulation simulation = world(base.withTrooper(base.trooper().withSquadGrowth(0)));
        for (int i = 0; i < SQUAD_SIZE; i++) {
            simulation.spawnTrooper(X + 20f + i * 3f, Y);
        }
        simulation.level().pendingReinforcements = CRATE_TROOPERS;

        simulation.step();

        assertThat(simulation.level().reserve).isEqualTo(RESERVE_START + CRATE_TROOPERS);
        assertThat(simulation.level().pendingReinforcements).isZero();
    }

    /** The reserve has a most too, and a crate's men past it are wasted rather than kept somewhere else. */
    @Test
    void wastesWhatTheReserveHasNoRoomFor() {
        SimConfig base = config();
        Simulation simulation = world(base.withTrooper(base.trooper().withSquadGrowth(0)));
        for (int i = 0; i < SQUAD_SIZE; i++) {
            simulation.spawnTrooper(X + 20f + i * 3f, Y);
        }
        simulation.level().reserve = RESERVE_MAX - 1;
        simulation.level().pendingReinforcements = CRATE_TROOPERS;

        simulation.step();

        assertThat(simulation.level().reserve).isEqualTo(RESERVE_MAX);
    }

    /**
     * A level with no headroom at all: a rescue then replaces a man you lost and never adds one. The
     * ceiling is the whole squad rather than the crate, so the same pickup is worth everything after
     * a bad fight and nothing after a clean one.
     */
    @Test
    void fillsAGapLeftByAManTheLevelLost() {
        SimConfig base = config();
        Simulation simulation = world(base.withTrooper(base.trooper().withSquadGrowth(0)));
        simulation.spawnCommander(X, Y);
        for (int i = 0; i < SQUAD_SIZE - 1; i++) {
            simulation.spawnTrooper(X + 20f + i * 3f, Y);
        }
        simulation.level().pendingReinforcements = 1;

        simulation.step();

        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE);
    }

    /** Two crates and one place left in the squad is one man, not two and not none. */
    @Test
    void takesAsManyAsTheSquadHasRoomForAndNoMore() {
        SimConfig base = config();
        Simulation simulation = world(base.withTrooper(base.trooper().withSquadGrowth(0)));
        simulation.spawnCommander(X, Y);
        for (int i = 0; i < SQUAD_SIZE - 1; i++) {
            simulation.spawnTrooper(X + 20f + i * 3f, Y);
        }
        simulation.level().pendingReinforcements = 2;

        simulation.step();

        assertThat(count(simulation, Trooper.class)).isEqualTo(SQUAD_SIZE);
    }

    /** The rally point is ground, not a man: a crate a trooper fetched while every commander is out still lands. */
    @Test
    void putsACratesMenDownWithNobodyStandingToFormUpOn() {
        Simulation simulation = world(config());
        simulation.level().pendingReinforcements = 1;

        simulation.step();

        assertThat(count(simulation, Trooper.class)).isEqualTo(1);
        assertThat(simulation.level().pendingReinforcements).isZero();
    }

    private static Simulation world(SimConfig config) {
        return on(config, ground().build());
    }

    private static ImmutableArray<Entity> all(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Trooper.class).get());
    }
}
