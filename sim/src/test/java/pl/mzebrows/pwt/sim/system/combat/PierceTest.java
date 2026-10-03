package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RAILGUN_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * A shot that goes through things.
 *
 * <p>It is one number on the gun - the least time between the blows one shot lands - and zero on
 * every other gun in the table, which is the same "zero means not for me" a strain that does not
 * leap and a gun that stays cold already declare. What a piercing shot comes off is <em>scenery</em>
 * and nothing else, which is the rule a thrown grenade has followed since G0019 rather than a second
 * one invented here.
 *
 * <p>Rock is not in that rule and does not need to be: a shot flying into rock is thrown away by
 * {@code ProjectileBoundsSystem}, which asks nothing about what kind of shot it is. The test for it
 * is here anyway, because "it only stops at walls" is the whole of what the feature promises and a
 * promise nobody checked is the one that breaks.
 */
class PierceTest {

    private static final float X = 20f;
    private static final float Y = 50f;

    /** Two in the same lane, far enough apart that the first is well behind before the second is met. */
    private static final float NEAR = 35f;

    private static final float FAR = 42f;

    /** Aim along the lane, well past both of them, so the shot leaves along +x. */
    private static final CommanderInput FIRING = new CommanderInput(0f, 0f, X + 60f, Y, true, false);

    /** Long enough for a shot to cross the arena, and far short of the railgun's next pull. */
    private static final int UNTIL_PAST = 40;

    /** Health nothing on the field can spend, so what a body lost can be read rather than inferred. */
    private static final int UNKILLABLE = 5000;

    @Test
    void killsBothOfTwoStandingInOneLine() {
        Simulation simulation = withRailgun(harmlessWorld());
        simulation.spawnUnit(UnitKind.GRUNT, NEAR, Y);
        simulation.spawnUnit(UnitKind.GRUNT, FAR, Y);

        fireOnce(simulation);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    @Test
    void goesOnFlyingAfterItHasPassedThroughSomething() {
        Simulation simulation = withRailgun(harmlessWorld());
        simulation.spawnUnit(UnitKind.GRUNT, NEAR, Y);

        simulation.step(FIRING);
        steps(simulation, 20);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    @Test
    void stopsAtAnObstacleAndLeavesWhatIsBehindItStanding() {
        Simulation simulation = withRailgun(harmlessWorld());
        simulation.spawnObstacle(NEAR, Y);
        simulation.spawnUnit(UnitKind.GRUNT, FAR, Y);

        fireOnce(simulation);

        assertThat(count(simulation, Projectile.class)).isZero();
        assertThat(countLiving(simulation, Enemy.class)).isEqualTo(1);
    }

    @Test
    void stopsAtRock() {
        Simulation simulation = withRailgun(new Simulation(config(), wall()));

        fireOnce(simulation);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /**
     * The other half of the rule. A gun that names no interval is unchanged: its shot is spent on the
     * first thing it touches, whatever that thing is.
     */
    @Test
    void leavesAnOrdinaryShotStoppingAtTheFirstThingItTouches() {
        Simulation simulation = harmlessWorld();
        simulation.spawnCommander(X, Y);
        simulation.spawnUnit(UnitKind.GRUNT, NEAR, Y);

        fireOnce(simulation);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /**
     * What the interval is for. A shot overlaps a body for several ticks on its way through, and
     * without a wait between blows it would land one on every one of them.
     */
    @Test
    void landsOneBlowOnOneBodyRatherThanOneEveryTick() {
        Simulation simulation = withRailgun(new Simulation(
                everyStrain(config(), strain -> strain.withHealth(UNKILLABLE).withContactDamage(0))));
        Entity enemy = simulation.spawnUnit(UnitKind.GRUNT, NEAR, Y);

        fireOnce(simulation);

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(UNKILLABLE - RAILGUN_DAMAGE);
    }

    /** Nothing on the field hits back, so a test about a shot is only about the shot. */
    private static Simulation harmlessWorld() {
        return new Simulation(everyStrain(config(), strain -> strain.withContactDamage(0)));
    }

    private static Simulation withRailgun(Simulation simulation) {
        Entity commander = simulation.spawnCommander(X, Y);
        commander
                .getComponent(Weapon.class)
                .set(
                        WeaponKind.RAILGUN,
                        simulation.config().weapon(WeaponKind.RAILGUN),
                        simulation.config().tickSeconds());
        return simulation;
    }

    /** One pull, then nothing: the railgun's cooldown is far longer than any of these tests run. */
    private static void fireOnce(Simulation simulation) {
        simulation.step(FIRING);
        steps(simulation, UNTIL_PAST);
    }

    /** A rock column across the lane, with open ground either side of it. */
    private static TileMap wall() {
        return ground().rect(8, 0, 1, ARENA_TILES, Terrain.ROCK).build();
    }
}
