package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A shot hits what it passes through between two ticks, however fast it flies. Tested only where it
 * stood at the end of each tick, the laser - over three world units a tick - went clean through a
 * barrel one shot in five, and more often the further off-centre it was aimed.
 */
class SweptShotTest {

    private static final float BARREL_X = 80f;

    private static final float LANE = 45f;

    /** Far enough back that the shot is in its stride before it arrives. */
    private static final float RUN_UP = 20f;

    /** Long enough for the laser to cross the barrel and be well past it. */
    private static final int FLIGHT = 30;

    /** Offsets inside what the barrel and the beam's own width can touch. */
    @ParameterizedTest(name = "{0} off centre")
    @ValueSource(floats = {0f, 0.4f, 0.8f, 1.2f})
    void hitsWhatAFastShotPassesThroughWhereverItStandsWhenTheTickEnds(float offCentre) {
        SimConfig config = SimConfig.defaults();
        float stride = config.weapon(WeaponKind.LASER).projectileSpeed() * config.tickSeconds();
        int phases = 12;
        int hits = 0;
        for (int phase = 0; phase < phases; phase++) {
            Simulation simulation = new Simulation(config);
            Entity barrel = simulation.spawnBarrel(BARREL_X, LANE);
            float startX = BARREL_X - RUN_UP - stride * phase / phases;
            simulation.spawnProjectile(startX, LANE + offCentre, 1f, 0f, WeaponKind.LASER, Side.PLAYER);

            if (struckWithin(simulation, barrel)) {
                hits++;
            }
        }

        assertThat(hits).isEqualTo(phases);
    }

    @ParameterizedTest(name = "{0} off centre")
    @ValueSource(floats = {2f, 4f})
    void stillMissesWhatItOnlyPassesBeside(float offCentre) {
        Simulation simulation = new Simulation(SimConfig.defaults());
        Entity barrel = simulation.spawnBarrel(BARREL_X, LANE);
        simulation.spawnProjectile(BARREL_X - RUN_UP, LANE + offCentre, 1f, 0f, WeaponKind.LASER, Side.PLAYER);

        assertThat(struckWithin(simulation, barrel)).isFalse();
    }

    private static boolean struckWithin(Simulation simulation, Entity barrel) {
        for (int tick = 0; tick < FLIGHT; tick++) {
            simulation.step();
            Health health = barrel.getComponent(Health.class);
            if (health == null || health.current < health.max) {
                return true;
            }
        }
        return false;
    }
}
