package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A duration in the config is seconds; the simulation counts whole ticks, converted once, so a number
 * means what it says. Counting seconds down by a float tick left a residue on 71 of the shipped
 * durations and ran each a tick long - the flamer fired every sixth tick instead of every fifth.
 */
class TicksTest {

    private static final float TICK = 1f / 60f;

    private static final float MIDDLE = 50f;

    private static final int SECOND = 60;

    @Test
    void roundsADurationToTheNearestWholeTick() {
        assertThat(Ticks.of(0.1f, TICK)).isEqualTo(6);
        assertThat(Ticks.of(0.09f, TICK)).isEqualTo(5);
        assertThat(Ticks.of(1.1f, TICK)).isEqualTo(66);
    }

    @Test
    void keepsZeroAsOff() {
        assertThat(Ticks.of(0f, TICK)).isZero();
    }

    /** A duration that was asked for is never rounded away to nothing. */
    @Test
    void lastsAtLeastOneTickForAnythingThatLastsAtAll() {
        assertThat(Ticks.of(0.001f, TICK)).isEqualTo(1);
    }

    @Test
    void firesAGunExactlyAsOftenAsItsCooldownSays() {
        Simulation simulation = new Simulation(SimConfig.defaults());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawner().applyWeapon(commander, WeaponKind.FLAMER);
        int cooldown = Ticks.of(SimConfig.defaults().weapon(WeaponKind.FLAMER).cooldownSeconds(), TICK);
        ShotCounter shots = new ShotCounter();
        simulation.engine().addEntityListener(Family.all(Projectile.class).get(), shots);
        CommanderInput firing = new CommanderInput(0f, 0f, MIDDLE + 10f, MIDDLE, true, false);

        for (int tick = 0; tick < SECOND; tick++) {
            shots.tick = tick;
            simulation.step(firing);
        }

        assertThat(cooldown).isEqualTo(5);
        assertThat(shots.firingTicks).isEqualTo(SECOND / cooldown);
    }

    /** Counts ticks on which a gun fired, however many pellets it threw. */
    private static final class ShotCounter implements EntityListener {
        int tick;
        int firingTicks;
        int lastFired = -1;

        @Override
        public void entityAdded(Entity entity) {
            if (lastFired != tick) {
                lastFired = tick;
                firingTicks++;
            }
        }

        @Override
        public void entityRemoved(Entity entity) {}
    }
}
