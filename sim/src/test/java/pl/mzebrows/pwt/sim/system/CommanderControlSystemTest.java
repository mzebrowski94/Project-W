package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TICK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

class CommanderControlSystemTest {

    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);

    @Test
    void movesTheCommanderInTheDirectionItIsAskedTo() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(1f, 0f, 0f, 0f, false));

        Position position = commander.getComponent(Position.class);
        assertThat(position.x).isCloseTo(50f + COMMANDER_SPEED * TICK, TOLERANCE);
        assertThat(position.y).isCloseTo(50f, TOLERANCE);
    }

    @Test
    void doesNotTravelFasterDiagonallyThanStraight() {
        Simulation straight = simulation();
        Entity straightCommander = straight.spawnCommander(50f, 50f);
        Simulation diagonal = simulation();
        Entity diagonalCommander = diagonal.spawnCommander(50f, 50f);

        straight.step(new CommanderInput(1f, 0f, 0f, 0f, false));
        diagonal.step(new CommanderInput(1f, 1f, 0f, 0f, false));

        assertThat(travelled(diagonalCommander)).isCloseTo(travelled(straightCommander), TOLERANCE);
    }

    @Test
    void firesAProjectileWhileTheFireInputIsHeld() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0f, 0f, 60f, 50f, true));

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    @Test
    void firesNothingWhileTheFireInputIsReleased() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0f, 0f, 60f, 50f, false));

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void waitsOutTheCooldownBeforeFiringAgain() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        CommanderInput firing = new CommanderInput(0f, 0f, 60f, 50f, true);

        for (int i = 0; i < ticksIn(COOLDOWN) - 2; i++) {
            simulation.step(firing);
        }

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    @Test
    void firesAgainOnceTheCooldownHasElapsed() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        CommanderInput firing = new CommanderInput(0f, 0f, 60f, 50f, true);

        for (int i = 0; i < ticksIn(COOLDOWN) + 2; i++) {
            simulation.step(firing);
        }

        assertThat(count(simulation, Projectile.class)).isEqualTo(2);
    }

    @Test
    void sendsProjectilesTowardsWhereTheCommanderIsAiming() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0f, 0f, 50f, 90f, true));

        Position projectile = firstProjectile(simulation).getComponent(Position.class);
        assertThat(projectile.y).isGreaterThan(50f);
        assertThat(projectile.x).isCloseTo(50f, TOLERANCE);
    }

    @Test
    void firesWhereItAimsNoMatterWhereItIsWalking() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(-1f, 0f, 90f, 50f, true));

        Position projectile = firstProjectile(simulation).getComponent(Position.class);
        assertThat(projectile.x).isGreaterThan(50f);
    }

    @Test
    void firesNothingWhenAimingAtItsOwnFeet() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0f, 0f, 50f, 50f, true));

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void movesAtPartOfItsSpeedWhenAskedForPartOfADirection() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(new CommanderInput(0.5f, 0f, 0f, 0f, false));

        assertThat(travelled(commander)).isCloseTo(COMMANDER_SPEED * TICK * 0.5f, TOLERANCE);
    }

    private static int ticksIn(float seconds) {
        return Math.round(seconds / TICK);
    }

    private static float travelled(Entity commander) {
        Position position = commander.getComponent(Position.class);
        float dx = position.x - 50f;
        float dy = position.y - 50f;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Entity firstProjectile(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Projectile.class).get())
                .first();
    }
}
