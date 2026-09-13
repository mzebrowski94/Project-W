package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Pickup;

/**
 * A barrel is set off by gunfire or by something hungry, and both are the same event: it dies, and a
 * thing that blasts on death leaves a blast where it fell.
 *
 * <p>It is a pickup rather than an obstacle, and that is load-bearing rather than tidy. Avoidance
 * steers a unit around anything solid <em>except</em> a pickup, so a barrel that was an obstacle would
 * be something the aliens carefully walked around - and the commander's side is offered nothing by
 * one, so he can stand against it all day.
 */
class BarrelTest {

    private static final float MIDDLE = 50f;

    /** Far enough that the shot is visibly in flight, near enough that a few ticks land it. */
    private static final float MUZZLE = 45f;

    /** Touching the barrel: an enemy is one across and so is the barrel. */
    private static final float TOUCHING = MIDDLE + 1.5f;

    /** Inside the blast, and clear of the line the shot flies along. */
    private static final float BESIDE = MIDDLE + 5f;

    /** Enough ticks for the shot to arrive, the barrel to die and the blast to appear. */
    private static final int ARRIVES = 8;

    /** Enough for that, plus the second barrel to take the blast and go off in its turn. */
    private static final int CASCADES = 16;

    /** Well inside the blast: near enough that setting one off here is a mistake he made. */
    private static final float POINT_BLANK = 4f;

    @Test
    void goesOffWhenItIsShot() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);

        simulation.spawnProjectile(MUZZLE, MIDDLE, 1f, 0f);
        steps(simulation, ARRIVES);

        assertThat(count(simulation, Blast.class)).isOne();
        assertThat(count(simulation, Pickup.class)).isZero();
    }

    @Test
    void goesOffWhenAnAlienBitesIntoIt() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        simulation.spawnEnemy(TOUCHING, MIDDLE);

        steps(simulation, ARRIVES);

        assertThat(count(simulation, Blast.class)).isOne();
        assertThat(count(simulation, Pickup.class)).isZero();
    }

    @Test
    void killsWhatIsStandingBesideItWhenItGoesOff() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        Entity bystander = simulation.spawnEnemy(MIDDLE, BESIDE);

        simulation.spawnProjectile(MUZZLE, MIDDLE, 1f, 0f);
        steps(simulation, ARRIVES + 2);

        assertThat(bystander.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void offersTheCommanderNothingAndIsLeftStanding() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        simulation.spawnCommander(TOUCHING, MIDDLE);

        steps(simulation, ARRIVES);

        assertThat(count(simulation, Blast.class)).isZero();
        assertThat(count(simulation, Pickup.class)).isOne();
    }

    /**
     * Friendly fire is not a special case anywhere: a blast is on nobody's side, so the rule that kills
     * the aliens is the same rule that kills him. Pillar 2 - he is a unit on the field, and this is one
     * of the ways the field can kill him.
     */
    @Test
    void killsTheCommanderWhoShootsOneFromTooCloseUp() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + POINT_BLANK, MIDDLE);

        simulation.spawnProjectile(MIDDLE + 2f, MIDDLE, -1f, 0f);
        steps(simulation, ARRIVES + 2);

        assertThat(commander.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void setsOffTheBarrelStandingNextToIt() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        simulation.spawnBarrel(MIDDLE, MIDDLE + BARREL_BLAST_RADIUS - 1f);

        simulation.spawnProjectile(MUZZLE, MIDDLE, 1f, 0f);
        steps(simulation, CASCADES);

        assertThat(count(simulation, Pickup.class)).isZero();
    }
}
