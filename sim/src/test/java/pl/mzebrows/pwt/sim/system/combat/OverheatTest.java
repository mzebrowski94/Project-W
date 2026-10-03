package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.HEAT_RESUME_BELOW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TURRET_HEAT_PER_SHOT;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A gun that can be fired faster than it can be fired safely. The heat is the player's to manage -
 * it is the one thing on the field that fills up because of something he did rather than something
 * being done to him - and running it into the stop costs him the gun until it has cooled well back
 * down, not just until it has stopped being full.
 */
class OverheatTest {

    private static final float MIDDLE = 50f;

    private static final float BESIDE = 4f;

    private static final float AHEAD = 10f;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    /** Long enough that the gun has locked itself out, short enough that nothing has left the arena. */
    private static final int UNTIL_LOCKED = 30;

    private static final CommanderInput STILL = CommanderInput.IDLE;

    private static final CommanderInput PRESSING = STILL.acting(true);

    @Test
    void heatsUpWithEveryShotTheTurretFires() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        simulation.step(firing());

        assertThat(turret.getComponent(Heat.class).value).isCloseTo(TURRET_HEAT_PER_SHOT, CLOSE);
    }

    @Test
    void stopsFiringOnceItIsFull() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, UNTIL_LOCKED, firing());
        int fired = count(simulation, Projectile.class);
        steps(simulation, 6, firing());

        assertThat(turret.getComponent(Heat.class).locked).isTrue();
        assertThat(count(simulation, Projectile.class)).isEqualTo(fired);
    }

    /**
     * The hysteresis is the mechanic. A gun that came back the moment it stopped being full would be
     * a gun the player taps rather than one he has to stop using.
     */
    @Test
    void staysLockedOutUntilItHasCooledWellBelowFull() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, UNTIL_LOCKED, firing());
        Heat heat = turret.getComponent(Heat.class);
        steps(simulation, ticksIn(0.2f), STILL);
        boolean lockedWhileStillHot = heat.locked;
        steps(simulation, ticksIn(2f), STILL);

        assertThat(lockedWhileStillHot).isTrue();
        assertThat(heat.value).isLessThan(HEAT_RESUME_BELOW);
        assertThat(heat.locked).isFalse();
    }

    @Test
    void coolsWhileNobodyIsFiringIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        simulation.step(firing());
        float hot = turret.getComponent(Heat.class).value;
        steps(simulation, ticksIn(0.25f), STILL);

        assertThat(turret.getComponent(Heat.class).value).isLessThan(hot);
    }

    /** 4a: an abandoned gun goes on cooling, so taking your hands off it is not a way of hiding. */
    @Test
    void goesOnCoolingWhileNobodyIsOnIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, UNTIL_LOCKED, firing());
        simulation.step(STILL);
        simulation.step(PRESSING);
        float whenHeLeft = turret.getComponent(Heat.class).value;
        steps(simulation, ticksIn(1f), STILL);

        assertThat(turret.getComponent(Heat.class).value).isLessThan(whenHeLeft);
    }

    /** 4a again, from the other side: the heat belongs to the gun, so getting back on inherits it. */
    @Test
    void keepsWhatItHadWhenSomebodyGetsBackOn() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, UNTIL_LOCKED, firing());
        simulation.step(STILL);
        simulation.step(PRESSING);
        simulation.step(STILL);
        simulation.step(PRESSING);

        assertThat(turret.getComponent(Heat.class).locked).isTrue();
    }

    @Test
    void showsHowHotItIsAndWhetherItHasStopped() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity turret = simulation.spawnTurret(MIDDLE + BESIDE, MIDDLE);

        simulation.step(PRESSING);
        steps(simulation, UNTIL_LOCKED, firing());

        Appearance appearance = turret.getComponent(Appearance.class);
        assertThat(appearance.heat).isGreaterThan(HEAT_RESUME_BELOW);
        assertThat(appearance.overheated).isTrue();
    }

    @Test
    void leavesAGunThatNamesNoHeatCold() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        steps(simulation, ticksIn(1f), firing());

        assertThat(count(simulation, Projectile.class)).isPositive();
        assertThat(commander.getComponent(Heat.class).value).isCloseTo(0f, CLOSE);
    }

    /** It is a property of the gun, not of the mount: the railgun runs hot in his hands too. */
    @Test
    void heatsACarriedGunToo() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, MIDDLE, MIDDLE);

        simulation.step(STILL);
        simulation.step(firing());

        assertThat(commander.getComponent(Heat.class).value).isPositive();
    }

    /**
     * <strong>The bar belongs to the gun, not to the man carrying it</strong> (G0025). A gun from a crate
     * is a gun nobody has fired, so a man who ran his last one into the stop picks up a cold one - the
     * stop was the other gun's, and it went with it.
     */
    @Test
    void handsAManWhoseLastGunStoppedItselfAColdOneFromACrate() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Heat heat = commander.getComponent(Heat.class);
        heat.value = 1f;
        heat.locked = true;

        simulation.spawnPickup(PickupKind.ARMOURY_RAILGUN, MIDDLE, MIDDLE);
        simulation.step(STILL);

        assertThat(heat.locked).isFalse();
        assertThat(heat.value).isCloseTo(0f, CLOSE);
        assertThat(commander.getComponent(Appearance.class).overheated).isFalse();
    }

    private static CommanderInput firing() {
        return new CommanderInput(0f, 0f, MIDDLE + BESIDE + AHEAD, MIDDLE, true, false);
    }
}
