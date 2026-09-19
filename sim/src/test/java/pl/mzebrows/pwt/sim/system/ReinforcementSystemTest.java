package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CRATE_TROOPERS;
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
 * What a rescued trooper actually becomes.
 *
 * <p>The pickup half of this is {@code PickupSystemTest}'s: a supply drop lands a level-scope
 * grant on the level entity. This is the other half - the grant becoming a man standing beside the
 * commander - and the cap that keeps pillar 3 honest, because a squad that can be topped up without
 * limit is not a resource.
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

    /** He forms up on the commander, not at the edge of the level and not where the crate was. */
    @Test
    void putsHimDownBesideTheCommander() {
        Simulation simulation = world(config());
        simulation.spawnCommander(X, Y);
        simulation.spawnPickup(PickupKind.SUPPLY_DROP, X, Y);

        simulation.step();

        TileMap map = simulation.map();
        Position at = all(simulation).get(0).getComponent(Position.class);
        TileRect beside =
                TileRect.around(map, X, Y, simulation.config().trooper().reinforcementRadius());

        assertThat(beside.contains(map.colAt(at.x), map.rowAt(at.y))).isTrue();
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

    /** Pillar 3: the squad is a resource, so there is a ceiling and reaching it wastes the crate. */
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

    /**
     * Nobody standing is nobody to form up on, and the men are still owed. Held rather than lost:
     * a trooper reaching a crate while the commander is dead would otherwise waste it, and the wait
     * is the couple of seconds it takes him to come back rather than a balance carried anywhere.
     */
    @Test
    void keepsAReinforcementOwedWhileNobodyIsStandingToFormUpOn() {
        Simulation simulation = world(config());
        simulation.level().pendingReinforcements = 1;

        simulation.step();

        assertThat(count(simulation, Trooper.class)).isZero();
        assertThat(simulation.level().pendingReinforcements).isEqualTo(1);
    }

    @Test
    void deliversWhatItOwedOnceSomebodyIsStandingAgain() {
        Simulation simulation = world(config());
        simulation.level().pendingReinforcements = 1;
        simulation.step();

        simulation.spawnCommander(X, Y);
        simulation.step();

        assertThat(count(simulation, Trooper.class)).isEqualTo(1);
    }

    private static Simulation world(SimConfig config) {
        return on(config, ground().build());
    }

    private static ImmutableArray<Entity> all(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Trooper.class).get());
    }
}
