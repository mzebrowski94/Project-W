package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FLAME_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.PELLETS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * What one pull of a trigger puts in the air.
 *
 * <p>It used to be one shot, always, worked out separately by each of the four things that fire - so
 * a gun that threw a handful of pellets would have thrown one from a mount and six in the commander's
 * hands. It is one place now, and these tests are about the three things a gun can say there: how
 * many, how wide, and how far.
 *
 * <p>The reach is the one worth reading twice. <strong>It is a property of the shot, not of the
 * aim</strong> - a grenade has had a flight length since G0019 - so the enforced invariant that aim
 * is a direction and never a distance is untouched, and {@code CommanderControlSystemTest} still
 * holds it.
 */
class VolleyTest {

    private static final float X = 40f;
    private static final float Y = 50f;

    /** Aim well to his right, along the lane, so every pellet leaves along +x. */
    private static final CommanderInput FIRING = new CommanderInput(0f, 0f, X + 30f, Y, true, false);

    /** Enough ticks for a short-lived shot to have gone out, at the fixture's projectile speed. */
    private static final int UNTIL_SPENT = 40;

    @Test
    void sendsAsManyShotsAsTheGunNamesFromOnePull() {
        Simulation simulation = holding(WeaponKind.SHOTGUN);

        simulation.step(FIRING);

        assertThat(count(simulation, Projectile.class)).isEqualTo(PELLETS);
    }

    @Test
    void sendsOneFromAGunThatNamesOne() {
        Simulation simulation = holding(WeaponKind.SIDEARM);

        simulation.step(FIRING);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    /**
     * A cone, not a line. The shotgun in the fixture names no spread and the flamer does, so the two
     * of them together say that the width comes off the gun rather than off the firing code.
     */
    @Test
    void throwsEveryPelletStraightForAGunWithNoConeOfItsOwn() {
        Simulation simulation = holding(WeaponKind.SHOTGUN);

        simulation.step(FIRING);

        for (Entity shot : projectiles(simulation)) {
            assertThat(shot.getComponent(Velocity.class).y).isZero();
        }
    }

    @Test
    void scattersThemInsideTheConeForAGunThatNamesOne() {
        Simulation simulation = holding(WeaponKind.FLAMER);

        simulation.step(FIRING);

        assertThat(projectiles(simulation)).anyMatch(shot -> shot.getComponent(Velocity.class).y != 0f);
    }

    /**
     * The cone is applied by turning the shot rather than by aiming it somewhere else, so every
     * pellet of a volley travels at exactly the same speed and the cone is the same width at every
     * distance - the trade a spitter's spread already makes, for the same reason.
     */
    @Test
    void sendsEveryPelletOutAtTheSameSpeed() {
        Simulation simulation = holding(WeaponKind.FLAMER);

        simulation.step(FIRING);

        for (Entity shot : projectiles(simulation)) {
            Velocity velocity = shot.getComponent(Velocity.class);
            assertThat((float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y))
                    .isCloseTo(speed(simulation), org.assertj.core.data.Offset.offset(0.01f));
        }
    }

    /** A gun with a reach is a gun whose shots go out. Nothing else changes about them. */
    @Test
    void dropsAShotOnceItHasFlownAsFarAsItsGunAllows() {
        Simulation simulation = holding(WeaponKind.FLAMER);

        simulation.step(FIRING);
        steps(simulation, UNTIL_SPENT);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void leavesAShotFromAGunWithNoReachFlying() {
        Simulation simulation = holding(WeaponKind.SIDEARM);

        simulation.step(FIRING);
        steps(simulation, UNTIL_SPENT);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    /** And it goes out where the gun said, not somewhere convenient: half of the reach is still flying. */
    @Test
    void carriesItTheWholeOfTheReachBeforeItGoesOut() {
        Simulation simulation = holding(WeaponKind.FLAMER);

        simulation.step(FIRING);
        assertThat(projectiles(simulation).first().getComponent(Projectile.class).range)
                .isEqualTo(FLAME_RANGE);

        steps(simulation, ticksIn(FLAME_RANGE / speed(simulation) * 0.5f));

        assertThat(count(simulation, Projectile.class)).isEqualTo(PELLETS);
    }

    private static Simulation holding(WeaponKind gun) {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(X, Y);
        commander.getComponent(Weapon.class).set(gun, simulation.config().weapon(gun));
        return simulation;
    }

    private static float speed(Simulation simulation) {
        return simulation.config().weapon(WeaponKind.FLAMER).projectileSpeed();
    }

    private static ImmutableArray<Entity> projectiles(Simulation simulation) {
        PooledEngine engine = simulation.engine();
        return engine.getEntitiesFor(
                Family.all(Projectile.class, Velocity.class).get());
    }
}
