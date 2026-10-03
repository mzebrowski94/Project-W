package pl.mzebrows.pwt.sim.system.death;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MINE_FUSE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MINE_TRIGGER_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * Something small lying in the open that the other side walks into.
 *
 * <p>The barrel's opposite number, and the two halves of that are what these tests are about.
 * <strong>Only the other side arms one</strong>, so the player lays them, crosses them and hauls
 * them about - but <strong>the blast is on nobody's side</strong> like every other blast (G0018), so
 * being near one when it goes is still his own doing.
 *
 * <p>Everything else about it is borrowed rather than invented: it explodes by dying, through the one
 * death reaction, so being shot, being caught in somebody else's blast and running out of fuse all
 * end in the same circle - and a mine beside a mine chains without a rule anywhere saying it should.
 */
class MineTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside the trigger, and comfortably outside it. */
    private static final float INSIDE = MINE_TRIGGER_RADIUS - 1f;

    private static final float OUTSIDE = MINE_TRIGGER_RADIUS + 3f;

    /** The fuse, and a couple of ticks for the death and the blast that answers it. */
    private static final int UNTIL_IT_GOES = ticksIn(MINE_FUSE) + 3;

    /** Long enough to see a mine that was never going to arm fail to arm. */
    private static final int A_WHILE = 30;

    private static final CommanderInput STILL = CommanderInput.IDLE;

    /** Directly away from the load, which is always to his left in these tests. */
    private static final CommanderInput BACKWARDS = new CommanderInput(1f, 0f, 0f, 0f, false, false);

    @Test
    void startsTickingWhenSomethingOnTheOtherSideComesNear() {
        Simulation simulation = simulation();
        Entity mine = simulation.spawnMine(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + INSIDE, MIDDLE);

        simulation.step();

        assertThat(mine.getComponent(Mine.class).armed).isTrue();
    }

    @Test
    void liesThereWhileNothingHasComeNearEnough() {
        Simulation simulation = simulation();
        Entity mine = simulation.spawnMine(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + OUTSIDE, MIDDLE);

        steps(simulation, A_WHILE);

        assertThat(mine.getComponent(Mine.class).armed).isFalse();
    }

    /** The whole of why a level can lay a field of them in front of the men who have to cross it. */
    @Test
    void liesThereWhileTheCommanderStandsOnIt() {
        Simulation simulation = simulation();
        Entity mine = simulation.spawnMine(MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE, MIDDLE);

        steps(simulation, A_WHILE, STILL);

        assertThat(mine.getComponent(Mine.class).armed).isFalse();
    }

    @Test
    void liesThereWhileATrooperStandsOnIt() {
        Simulation simulation = simulation();
        Entity mine = simulation.spawnMine(MIDDLE, MIDDLE);
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE, MIDDLE);

        steps(simulation, A_WHILE, STILL);

        assertThat(mine.getComponent(Mine.class).armed).isFalse();
    }

    @Test
    void goesOffOnceItsFuseHasRunOut() {
        Simulation simulation = harmless();
        simulation.spawnMine(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + INSIDE, MIDDLE);

        steps(simulation, UNTIL_IT_GOES);

        assertThat(count(simulation, Mine.class)).isZero();
    }

    @Test
    void killsWhateverWalkedIntoIt() {
        Simulation simulation = harmless();
        simulation.spawnMine(MIDDLE, MIDDLE);
        simulation.spawnEnemy(MIDDLE + INSIDE, MIDDLE);

        steps(simulation, UNTIL_IT_GOES);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    /** A blast is on nobody's side, and a mine leaves an ordinary one. Laying them badly costs him. */
    @Test
    void catchesTheCommanderStandingBesideOneAsReadilyAsAnEnemy() {
        Simulation simulation = harmless();
        simulation.spawnMine(MIDDLE, MIDDLE);
        Entity commander = simulation.spawnCommander(MIDDLE + 1f, MIDDLE);
        simulation.spawnEnemy(MIDDLE - INSIDE, MIDDLE);

        steps(simulation, UNTIL_IT_GOES, STILL);

        assertThat(commander.getComponent(Vitality.class).begun).isTrue();
    }

    /**
     * A fuse that could be walked away from is a fuse the player watches rather than a threat the
     * other side has to deal with. The same reason a nest that has woken never sleeps again.
     */
    @Test
    void goesOnTickingAfterWhateverSetItOffIsGone() {
        Simulation simulation = harmless();
        simulation.spawnMine(MIDDLE, MIDDLE);
        Entity enemy = simulation.spawnEnemy(MIDDLE + INSIDE, MIDDLE);

        simulation.step();
        enemy.getComponent(Damage.class).land(1000, Harm.KILL);
        steps(simulation, UNTIL_IT_GOES);

        assertThat(count(simulation, Mine.class)).isZero();
    }

    /** No rule anywhere says a mine chains. It falls out of every explosion being a death (G0018). */
    @Test
    void goesUpWithAnythingThatExplodesBesideIt() {
        Simulation simulation = harmless();
        Entity mine = simulation.spawnMine(MIDDLE, MIDDLE);

        simulation.spawnBlast(MIDDLE + 1f, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, UNTIL_IT_GOES);

        assertThat(mine.getComponent(Mine.class)).isNull();
    }

    /** Hauled by the same hands and the same component that haul a barrel (G0021). */
    @Test
    void haulsAMineAsReadilyAsABarrel() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + 2f, MIDDLE);
        Entity mine = simulation.spawnMine(MIDDLE, MIDDLE);

        simulation.step(STILL.dragging(true));
        steps(simulation, 30, BACKWARDS.dragging(true));

        assertThat(mine.getComponent(Position.class).x).isGreaterThan(MIDDLE + 2f);
    }

    /** Nothing on the field claws anything, so a test about a mine is only about the mine. */
    private static Simulation harmless() {
        return new Simulation(everyStrain(config(), strain -> strain.withContactDamage(0)));
    }
}
