package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLAST_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;

/**
 * One circle, uniformly fatal inside it, harmless outside it, and on nobody's side. Those three
 * together are the whole of what a blast is, and they are what make an explosion something the player
 * reads off the picture rather than computes.
 *
 * <p>The circle that kills is the entity's own {@code Body}, which is also the circle the renderer
 * draws. There is no second radius to keep in step, so the tests here pin the rule rather than the
 * bookkeeping.
 */
class BlastTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside a blast at the middle, and comfortably outside one. */
    private static final float INSIDE = BLAST_RADIUS - 1f;

    private static final float OUTSIDE = BLAST_RADIUS + 1f;

    /** Wide enough to reach past a tile of rock, so the sight line decides rather than the range. */
    private static final float LONG_REACH = 14f;

    private static final int ROCK_COL = 10;

    /** Clear of the rock, in the open, and the same distance away as the sheltered one. */
    private static final float BLAST_X = 47f;

    private static final float BLAST_Y = 52f;

    /**
     * Enough ticks for a blast to catch what is standing in it and for the damage it owes to land the
     * tick afterwards.
     */
    private static final int SETTLE = 3;

    @Test
    void killsEverythingStandingInsideIt() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(MIDDLE + INSIDE, MIDDLE);
        simulation.spawnEnemy(MIDDLE, MIDDLE - INSIDE);

        simulation.spawnBlast(MIDDLE, MIDDLE, BLAST_RADIUS);
        steps(simulation, SETTLE);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    @Test
    void leavesAnythingStandingOutsideItUntouched() {
        Simulation simulation = simulation();
        Entity clear = simulation.spawnEnemy(MIDDLE + OUTSIDE, MIDDLE);

        simulation.spawnBlast(MIDDLE, MIDDLE, BLAST_RADIUS);
        steps(simulation, SETTLE);

        assertThat(clear.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    /**
     * A blast is on nobody's side, so it catches him exactly as it catches them. What it leaves
     * behind differs, and that is a fact about the victim rather than about the blast (G0020): an
     * alien dies, and he goes down bleeding, because there is now somebody on his side who would
     * come and get him (G0023).
     */
    @Test
    void catchesTheCommanderAsReadilyAsAnEnemy() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);

        simulation.spawnBlast(MIDDLE, MIDDLE, BLAST_RADIUS);
        steps(simulation, SETTLE);

        assertThat(commander.getComponent(Casualty.class).begun).isTrue();
    }

    @Test
    void killsABruteAsReadilyAsAGrunt() {
        Simulation simulation = simulation();
        Entity brute = simulation.spawnBrute(MIDDLE, MIDDLE);

        simulation.spawnBlast(MIDDLE, MIDDLE, BLAST_RADIUS);
        steps(simulation, SETTLE);

        assertThat(brute.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void doesNotReachThroughRock() {
        Simulation simulation =
                on(ground().rect(ROCK_COL, 0, 1, ARENA_TILES, Terrain.ROCK).build());
        Entity sheltered = simulation.spawnEnemy(58f, BLAST_Y);
        Entity exposed = simulation.spawnEnemy(BLAST_X, 63f);

        simulation.spawnBlast(BLAST_X, BLAST_Y, LONG_REACH);
        steps(simulation, SETTLE);

        assertThat(sheltered.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(exposed.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void catchesWhatWasStandingInItAndNothingThatWalksInAfterwards() {
        Simulation simulation = simulation();

        simulation.spawnBlast(MIDDLE, MIDDLE, BLAST_RADIUS);
        steps(simulation, SETTLE);
        Entity latecomer = simulation.spawnEnemy(MIDDLE, MIDDLE);
        steps(simulation, SETTLE);

        assertThat(count(simulation, Blast.class)).isOne();
        assertThat(latecomer.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    @Test
    void takesItselfOffTheFieldWhenTheFlashHasBurnedOut() {
        Simulation simulation = simulation();

        simulation.spawnBlast(MIDDLE, MIDDLE, BLAST_RADIUS);
        steps(simulation, ticksIn(BLAST_SECONDS) + 2);

        assertThat(count(simulation, Blast.class)).isZero();
    }
}
