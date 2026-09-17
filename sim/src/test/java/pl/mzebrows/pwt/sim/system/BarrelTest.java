package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_BITTEN_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.huntingBarrels;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.willing;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.def.AiChance;

/**
 * A barrel is set off by gunfire or by something hungry, and both are the same event: it dies, and a
 * thing that blasts on death leaves a blast where it fell.
 *
 * <p>It is a pickup rather than an obstacle, and the commander's side is offered nothing by one, so
 * he can stand against it all day.
 *
 * <p><strong>It is also the one pickup a creature is wary of</strong> (G0022): it is steered around
 * rather than walked over, and touching one only sets it off if the dice say so. Every test here that
 * wants a bite therefore rigs the dice, and the one that wants none leaves the base fixture alone -
 * which switches every occasional behaviour off.
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

    /** Close enough that anything hungry for one would set off across the ground to it. */
    private static final float NEARBY = 5f;

    /** Enough to notice it, walk the distance and put its mouth on it. */
    private static final int LONG_ENOUGH_TO_WALK_OVER = 240;

    /** Outside the circle a bitten barrel leaves and inside the one a shot barrel leaves. */
    private static final float BETWEEN_THE_TWO_CIRCLES =
            MIDDLE + (BARREL_BITTEN_BLAST_RADIUS + BARREL_BLAST_RADIUS) * 0.5f;

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
        Simulation simulation = new Simulation(willing(AiChance.BITE_A_BARREL));
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        simulation.spawnEnemy(TOUCHING, MIDDLE);

        steps(simulation, ARRIVES);

        assertThat(count(simulation, Blast.class)).isOne();
        assertThat(count(simulation, Pickup.class)).isZero();
    }

    @Test
    void isLeftStandingByAnAlienWithNoAppetiteForOne() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        simulation.spawnEnemy(TOUCHING, MIDDLE);

        steps(simulation, ARRIVES);

        assertThat(count(simulation, Blast.class)).isZero();
        assertThat(count(simulation, Pickup.class)).isOne();
    }

    /**
     * The asymmetry the whole barrel is balanced on: a creature that set one off with its mouth was
     * standing on top of it and takes most of the blast itself, so a barrel the aliens blunder into
     * is worth much less than one the player shoots.
     */
    @Test
    void leavesASmallerCircleWhenItIsBittenIntoThanWhenItIsShot() {
        Simulation simulation = new Simulation(willing(AiChance.BITE_A_BARREL));
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        simulation.spawnEnemy(TOUCHING, MIDDLE);
        Entity bystander = simulation.spawnEnemy(MIDDLE, BETWEEN_THE_TWO_CIRCLES);

        steps(simulation, ARRIVES + 2);

        assertThat(bystander.getComponent(Dying.class).begun).isFalse();
    }

    @Test
    void catchesTheSameBystanderWhenItIsShotInstead() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        Entity bystander = simulation.spawnEnemy(MIDDLE, BETWEEN_THE_TWO_CIRCLES);

        simulation.spawnProjectile(MUZZLE, MIDDLE, 1f, 0f);
        steps(simulation, ARRIVES + 2);

        assertThat(bystander.getComponent(Dying.class).begun).isTrue();
    }

    /**
     * The other side of a barrel being wanted: something that can only set one off in its own face
     * is wary of it, and stays wary however hungry and idle it is. Wariness is about the pair, not
     * about the thing (G0022).
     */
    @Test
    void isNotWorthCrossingTheGroundForToSomethingThatCannotUseOne() {
        Simulation simulation = huntingBarrels();
        Entity grunt = simulation.spawnEnemy(MIDDLE, MIDDLE);
        grunt.getComponent(Taking.class).swallows = false;
        simulation.spawnBarrel(MIDDLE + NEARBY, MIDDLE);

        steps(simulation, LONG_ENOUGH_TO_WALK_OVER);

        assertThat(count(simulation, Pickup.class)).isOne();
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
     * Friendly fire is not a special case anywhere: a blast is on nobody's side, so the rule that
     * catches the aliens is the same rule that catches him. Pillar 2 - he is a unit on the field, and
     * this is one of the ways the field can put him on his back. It leaves him a casualty rather than
     * a corpse because a blast wounds, and that is a fact about the blast rather than about him.
     */
    @Test
    void putsDownTheCommanderWhoShootsOneFromTooCloseUp() {
        Simulation simulation = simulation();
        simulation.spawnBarrel(MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + POINT_BLANK, MIDDLE);

        simulation.spawnProjectile(MIDDLE + 2f, MIDDLE, -1f, 0f);
        steps(simulation, ARRIVES + 2);

        assertThat(commander.getComponent(Casualty.class).begun).isTrue();
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
