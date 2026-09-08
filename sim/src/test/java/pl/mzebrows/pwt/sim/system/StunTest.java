package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_LOCK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * What a landed blow costs both people involved in it.
 *
 * <p>A blow that carries a lock holds the attacker as well as the victim. That single rule is what
 * turns a hit from an event into an exchange: an enemy that reaches the commander is committed for
 * three seconds and cannot simply walk on to the next target, and a sweep that answers a leap costs
 * the commander a shorter opening of his own.
 *
 * <p>The other half of the rule is that a stun stops an entity <em>acting</em>, not <em>moving</em>.
 * If it stopped movement, knockback could not exist, and a stunned unit would become a wall the crowd
 * behind it could not push through.
 */
class StunTest {

    /** Low enough that the commander survives the blow and can be watched being held by it. */
    private static final float SURVIVABLE_HIT = 30f;

    private static final float TOLERANCE = 1e-4f;

    /** A fatal blow tangles the two of them together - that is the clinch, and both are committed. */
    @Test
    void holdsBothTheAttackerAndItsVictimWhenTheBlowKills() {
        Simulation simulation = AlphaWorlds.simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Stunned.class).active()).isTrue();
        assertThat(enemy.getComponent(Stunned.class).active()).isTrue();
        assertThat(enemy.getComponent(Stunned.class).grappling).isTrue();
    }

    /**
     * A blow that is survived is a shove, not a clinch. Only the one that took it is held; the one
     * that landed it pushed something away and is free to keep fighting.
     */
    @Test
    void holdsOnlyTheVictimWhenTheBlowIsSurvived() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Stunned.class).active()).isTrue();
        assertThat(commander.getComponent(Stunned.class).grappling).isFalse();
        assertThat(enemy.getComponent(Stunned.class).active()).isFalse();
    }

    @Test
    void letsTheVictimGoOnceTheLockHasPassed() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);

        steps(simulation, ticksIn(ENEMY_LOCK) + 2);

        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    /**
     * He can still be pushed around - the enemy that hit him is not held either, and keeps shouldering
     * into him - but none of that movement is his. Asserting he does not travel the way he is pressing
     * is the honest version of "the input is ignored"; asserting he does not move at all would be
     * asserting that the solver stops working.
     */
    @Test
    void ignoresTheMoveInputOfAHeldCommander() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);

        simulation.step();
        float afterHit = commander.getComponent(Position.class).x;
        steps(simulation, ticksIn(ENEMY_LOCK) - 4, new CommanderInput(1f, 0f, 0f, 0f, false, false));

        assertThat(commander.getComponent(Position.class).x).isLessThanOrEqualTo(afterHit);
    }

    /**
     * The blow lands after the commander has already acted this tick, so one shot still leaves on the
     * tick he is hit. The lock is about every tick after that one.
     */
    @Test
    void firesNothingMoreWhileTheCommanderIsHeld() {
        Simulation simulation = survivableWorld();
        CommanderInput shooting = new CommanderInput(0f, 0f, 90f, 50f, true, false);
        simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);
        simulation.step(shooting);
        int firedBeforeLock = count(simulation, Projectile.class);

        steps(simulation, ticksIn(ENEMY_LOCK) - 4, shooting);

        assertThat(count(simulation, Projectile.class)).isEqualTo(firedBeforeLock);
    }

    /**
     * A held attacker lands nothing. This is what the opening after a parry is actually made of: the
     * thing you just shoved cannot hit you while it is recovering.
     *
     * <p>The stun is applied directly rather than earned through a swing, on purpose. Choreographing a
     * real exchange here would mean racing the enemy's approach against the sweep's arc, and a test
     * that passes because the timing happened to work out is not testing the rule.
     */
    @Test
    void landsNoBlowFromAHeldAttacker() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity enemy = simulation.spawnEnemy(50f, 50f);
        enemy.getComponent(Stunned.class).apply(ENEMY_LOCK, 0f);

        simulation.step();

        assertThat(commander.getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH);
    }

    /** The solver still owns where a held unit ends up, so a crowd is not stopped by one stunned body. */
    @Test
    void stillLetsAHeldUnitBeShovedOutOfTheWay() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Position.class).x).isNotEqualTo(50f);
    }

    @Test
    void bleedsAKnockbackAwayOverTheStun() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        commander.getComponent(Stunned.class).apply(ENEMY_LOCK, AlphaWorlds.KNOCKBACK_DECAY);
        commander.getComponent(Velocity.class).set(AlphaWorlds.KNOCKBACK_SPEED, 0f);

        steps(simulation, ticksIn(AlphaWorlds.KNOCKBACK_SPEED / AlphaWorlds.KNOCKBACK_DECAY) + 2);

        Velocity velocity = commander.getComponent(Velocity.class);
        assertThat(velocity.x).isCloseTo(0f, Offset.offset(TOLERANCE));
        assertThat(velocity.y).isCloseTo(0f, Offset.offset(TOLERANCE));
    }

    /** Nothing is owed a stun it never asked for: a bullet does not commit the shooter to anything. */
    @Test
    void holdsNobodyWhenAProjectileLands() {
        Simulation simulation = survivableWorld();
        Entity enemy = simulation.spawnEnemy(50f, 50f);
        simulation.spawnProjectile(49f, 50f, 1f, 0f);

        simulation.step();

        assertThat(enemy.getComponent(Stunned.class).active()).isFalse();
    }

    private static Simulation survivableWorld() {
        SimConfig config = AlphaWorlds.config();
        return new Simulation(config.with(config.enemy().withContactDamage(SURVIVABLE_HIT)));
    }
}
