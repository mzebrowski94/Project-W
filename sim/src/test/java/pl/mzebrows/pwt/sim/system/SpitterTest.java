package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_RANGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_SPEED;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SPIT_SPREAD_DEGREES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.shooting;
import static pl.mzebrows.pwt.sim.AlphaWorlds.shootingStraight;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A strain that keeps its distance and shoots. Everything about firing already existed - the gun,
 * the projectile, the range tests - so what these are about is when it pulls the trigger, where the
 * shot goes, and the fact that it stops walking to do it.
 *
 * <p>The commander is invulnerable throughout. These are tests about a shooter, and one that killed
 * its target on the second shot would end most of them before the interesting part.
 */
class SpitterTest {

    private static final float MIDDLE = 50f;

    /** Two thirds of the firing range: comfortably inside it, and nowhere near touching. */
    private static final float INSIDE = SPIT_RANGE * 0.66f;

    @Test
    void holdsItsGroundOnceWhatItIsHuntingIsInRange() {
        Simulation simulation = shooting();
        simulation.setCommanderInvulnerable(true);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        steps(simulation, 20);

        assertThat(spitter.getComponent(Position.class).x).isCloseTo(MIDDLE, Offset.offset(0.01f));
    }

    /** The other half of the same rule: out of range it is an ordinary enemy and comes at you. */
    @Test
    void closesTheDistanceWhileItIsStillTooFarAwayToShoot() {
        Simulation simulation = shooting();
        simulation.setCommanderInvulnerable(true);
        Entity spitter = simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + SPIT_RANGE * 3f, MIDDLE);

        steps(simulation, 20);

        assertThat(spitter.getComponent(Position.class).x).isGreaterThan(MIDDLE + 1f);
    }

    @Test
    void firesOnceWhatItIsHuntingComesInsideItsRange() {
        Simulation simulation = shootingStraight();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
    }

    @Test
    void firesNothingAtSomethingOutOfRange() {
        Simulation simulation = shootingStraight();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + SPIT_RANGE * 3f, MIDDLE);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /**
     * It will not empty itself into a wall. The shot would stop in the rock anyway - that rule is
     * older than this strain - so what this is about is the picture, which would read as broken.
     */
    @Test
    void willNotFireThroughRock() {
        Simulation simulation =
                shootingStraight(ground().rect(11, 0, 1, 20, Terrain.ROCK).build());
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, 52f, MIDDLE);
        simulation.spawnCommander(52f + INSIDE, MIDDLE);

        steps(simulation, 10);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void sendsAShotStraightWhenItHasNoSpreadToDrawFrom() {
        Simulation simulation = shootingStraight();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        simulation.step();

        assertThat(shots(simulation).first().getComponent(Velocity.class).y).isCloseTo(0f, Offset.offset(1e-4f));
    }

    /** A cone, not a line: no two shots need go the same way, and that is what reaches the men beside him. */
    @Test
    void throwsItsShotsAroundTheAimRatherThanAlongIt() {
        Simulation simulation = shooting();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        float first = nextShotBearing(simulation);
        float second = nextShotBearing(simulation);
        float third = nextShotBearing(simulation);

        assertThat(first).isNotEqualTo(second);
        assertThat(second).isNotEqualTo(third);
    }

    /** Nothing leaves the cone. A spread that could miss by ninety degrees is not aiming at all. */
    @Test
    void keepsEveryShotInsideTheConeItDeclares() {
        Simulation simulation = shooting();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        float widest = (float) Math.sin(Math.toRadians(SPIT_SPREAD_DEGREES * 0.5));
        for (int i = 0; i < 6; i++) {
            assertThat(Math.abs(nextShotBearing(simulation))).isLessThanOrEqualTo(widest + 1e-4f);
        }
    }

    /**
     * The cone is applied by turning the shot, not by nudging where it is aimed. Turning keeps every
     * shot the same length and the cone the same width at every distance; nudging a target point
     * would make a creature wilder the further away it stood, which is backwards.
     */
    @Test
    void sendsEveryShotOutAtTheSameSpeedHoweverFarOffTrueItIs() {
        Simulation simulation = shooting();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnUnit(UnitKind.SPITTER, MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE + INSIDE, MIDDLE);

        for (int i = 0; i < 4; i++) {
            Velocity velocity = nextShot(simulation);
            assertThat((float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y))
                    .isCloseTo(SPIT_SPEED, Offset.offset(0.01f));
        }
    }

    /** How far off true the next shot went, as the sine of its bearing - no trigonometry to read it. */
    private static float nextShotBearing(Simulation simulation) {
        Velocity velocity = nextShot(simulation);
        float length = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return velocity.y / length;
    }

    /**
     * The next shot fired, read on the tick it leaves the muzzle.
     *
     * <p>It waits for the air to clear first, and that is not tidiness: the gun's cooldown is longer
     * than a shot's flight, so stepping a fixed number of ticks and reading whatever is in the air
     * samples one projectile twice as readily as it samples two - which looks exactly like a shooter
     * that fires the same way every time.
     */
    private static Velocity nextShot(Simulation simulation) {
        stepUntil(simulation, false);
        stepUntil(simulation, true);
        return shots(simulation).first().getComponent(Velocity.class);
    }

    /** Steps until the arena holds a shot, or holds none, whichever was asked for. */
    private static void stepUntil(Simulation simulation, boolean wanted) {
        for (int i = 0; i < 300; i++) {
            if ((shots(simulation).size() > 0) == wanted) {
                return;
            }
            simulation.step();
        }
        throw new AssertionError(wanted ? "nothing was ever fired" : "the air never cleared");
    }

    private static ImmutableArray<Entity> shots(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Projectile.class).get());
    }
}
