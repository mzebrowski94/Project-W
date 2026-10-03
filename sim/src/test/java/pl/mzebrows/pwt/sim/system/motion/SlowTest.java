package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LASER_SELF_SLOW_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.spawnSteered;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Being slow: one rule with two sources.
 *
 * <p>Something can be robbed of speed by what was <em>done</em> to it - a beam held on it - or by
 * where it is <em>standing</em>, and these are the same answer rather than two mechanics. Both are
 * spent in one place, both scale the pace and neither touches the course, so a slowed unit goes
 * exactly where it was going and takes longer about it.
 *
 * <p>The thicket half is about the <strong>asymmetry</strong> as much as the slow. Ground that cost
 * both sides the same would be scenery: everybody arrives at the fight in the order they would have
 * anyway. It is the player's side wading and the things that live there barely noticing that makes a
 * thicket ground he has to decide about.
 */
class SlowTest {

    private static final float LANE_Y = 50f;

    /** Where things are put, and how fast the fixture body is asked to go along the lane. */
    private static final float START_X = 30f;

    private static final float DESIRED = 60f;

    private static final int A_SECOND = 60;

    /** The thicket runs from here across the arena, well east of where anything is put down. */
    private static final int THICKET_COL = 2;

    /** Trigger down, aimed east, standing still. */
    private static final CommanderInput FIRING = new CommanderInput(0f, 0f, 1f, 0f, true, false);

    @Test
    void robsACreatureTheBeamLandsOnOfItsSpeed() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(START_X, LANE_Y);
        beamAt(simulation, START_X - 2f);

        steps(simulation, 6);

