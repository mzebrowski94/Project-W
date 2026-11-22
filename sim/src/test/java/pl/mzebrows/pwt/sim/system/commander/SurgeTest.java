package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DASH_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SURGE_REACH_PER_RANK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SURGE_STUN_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Dash;
import pl.mzebrows.pwt.sim.component.Puff;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * A Rocketman's surge (G0108): on the tick a dash launches, the aliens within its reach of him, in a clear line, are
 * stunned by a harmless blow - and nobody on his side ever is.
 */
class SurgeTest {

    private static final float START_X = 33f;

    private static final float START_Y = 50f;

    /** Inside the first rank's reach, west of him: behind the way he dashes. */
    private static final float NEAR = SURGE_REACH_PER_RANK - 1f;

    /** Outside the first rank's reach and inside the second's. */
    private static final float FAR = SURGE_REACH_PER_RANK * 2 - 1f;

    /** Standing still, aimed east. */
    private static final CommanderInput STILL = new CommanderInput(0f, 0f, START_X + 50f, START_Y, false, false);

    /** Dashing east. */
    private static final CommanderInput DASH =
            new CommanderInput(1f, 0f, START_X + 50f, START_Y, false, false).dashing(true);

    @Test
    void stunsAnAlienInsideItsReachOnTheTickTheDashLaunches() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        Entity grunt = simulation.spawnEnemy(START_X - NEAR, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 1, 0);

        simulation.step(DASH);

        assertThat(dazed(grunt)).isTrue();
        assertThat(grunt.getComponent(Stunned.class).remainingTicks).isEqualTo(ticksIn(SURGE_STUN_SECONDS));
        assertThat(simulation.dashes(commander)).isEqualTo(DASH_MOST - 1);
    }

    @Test
    void sparesAnAlienOutsideItsReach() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y);
        Entity grunt = simulation.spawnEnemy(START_X - FAR, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 1, 0);

        simulation.step(DASH);

        assertThat(dazed(grunt)).isFalse();
    }

    @Test
    void reachesFartherAtRankTwo() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y);
        Entity grunt = simulation.spawnEnemy(START_X - FAR, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 2, 0);

        simulation.step(DASH);

        assertThat(dazed(grunt)).isTrue();
    }

    /** Rock between them stops it, as it stops a blast. */
    @Test
    void sparesAnAlienBehindRock() {
        Simulation simulation = new Simulation(
                config(), ground().rect(5, 0, 1, 20, Terrain.ROCK).build());
        simulation.spawnCommander(START_X, START_Y);
        Entity grunt = simulation.spawnEnemy(START_X - FAR, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 2, 0);

        simulation.step(DASH);

        assertThat(dazed(grunt)).isFalse();
    }

    @Test
    void neverStunsATrooperOrAnotherCommander() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y, 0);
        Entity other = simulation.spawnCommander(START_X, START_Y + NEAR, 1);
        Entity trooper = simulation.spawnTrooper(START_X - NEAR, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 2, 0);

        simulation.step(new PlayerInputs().set(0, DASH).set(1, STILL));

        assertThat(dazed(other)).isFalse();
        assertThat(dazed(trooper)).isFalse();
    }

    @Test
    void setsNothingOffOnAPressWithNoDashLeft() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(START_X, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 1, 0);
        Dash dash = commander.getComponent(Dash.class);
        dash.spent = DASH_MOST;
        dash.rechargeTicks = ticksIn(SURGE_STUN_SECONDS) * 10;
        Entity grunt = simulation.spawnEnemy(START_X - NEAR, START_Y);

        simulation.step(DASH);

        assertThat(dazed(grunt)).isFalse();
        assertThat(rings(simulation)).isEmpty();
    }

    @Test
    void setsNothingOffWithoutTheUpgrade() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y);
        Entity grunt = simulation.spawnEnemy(START_X - NEAR, START_Y);

        simulation.step(DASH);

        assertThat(dazed(grunt)).isFalse();
        assertThat(rings(simulation)).isEmpty();
    }

    /** Only the chooser's dash surges: Rocketman is personal. */
    @Test
    void setsNothingOffForAPlayerWhoDoesNotHoldIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y + 40f, 0);
        simulation.spawnCommander(START_X, START_Y, 1);
        Entity grunt = simulation.spawnEnemy(START_X - NEAR, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 2, 0);

        simulation.step(new PlayerInputs().set(0, STILL).set(1, DASH));

        assertThat(dazed(grunt)).isFalse();
    }

    /** A harmless blow that lands takes a shield, as any blow does (G0057): the shield breaks and the stun is spent on it. */
    @Test
    void breaksAnAliensShield() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y);
        Entity grunt = simulation.spawnEnemy(START_X - NEAR, START_Y);
        Shield shield = simulation.engine().createComponent(Shield.class);
        shield.raise();
        grunt.add(shield);
        hold(simulation, UpgradeKind.ROCKETMAN, 1, 0);

        simulation.step(DASH);

        assertThat(shield.up).isFalse();
        assertThat(dazed(grunt)).isFalse();
    }

    /** Where it went off, a ring as wide as its reach, drawn on the slam's circle. */
    @Test
    void leavesARingAsWideAsItsReachWhereItWentOff() {
        Simulation simulation = simulation();
        simulation.spawnCommander(START_X, START_Y);
        hold(simulation, UpgradeKind.ROCKETMAN, 2, 0);

        simulation.step(DASH);

        Appearance ring = ring(simulation).getComponent(Appearance.class);
        assertThat(ring.look).isEqualTo(Look.ROCKETMAN);
        assertThat(ring.burst).isEqualTo(SURGE_REACH_PER_RANK * 2f);
        assertThat(ring.puff).isZero();
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

    private static boolean dazed(Entity entity) {
        Stunned stunned = entity.getComponent(Stunned.class);
        return stunned.dazed && stunned.active();
    }

    private static List<Entity> rings(Simulation simulation) {
        List<Entity> rings = new ArrayList<>();
        for (Entity puff :
                simulation.engine().getEntitiesFor(Family.all(Puff.class).get())) {
            if (puff.getComponent(Puff.class).reach > 0f) {
                rings.add(puff);
            }
        }
        return rings;
    }

    private static Entity ring(Simulation simulation) {
        List<Entity> rings = rings(simulation);
        assertThat(rings).hasSize(1);
        return rings.getFirst();
    }
}
