package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DISC_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WEAPON_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The disc gun's disc: a shot that comes off what it hits rather than stopping at it - rock, the arena's
 * edge, scenery and the enemy alike - landing its blow on every body it comes off, passing through its own
 * side, and stopping at the thing after its last bounce or at the end of its reach, whichever is first.
 *
 * <p>The fixture's disc comes off two things ({@code DISC_BOUNCES}). Every disc here is fired along +x from
 * the same place; the fixture's shot crosses one world unit a tick.
 */
class DiscTest {

    private static final float X = 20f;
    private static final float Y = 50f;

    /** Ahead of the muzzle, and behind it: a lane the disc can rattle up and down. */
    private static final float AHEAD = X + 12f;

    private static final float BEHIND = X - 12f;

    /** Long enough to reach what is ahead and be on the way back, short of reaching what is behind. */
    private static final int ON_THE_WAY_BACK = 15;

    /** Long enough for any disc here to have used up every bounce it has, and short of its reach. */
    private static final int LONG_AFTER = 50;

    /** Health nothing on the field can spend, so what a body lost can be read rather than inferred. */
    private static final int UNKILLABLE = 5000;

    /** More bounces than any of these lanes can use up, so only the reach ends the flight. */
    private static final int ENDLESS = 1000;

    @Test
    void comesBackOffAnObstacleTheWayItCame() {
        Simulation simulation = harmlessWorld(config());
        simulation.spawnObstacle(AHEAD, Y);

        Entity disc = fire(simulation);
        steps(simulation, ON_THE_WAY_BACK);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
        assertThat(disc.getComponent(Velocity.class).x).isNegative();
    }

    @Test
    void comesBackOffRock() {
        // A rock column a few tiles ahead of the muzzle, open ground either side of it.
        Simulation simulation = new Simulation(
                config(), ground().rect(5, 0, 1, ARENA_TILES, Terrain.ROCK).build());

        Entity disc = fire(simulation);
        steps(simulation, ON_THE_WAY_BACK);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
        assertThat(disc.getComponent(Velocity.class).x).isNegative();
    }

    @Test
    void comesBackOffTheEdgeOfTheArena() {
        Simulation simulation = harmlessWorld(config());

        Entity disc = simulation.spawnProjectile(ARENA - 3f, Y, 1f, 0f, WeaponKind.DISC_GUN, Side.PLAYER);
        steps(simulation, 10);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
        assertThat(disc.getComponent(Velocity.class).x).isNegative();
    }

    /** Two bounces: off the one ahead, off the one behind, and the one ahead again is where it stops. */
    @Test
    void stopsAtTheThingAfterItsLastBounce() {
        Simulation simulation = harmlessWorld(config());
        simulation.spawnObstacle(AHEAD, Y);
        simulation.spawnObstacle(BEHIND, Y);

        fire(simulation);
        steps(simulation, LONG_AFTER);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /**
     * A blow every time it comes off a body, and only one: it is put clear of what it came off, so it does
     * not land on the same body again on the ticks that follow. Off the enemy, off the obstacle behind, and
     * into the enemy a second time, which is where it stops - two blows on one body.
     */
    @Test
    void landsABlowOnAnEnemyEveryTimeItComesOffOne() {
        Simulation simulation = harmlessWorld(everyStrain(config(), strain -> strain.withHealth(UNKILLABLE)));
        Entity enemy = simulation.spawnUnit(UnitKind.GRUNT, AHEAD, Y);
        simulation.spawnObstacle(BEHIND, Y);

        fire(simulation);
        steps(simulation, LONG_AFTER);

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(UNKILLABLE - 2 * WEAPON_DAMAGE);
    }

    @Test
    void passesThroughItsOwnSideAndHurtsNobodyOnIt() {
        Simulation simulation = harmlessWorld(config());
        Entity trooper = simulation.spawnTrooper(AHEAD, Y);

        Entity disc = fire(simulation);
        steps(simulation, ON_THE_WAY_BACK);

        assertThat(disc.getComponent(Velocity.class).x).isPositive();
        assertThat(trooper.getComponent(Vitality.class).wounds).isZero();
    }

    /**
     * The reach is counted along every leg, not as the crow flies: rattling between two obstacles it never
     * gets further than a lane's length from the muzzle, and still goes out once it has flown its reach.
     */
    @Test
    void goesOutAtItsReachCountedAlongEveryLeg() {
        SimConfig endless =
                config().withWeapons(config().weapons().map(WeaponKind.DISC_GUN, gun -> gun.withBounces(ENDLESS)));
        Simulation simulation = harmlessWorld(endless);
        simulation.spawnObstacle(AHEAD, Y);
        simulation.spawnObstacle(BEHIND, Y);

        fire(simulation);
        steps(simulation, (int) (DISC_RANGE * 0.5f));
        int halfway = count(simulation, Projectile.class);
        steps(simulation, (int) DISC_RANGE);

        assertThat(halfway).isEqualTo(1);
        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /** Nothing on the field hits back, so a test about a shot is only about the shot. */
    private static Simulation harmlessWorld(SimConfig config) {
        return new Simulation(everyStrain(config, strain -> strain.withContactDamage(0)));
    }

    private static Entity fire(Simulation simulation) {
        return simulation.spawnProjectile(X, Y, 1f, 0f, WeaponKind.DISC_GUN, Side.PLAYER);
    }
}
