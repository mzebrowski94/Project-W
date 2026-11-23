package pl.mzebrows.pwt.sim.system.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TROOPER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A trooper a warden's bead wounds steps off its line (G0114): square to it, to the side he already stands on, as far
 * as the gun says - then goes back to what he was doing. An ordinary shot is not stepped from.
 */
class TrooperStepOffTest {

    /** The line the shots fly up, south to north. */
    private static final float LANE = 50f;

    private static final float MAN_Y = 60f;

    /** How far off the line each man stands: inside his body's reach of it, so the shot finds him. */
    private static final float OFF_THE_LINE = 0.4f;

    /** Where the shot starts, just short of him, so he has no time to wander before it lands. */
    private static final float MUZZLE_Y = MAN_Y - TROOPER_RADIUS - 1f;

    /** Long enough for the shot to land and any step to be walked. */
    private static final int WATCH = ticksIn(1.5f);

    @Test
    void stepsOffTheLineOfAWardensShotToTheSideHeStandsOn() {
        Simulation simulation = harmless();
        Entity east = standing(simulation, LANE + OFF_THE_LINE);
        Entity west = standing(simulation, LANE - 40f - OFF_THE_LINE);
        float stepOff = simulation.config().weapon(WeaponKind.WARDEN_SHOT).stepOff();
        float shot = simulation.config().weapon(WeaponKind.WARDEN_SHOT).projectileRadius();

        simulation.spawnProjectile(LANE, MUZZLE_Y, 0f, 1f, WeaponKind.WARDEN_SHOT, Side.ALIEN);
        simulation.spawnProjectile(LANE - 40f, MUZZLE_Y, 0f, 1f, WeaponKind.WARDEN_SHOT, Side.ALIEN);
        float farthestEast = 0f;
        float farthestWest = 0f;
        boolean stepped = false;
        for (int tick = 0; tick < WATCH; tick++) {
            simulation.step();
            farthestEast = Math.max(farthestEast, x(east) - LANE);
            farthestWest = Math.max(farthestWest, LANE - 40f - x(west));
            stepped |= east.getComponent(Trooper.class).mode == Trooper.Mode.STEP_OFF;
        }

        assertThat(stepOff).as("the warden's bead is stepped off from").isPositive();
        assertThat(unhurt(east)).as("wounded by it").isFalse();
        assertThat(unhurt(west)).as("wounded by it").isFalse();
        assertThat(stepped).isTrue();
        assertThat(farthestEast).as("east of the line, off to the east").isGreaterThanOrEqualTo(TROOPER_RADIUS + shot);
        assertThat(farthestWest).as("west of the line, off to the west").isGreaterThanOrEqualTo(TROOPER_RADIUS + shot);
        assertThat(east.getComponent(Trooper.class).mode)
                .as("back to what he was doing")
                .isNotEqualTo(Trooper.Mode.STEP_OFF);
    }

    @Test
    void doesNotStepOffAnOrdinaryShot() {
        Simulation simulation = harmless();
        Entity man = standing(simulation, LANE + OFF_THE_LINE);

        simulation.spawnProjectile(LANE, MUZZLE_Y, 0f, 1f, WeaponKind.SIDEARM, Side.ALIEN);
        boolean stepped = false;
        for (int tick = 0; tick < WATCH; tick++) {
            simulation.step();
            stepped |= man.getComponent(Trooper.class).mode == Trooper.Mode.STEP_OFF;
        }

        assertThat(simulation.config().weapon(WeaponKind.SIDEARM).stepOff()).isZero();
        assertThat(unhurt(man)).as("wounded by it").isFalse();
        assertThat(stepped).isFalse();
        assertThat(x(man) - LANE).as("still standing where he was").isLessThan(TROOPER_RADIUS);
    }

    /** A trooper standing on a point of his own, so he is still until something moves him. */
    private static Entity standing(Simulation simulation, float x) {
        simulation.spawnControlPoint(x, MAN_Y);
        return simulation.spawnTrooper(x, MAN_Y);
    }

    private static float x(Entity man) {
        return man.getComponent(Position.class).x;
    }
}
