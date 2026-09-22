package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LASER_TRAIL;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * What a beam publishes for the renderer.
 *
 * <p>Forty small shots a second are read as forty small shots unless the dots are joined, and the
 * beam is the whole of what the laser <em>is</em> (pillar 5). So a shot says where it came from and
 * the renderer draws the line between the two ends.
 *
 * <p><strong>Every point on that line is on the path a real simulated shot actually took.</strong>
 * These tests are the guard on that sentence: the line starts at the muzzle, it is never longer than
 * the gun allows, it ends where the shots end, and everything else in the game still publishes
 * nothing and is drawn as a dot. Nothing here is a hitscan and nothing is invented - the no-hitscan
 * rows are untouched, which is the point of publishing a fact rather than letting the renderer draw
 * a line of its own.
 */
class BeamTest {

    private static final float MUZZLE_X = 5f;
    private static final float LANE_Y = 50f;

    /** Short enough that a shot at sixty a second has not yet flown its whole trail. */
    private static final int WHILE_IT_IS_STILL_SHORT = 20;

    /** Long enough that it has flown well past it. */
    private static final int UNTIL_IT_IS_LONGER = 55;

    private static final Offset<Float> CLOSE = Offset.offset(1e-3f);

    /** Down the lane, with room in front of it for the shot to be in flight. */
    private static final float FIRST_X = 20f;

    /** Well clear of the first, and well inside the arena. */
    private static final float BEHIND_IT = 10f;

    /**
     * Long enough that a shot which had survived the first body would be well past the second.
     *
     * <p>The fixture's shots travel sixty units a second, so fifteen ticks reach the first body and
     * twenty-five reach the second. The fixture's pierce window is three units, far shorter than the
     * ten between them, so a gun that genuinely pierced would hit both - which is what makes this
     * test able to fail.
     */
    private static final int UNTIL_IT_WOULD_HAVE_PASSED = 25;

    @Test
    void drawsItBackToTheMuzzleWhileItIsStillCloseToIt() {
        Simulation simulation = new Simulation(config());
        Entity shot = beam(simulation);

        steps(simulation, WHILE_IT_IS_STILL_SHORT);

        Appearance appearance = shot.getComponent(Appearance.class);
        assertThat(appearance.streaks()).isTrue();
        assertThat(appearance.trailX).isCloseTo(MUZZLE_X, CLOSE);
        assertThat(appearance.trailY).isCloseTo(LANE_Y, CLOSE);
    }

    /**
     * A line the length of the whole level would be a gun with a reach it does not have. The trail
     * is a drawing number on the gun, and past it the line stops following the shot back.
     */
    @Test
    void keepsItNoLongerThanTheGunAllows() {
        Simulation simulation = new Simulation(config());
        Entity shot = beam(simulation);

        steps(simulation, UNTIL_IT_IS_LONGER);

        Position at = shot.getComponent(Position.class);
        Appearance appearance = shot.getComponent(Appearance.class);
        assertThat(at.x - appearance.trailX).isCloseTo(LASER_TRAIL, CLOSE);
        assertThat(appearance.trailX).isGreaterThan(MUZZLE_X);
    }

    /**
     * <strong>The line ends on the first thing it touches, because the shot does.</strong> This is
     * the whole of why the beam is drawn from the shots rather than as a line of its own: the gun
     * stopped piercing and the picture followed it without a word being said to the renderer.
     *
     * <p>It pierced once, and only appeared to. The window that stops one shot hitting one body
     * twice is a length of flight, and at this speed that length was several bodies deep - so the
     * line was drawn through the whole crowd and exactly one of them was ever hit.
     */
    @Test
    void stopsOnTheFirstThingItTouchesAndLeavesWhatIsBehindIt() {
        Simulation simulation = new Simulation(config());
        Entity first = simulation.spawnEnemy(FIRST_X, LANE_Y);
        Entity behind = simulation.spawnEnemy(FIRST_X + BEHIND_IT, LANE_Y);
        beam(simulation);

        steps(simulation, UNTIL_IT_WOULD_HAVE_PASSED);

        assertThat(first.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
        assertThat(behind.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    /** Every other gun in the game publishes nothing at all and is drawn as the dot it is. */
    @Test
    void drawsAnOrdinaryShotAsADot() {
        Simulation simulation = new Simulation(config());
        Entity shot = simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, Side.PLAYER);

        steps(simulation, WHILE_IT_IS_STILL_SHORT);

        assertThat(shot.getComponent(Appearance.class).streaks()).isFalse();
    }

    private static Entity beam(Simulation simulation) {
        return simulation.spawnProjectile(MUZZLE_X, LANE_Y, 1f, 0f, WeaponKind.LASER, Side.PLAYER);
    }
}