        assertThat(enemy.getComponent(Slowed.class).factor()).isLessThan(1f);
    }

    @Test
    void leavesOneItHasNotTouchedAtFullSpeed() {
        Simulation simulation = new Simulation(config());
        simulation.spawnEnemy(START_X, LANE_Y);
        Entity bystander = simulation.spawnEnemy(START_X, LANE_Y + 20f);
        beamAt(simulation, START_X - 2f);

        steps(simulation, 6);

        assertThat(bystander.getComponent(Slowed.class).factor()).isEqualTo(1f);
    }

    /**
     * Refreshed by every hit rather than added to, so a beam taken off something lets it go. Without
     * this, sweeping a beam across a crowd would bank a slow on each of them.
     */
    @Test
    void letsItBackUpToSpeedOnceTheBeamComesOff() {
        Simulation simulation = new Simulation(config());
        Entity enemy = simulation.spawnEnemy(START_X, LANE_Y);
        beamAt(simulation, START_X - 2f);

        steps(simulation, 6);
        steps(simulation, A_SECOND);

        assertThat(enemy.getComponent(Slowed.class).factor()).isEqualTo(1f);
    }

    /**
     * <strong>Holding the beam down costs the man holding it, too.</strong> The gun that stops
     * things coming at you is also the gun that makes them worth being afraid of - it is the other
     * half of what makes the laser a commitment rather than a default, and it is charged in the one
     * place a pull of any trigger is charged.
     */
    @Test
    void costsTheManHoldingTheBeamSomeOfHisOwnPace() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(START_X, LANE_Y);
        commander
                .getComponent(Weapon.class)
                .set(
                        WeaponKind.LASER,
                        simulation.config().weapon(WeaponKind.LASER),
                        simulation.config().tickSeconds());

        steps(simulation, 4, FIRING);

        assertThat(commander.getComponent(Slowed.class).factor()).isLessThan(1f);
    }

    /** And lets go of him almost at once, so it is a cost while firing rather than a punishment after. */
    @Test
    void letsGoOfHimAlmostAsSoonAsHeStopsFiring() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(START_X, LANE_Y);
        commander
                .getComponent(Weapon.class)
                .set(
                        WeaponKind.LASER,
                        simulation.config().weapon(WeaponKind.LASER),
                        simulation.config().tickSeconds());

        steps(simulation, 4, FIRING);
        steps(simulation, ticksIn(LASER_SELF_SLOW_SECONDS) + 2);

        assertThat(commander.getComponent(Slowed.class).factor()).isEqualTo(1f);
    }

    /** A gun that names no self-slow writes nothing at all, which is every other gun there is. */
    @Test
    void leavesAManFiringAnOrdinaryGunAtFullPace() {
        Simulation simulation = new Simulation(config());
        Entity commander = simulation.spawnCommander(START_X, LANE_Y);

        steps(simulation, 4, FIRING);

        assertThat(commander.getComponent(Slowed.class).factor()).isEqualTo(1f);
    }

    @Test
    void makesGroundWorthGoingRoundSlowerToCrossThanOpenGround() {
        Simulation simulation = new Simulation(config(), thicket());
        Entity wading = spawnSteered(simulation, inTheThicket(), LANE_Y);
        Entity walking = spawnSteered(simulation, onOpenGround(), LANE_Y);

        drive(simulation, wading, walking);

        assertThat(travelled(wading, inTheThicket())).isLessThan(travelled(walking, onOpenGround()));
    }

    /**
     * <strong>A pace changes how long a crossing takes and never where it goes.</strong> The same
     * contract a gait and a weave already keep, asked of the ground: anything that bent a course
     * would make a route through a thicket a different route, and a level would stop being a thing
     * the player can read.
     */
    @Test
    void keepsItOnExactlyTheBearingItAskedFor() {
        Simulation simulation = new Simulation(config(), thicket());
        Entity wading = spawnSteered(simulation, inTheThicket(), LANE_Y);
        Entity walking = spawnSteered(simulation, onOpenGround(), LANE_Y);

        driveDiagonally(simulation, wading, walking);

        assertThat(bearing(wading, inTheThicket(), LANE_Y))
                .isCloseTo(bearing(walking, onOpenGround(), LANE_Y), Offset.offset(1e-4f));
    }

    @Test
    void leavesEveryOtherKindOfGroundAlone() {
        Simulation simulation = new Simulation(config(), thicket());
        Entity onPlain = spawnSteered(simulation, onOpenGround(), LANE_Y);

        drive(simulation, onPlain);

        assertThat(travelled(onPlain, onOpenGround())).isCloseTo(DESIRED, Offset.offset(0.05f));
    }

    /** One shot of the beam on the player's side, fired east along the lane. */
    private static void beamAt(Simulation simulation, float x) {
        simulation.spawnProjectile(x, LANE_Y, 1f, 0f, WeaponKind.LASER, Side.PLAYER);
    }

    /** Asks every one of them to go east at the same speed, for a second. */
    private static void drive(Simulation simulation, Entity... bodies) {
        for (int tick = 0; tick < A_SECOND; tick++) {
            for (Entity body : bodies) {
                body.getComponent(Steering.class).set(DESIRED, 0f);
            }
            simulation.step();
        }
    }

    /** The same, on a bearing that is neither along an axis nor at forty-five degrees to one. */
    private static void driveDiagonally(Simulation simulation, Entity... bodies) {
        for (int tick = 0; tick < A_SECOND; tick++) {
            for (Entity body : bodies) {
                body.getComponent(Steering.class).set(DESIRED * 0.6f, DESIRED * 0.8f);
            }
            simulation.step();
        }
    }

    private static float travelled(Entity body, float from) {
        return body.getComponent(Position.class).x - from;
    }

    private static float bearing(Entity body, float fromX, float fromY) {
        Position at = body.getComponent(Position.class);
        return (at.y - fromY) / (at.x - fromX);
    }

    /** Inside the thicket band, far enough from its eastern edge to stay in it for the whole second. */
    private static float inTheThicket() {
        return (THICKET_COL + 0.5f) * TILE;
    }

    /** Open ground, well east of the band. */
    private static float onOpenGround() {
        return (THICKET_COL + 8.5f) * TILE;
    }

    /** A band of thicket up the west of the arena, with open ground east of it to compare against. */
    private static TileMap thicket() {
        return ground().rect(0, 0, THICKET_COL + 6, ARENA_TILES, Terrain.THICKET)
                .build();
    }
}
