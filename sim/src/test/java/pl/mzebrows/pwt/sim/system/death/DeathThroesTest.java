package pl.mzebrows.pwt.sim.system.death;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Harm;

/**
 * A death that takes time. The commander's body stays where it fell for its throes and only then is
 * taken away - the window the dying animation will play in.
 *
 * <p>The body is inert on purpose, and every way it could stop being inert is checked here: it must
 * not be driveable, must not shoot, must not be hit again, and must not be chased. A corpse that a
 * crowd keeps mauling would read as a bug long before it read as a feature.
 */
class DeathThroesTest {

    private static final float TOLERANCE = 1e-4f;

    @Test
    void landsNoBlowOnceItIsDead() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(20f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);
        kill(enemy);
        steps(simulation, 2);

        commander.getComponent(Position.class).set(50f, 50f);
        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        simulation.step();

        assertThat(unhurt(commander)).isTrue();
    }

    @Test
    void leavesTheBodyOnTheFieldWhileItIsDying() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(DEATH_THROES) - 2);

        assertThat(count(simulation, Commander.class)).isEqualTo(1);
    }

    @Test
    void takesTheBodyAwayOnceTheThroesAreOver() {
        Simulation simulation = simulation();
        kill(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(DEATH_THROES) + 2);

        assertThat(count(simulation, Commander.class)).isZero();
    }

    /**
     * He finishes the tick he was killed on - a blow is resolved after everything has moved - and then
     * the body stops dead. Before the stun took his velocity away as well as his control, a commander
     * killed while running slid the length of the arena.
     */
    @Test
    void leavesTheBodyWhereItFell() {
        Simulation simulation = simulation();
        CommanderInput running = new CommanderInput(1f, 0f, 0f, 0f, false, false);
        Entity commander = simulation.spawnCommander(50f, 50f);
        kill(commander);
        simulation.step(running);
        float fellAt = commander.getComponent(Position.class).x;

        steps(simulation, ticksIn(DEATH_THROES) - 2, running);

        Position position = commander.getComponent(Position.class);
        assertThat(position.x).isCloseTo(fellAt, Offset.offset(TOLERANCE));
        assertThat(position.y).isCloseTo(50f, Offset.offset(TOLERANCE));
    }

    /**
     * The blow is resolved after the commander has already acted this tick, so the shot that leaves on
     * the tick he dies is his last one fairly taken. What must not happen is another one after that.
     */
    @Test
    void firesNothingMoreOnceItIsDying() {
        Simulation simulation = simulation();
        CommanderInput shooting = new CommanderInput(0f, 0f, 90f, 50f, true, false);
        kill(simulation.spawnCommander(50f, 50f));
        simulation.step(shooting);
        int firedBeforeDying = count(simulation, Projectile.class);

        steps(simulation, ticksIn(DEATH_THROES) - 2, shooting);

        assertThat(count(simulation, Projectile.class)).isEqualTo(firedBeforeDying);
    }

    /** Otherwise the crowd that killed him spends the whole beat hitting a body that cannot answer. */
    @Test
    void protectsTheBodyFromAnythingElseThatLands() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        kill(commander);
        simulation.step();

        assertThat(commander.getComponent(Invulnerable.class).active()).isTrue();
    }

    /** The pack stops rather than piling on, which is what makes the beat read as a beat. */
    @Test
    void stopsEnemiesChasingABodyOnTheGround() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(80f, 50f);

        kill(commander);
        steps(simulation, 4);

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isCloseTo(0f, Offset.offset(TOLERANCE));
        assertThat(velocity.y).isCloseTo(0f, Offset.offset(TOLERANCE));
    }

    /** An enemy lies there too, so a body killed in a clinch is visibly part of it. */
    @Test
    void leavesAnEnemyBodyOnTheFieldForItsThroes() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        enemy.getComponent(Damage.class).land(ENEMY_HEALTH, Harm.KILL);
        simulation.step();

        assertThat(count(simulation, Enemy.class)).isEqualTo(1);
        assertThat(enemy.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void takesAnEnemyBodyAwayOnceItsThroesAreOver() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        enemy.getComponent(Damage.class).land(ENEMY_HEALTH, Harm.KILL);
        steps(simulation, ticksIn(ENEMY_THROES) + 2);

        assertThat(count(simulation, Enemy.class)).isZero();
    }

    /** A body is scenery: solid so it is in the way, immovable so it stays where it fell. */
    @Test
    void turnsABodyIntoAnObstacle() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        enemy.getComponent(Damage.class).land(ENEMY_HEALTH, Harm.KILL);
        simulation.step();

        Collider collider = enemy.getComponent(Collider.class);
        assertThat(collider.solid).isTrue();
        assertThat(collider.movable).isFalse();
    }

    /** Whatever it was doing when it died, it stops doing it where it stood. */
    @Test
    void stopsABodyDeadRatherThanLettingItCoast() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(60f, 50f);
        simulation.spawnCommander(50f, 50f);
        steps(simulation, 4);

        enemy.getComponent(Damage.class).land(ENEMY_HEALTH, Harm.KILL);
        simulation.step();

        Velocity velocity = enemy.getComponent(Velocity.class);
        assertThat(velocity.x).isCloseTo(0f, Offset.offset(TOLERANCE));
        assertThat(velocity.y).isCloseTo(0f, Offset.offset(TOLERANCE));
    }

    /** A second blow on a body already going down must not restart the clock and strand it there. */
    @Test
    void doesNotRestartTheThroesWhenSomethingHitsTheBodyAgain() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        kill(commander);
        simulation.step();
        int afterFirst = commander.getComponent(Dying.class).remainingTicks;
        commander.getComponent(Damage.class).land(0, Harm.KILL);
        simulation.step();

        assertThat(commander.getComponent(Dying.class).remainingTicks).isLessThan(afterFirst);
    }

    private static void kill(Entity entity) {
        entity.getComponent(Damage.class).land(ENEMY_HEALTH, Harm.KILL);
        entity.getComponent(Invulnerable.class).remainingTicks = 0;
    }
}
