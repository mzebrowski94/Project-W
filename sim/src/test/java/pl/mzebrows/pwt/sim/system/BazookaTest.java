package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BAZOOKA_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BAZOOKA_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The bazooka: one rocket, then the wait for the gun to cool, a kick back from every pull, and a blast
 * where the rocket stops - on nobody's side, so it wounds a man caught in it as a grenade does.
 */
class BazookaTest {

    private static final float X = 30f;
    private static final float Y = 50f;

    /** Aim well to his right, along the lane. */
    private static final CommanderInput FIRING = new CommanderInput(0f, 0f, X + 30f, Y, true, false);

    /** Long enough for the kick to have thrown him, short enough that the rocket is still flying. */
    private static final int KICKED = 6;

    // ---- the kick ----

    @Test
    void throwsTheManFiringItBackFromWhereHeAimed() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation, WeaponKind.BAZOOKA);

        simulation.step(FIRING);
        steps(simulation, KICKED);

        assertThat(commander.getComponent(Position.class).x).isLessThan(X);
        assertThat(commander.getComponent(Position.class).y).isEqualTo(Y);
    }

    @Test
    void leavesAManFiringAGunThatNamesNoKickWhereHeStood() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation, WeaponKind.SIDEARM);

        simulation.step(FIRING);
        steps(simulation, KICKED);

        assertThat(commander.getComponent(Position.class).x).isEqualTo(X);
    }

    // ---- one rocket, then the wait ----

    @Test
    void stopsItselfWithTheOneRocketItFires() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation, WeaponKind.BAZOOKA);

        steps(simulation, KICKED, FIRING);

        assertThat(commander.getComponent(Heat.class).locked).isTrue();
        assertThat(count(simulation, Projectile.class)).isOne();
    }

    // ---- the blast ----

    @Test
    void goesOffAtTheEndOfItsReachWithNothingInTheWay() {
        Simulation simulation = simulation();
        simulation.spawnProjectile(X, Y, 1f, 0f, WeaponKind.BAZOOKA, Side.PLAYER);

        steps(simulation, untilSpent(simulation));

        assertThat(count(simulation, Blast.class)).isOne();
        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /** As a grenade's does, and unlike a barrel's: the gun he fires is not a way to put himself down. */
    @Test
    void woundsAManCaughtInItsBlastRatherThanPuttingHimDown() {
        Simulation simulation = simulation();
        Entity bystander = simulation.spawnCommander(X + BAZOOKA_RANGE, Y + BAZOOKA_BLAST_RADIUS * 0.5f);
        simulation.spawnProjectile(X, Y, 1f, 0f, WeaponKind.BAZOOKA, Side.PLAYER);

        steps(simulation, untilSpent(simulation));

        assertThat(bystander.getComponent(Vitality.class).wounds).isEqualTo(1);
        assertThat(bystander.getComponent(Vitality.class).begun).isFalse();
    }

    private static Entity holding(Simulation simulation, WeaponKind gun) {
        Entity commander = simulation.spawnCommander(X, Y);
        commander
                .getComponent(Weapon.class)
                .set(gun, simulation.config().weapon(gun), simulation.config().tickSeconds());
        return commander;
    }

    /** The whole of a rocket's flight, and a few ticks for its blast to land. */
    private static int untilSpent(Simulation simulation) {
        return ticksIn(BAZOOKA_RANGE
                        / simulation.config().weapon(WeaponKind.BAZOOKA).projectileSpeed())
                + 3;
    }
}
