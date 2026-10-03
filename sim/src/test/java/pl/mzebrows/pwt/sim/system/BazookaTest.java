package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BAZOOKA_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BAZOOKA_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BAZOOKA_SHAKE_STRENGTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The bazooka: one rocket, then the wait for the gun to cool, a kick back from every pull, and a blast
 * where the rocket stops - on nobody's side, so it wounds a man caught in it as a grenade does.
 */
class BazookaTest {

    /** On a tile boundary's near side, so a wall can stand a short step in front of him. */
    private static final float X = 32.5f;

    private static final float Y = 50f;

    /** The column whose face is at {@code X + 2.5}: inside the blast, clear of his muzzle. */
    private static final int WALL_COL = (int) ((X + 2.5f) / TILE);

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

    /** The wait is the gun's, so a trooper who picked one up reloads as his commander does. */
    @Test
    void stopsItselfInATroopersHandsToo() {
        Simulation simulation = simulation();
        simulation.spawnCommander(X, Y + 40f);
        Entity trooper = simulation.spawnTrooper(X, Y);
        trooper.getComponent(Weapon.class)
                .set(
                        WeaponKind.BAZOOKA,
                        simulation.config().weapon(WeaponKind.BAZOOKA),
                        simulation.config().tickSeconds());
        trooper.getComponent(Trooper.class).look(1f, 0f);
        simulation.spawnEnemy(X + 20f, Y);

        int ticks = 0;
        while (count(simulation, Projectile.class) == 0 && ticks++ < ticksIn(2f)) {
            simulation.step();
        }

        assertThat(count(simulation, Projectile.class)).isOne();
        assertThat(trooper.getComponent(Heat.class).locked).isTrue();
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

    // ---- into a wall ----

    /**
     * A rocket that flies into rock goes off at the rock's face, not inside it - a blast whose middle is
     * in the wall sees nobody past it, so a man who fired one into the wall beside him walked away
     * untouched while its circle was drawn over him.
     */
    @Test
    void woundsAManWhoFiresItIntoAWallBesideHim() {
        Simulation simulation = on(wall());
        Entity commander = holding(simulation, WeaponKind.BAZOOKA);

        simulation.step(FIRING);
        steps(simulation, KICKED);

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    @Test
    void woundsATrooperStandingBesideHimAsReadily() {
        Simulation simulation = on(wall());
        holding(simulation, WeaponKind.BAZOOKA);
        Entity trooper = simulation.spawnTrooper(X, Y + 2f);

        simulation.step(FIRING);
        steps(simulation, KICKED);

        assertThat(trooper.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    // ---- the shake ----

    @Test
    void shakesTheGroundWhereItIsFired() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation, WeaponKind.BAZOOKA);

        simulation.step(FIRING);

        Shake shake = commander.getComponent(Shake.class);
        assertThat(shake.strength).isEqualTo(BAZOOKA_SHAKE_STRENGTH);
        assertThat(shake.fade()).isGreaterThan(0f);
    }

    @Test
    void shakesNothingForAGunThatNamesNoShake() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation, WeaponKind.SIDEARM);

        simulation.step(FIRING);

        assertThat(commander.getComponent(Shake.class).fade()).isZero();
    }

    /** A rock column whose face stands two and a half units in front of him, across the lane he aims down. */
    private static TileMap wall() {
        return ground().rect(WALL_COL, 0, 1, ARENA_TILES, Terrain.ROCK).build();
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
