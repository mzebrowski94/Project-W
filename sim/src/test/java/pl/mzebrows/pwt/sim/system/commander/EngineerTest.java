package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MUZZLE_GAP;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Sentry;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * Engineer (G0129): while the chooser holds it, his grenade key sets a sentry down at his feet instead of throwing,
 * for a grenade out of the same count and on the same wait; with none left, or on solid ground, it sets nothing down
 * and spends nothing. Personal: nobody else's key changes. Its second rank makes his sentries fire faster.
 */
class EngineerTest {

    private static final float AT_X = 30f;

    private static final float AT_Y = 52f;

    /** Where a sentry comes down: just in front of him, clear of his body by the muzzle gap. */
    private static final float SET_DOWN = COMMANDER_RADIUS + config().sentry().radius() + MUZZLE_GAP;

    @Test
    void setsASentryDownAtHisFeetInsteadOfThrowingAGrenade() {
        Simulation simulation = simulation();
        simulation.spawnCommander(AT_X, AT_Y);
        hold(simulation, UpgradeKind.ENGINEER, 1, 0);

        simulation.step(pressingTheKey(AT_X, AT_Y));

        assertThat(count(simulation, Sentry.class)).isOne();
        assertThat(count(simulation, Thrown.class)).isZero();
        Position at = sentry(simulation).getComponent(Position.class);
        assertThat(at.x).isCloseTo(AT_X + SET_DOWN, within(0.01f));
        assertThat(at.y).isCloseTo(AT_Y, within(0.01f));
    }

    /** One count, one most, one cache: a sentry is paid for out of his grenades. */
    @Test
    void spendsAGrenadeOnTheSentry() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);
        hold(simulation, UpgradeKind.ENGINEER, 1, 0);

        simulation.step(pressingTheKey(AT_X, AT_Y));

        assertThat(simulation.grenades(commander)).isEqualTo(GRENADE_MOST - 1);
        assertThat(sentry(simulation).getComponent(Sentry.class).slot).isZero();
    }

    @Test
    void setsNoSentryDownWithNoGrenadeLeft() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(AT_X, AT_Y);
        commander.getComponent(Thrower.class).spent = GRENADE_MOST;
        hold(simulation, UpgradeKind.ENGINEER, 1, 0);

        steps(simulation, 3, pressingTheKey(AT_X, AT_Y));

        assertThat(count(simulation, Sentry.class)).isZero();
    }

    /** Rock just in front of him: nothing is set down, and the grenade is still his. */
    @Test
    void setsNoSentryDownOnSolidGroundAndSpendsNothing() {
        TileMap map = ground().rect(6, 0, 1, 20, Terrain.ROCK).build();
        Simulation simulation = new Simulation(config(), map);
        float x = map.tileCentreX(5);
        float y = map.tileCentreY(10);
        Entity commander = simulation.spawnCommander(x, y);
        hold(simulation, UpgradeKind.ENGINEER, 1, 0);

        simulation.step(pressingTheKey(x, y));

        assertThat(count(simulation, Sentry.class)).isZero();
        assertThat(simulation.grenades(commander)).isEqualTo(GRENADE_MOST);
    }

    @Test
    void throwsAGrenadeAsEverWithoutIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(AT_X, AT_Y);

        simulation.step(pressingTheKey(AT_X, AT_Y));

        assertThat(count(simulation, Thrown.class)).isOne();
        assertThat(count(simulation, Sentry.class)).isZero();
    }

    /** Engineer is personal: the player beside him who does not hold it still throws. */
    @Test
    void leavesTheKeyOfAPlayerWhoDoesNotHoldItThrowing() {
        Simulation simulation = simulation();
        simulation.spawnCommander(AT_X, AT_Y + 30f, 0);
        simulation.spawnCommander(AT_X, AT_Y, 1);
        hold(simulation, UpgradeKind.ENGINEER, 2, 0);

        simulation.step(new PlayerInputs().set(0, standing()).set(1, pressingTheKey(AT_X, AT_Y)));

        assertThat(count(simulation, Thrown.class)).isOne();
        assertThat(count(simulation, Sentry.class)).isZero();
    }

    /** Held down, the key waits out his grenade wait between one sentry and the next, as between grenades. */
    @Test
    void waitsOutHisGrenadeWaitBetweenSentries() {
        Simulation simulation = simulation();
        simulation.spawnCommander(AT_X, AT_Y);
        hold(simulation, UpgradeKind.ENGINEER, 1, 0);

        steps(simulation, 3, pressingTheKey(AT_X, AT_Y));

        assertThat(count(simulation, Sentry.class)).isOne();
    }

    @Test
    void makesHisSentriesFireFasterAtItsSecondRank() {
        assertThat(cooldownOfHisSentry(2)).isLessThan(cooldownOfHisSentry(1));
    }

    // ---- helpers ----

    /** The longest wait its gun set itself between pulls, set down at this rank with an alien in its reach. */
    private static int cooldownOfHisSentry(int ranks) {
        Simulation simulation = harmless();
        simulation.spawnCommander(AT_X, AT_Y);
        hold(simulation, UpgradeKind.ENGINEER, ranks, 0);
        simulation.step(pressingTheKey(AT_X, AT_Y));
        Entity sentry = sentry(simulation);
        simulation.spawnEnemy(AT_X + SET_DOWN + simulation.config().sentry().range() * 0.5f, AT_Y);
        int longest = 0;
        for (int tick = 0; tick < ticksIn(1f); tick++) {
            simulation.step(standing());
            longest = Math.max(longest, sentry.getComponent(Weapon.class).remainingTicks);
        }
        assertThat(longest).isPositive();
        return longest;
    }

    /** Standing where he is, aimed east, the grenade key down. */
    private static CommanderInput pressingTheKey(float x, float y) {
        return new CommanderInput(0f, 0f, x + 20f, y, false, false, true, false, false, false);
    }

    private static CommanderInput standing() {
        return new CommanderInput(0f, 0f, AT_X + 20f, AT_Y, false, false);
    }

    private static Entity sentry(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Sentry.class).get())
                .first();
    }

    private static void hold(Simulation simulation, UpgradeKind kind, int ranks, int slot) {
        simulation
                .engine()
                .getEntitiesFor(Family.all(Upgrades.class).get())
                .first()
                .getComponent(Upgrades.class)
                .held
                .add(new Upgrades.Held(kind, slot, ranks));
    }
}
