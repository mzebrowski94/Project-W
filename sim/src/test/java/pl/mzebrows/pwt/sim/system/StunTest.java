package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_LOCK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_LOCK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

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
    private static final int SURVIVABLE_HIT = 30;

    /** Where the sweep passes halfway through its arc when the commander stands at (50, 50) aiming east. */
    private static final float IN_THE_ARC_X = 53f;

    private static final float STUN = 0.5f;

    private static final CommanderInput SWING_EAST = new CommanderInput(0f, 0f, 60f, 50f, false, true);

    private static final float TOLERANCE = 1e-4f;

    /**
     * A fatal blow tangles the two of them together - that is the clinch, and both are committed.
     *
     * <p>A decay describes a throw bleeding off, and a killing blow throws nothing. Carrying the weapon's
     * decay into a clinch left the survivor coasting on whatever he was doing when he swung - which is
     * how a commander who killed a leaper while backing away slid out from under the body he had just
     * been clinched to.
     */
    @Test
    void clearsTheThrowWhenAHoldBecomesAClinch() {
        Stunned stunned = new Stunned();
        stunned.apply(AlphaWorlds.ticksIn(1f), 60f);

        stunned.clinch(AlphaWorlds.ticksIn(1f));

        assertThat(stunned.knockbackDecay).isZero();
        assertThat(stunned.grappling).isTrue();
    }

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
        Simulation simulation = AlphaWorlds.harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        stepUntilStruck(simulation, brute);

        assertThat(brute.getComponent(Stunned.class).active()).isTrue();
        assertThat(brute.getComponent(Stunned.class).grappling).isFalse();
        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
    }

    @Test
    void letsTheVictimGoOnceTheLockHasPassed() {
        Simulation simulation = AlphaWorlds.harmless();
        simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        stepUntilStruck(simulation, brute);
        steps(simulation, ticksIn(MELEE_LOCK) + 2);

        assertThat(brute.getComponent(Stunned.class).active()).isFalse();
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
        hold(commander);

        steps(simulation, ticksIn(ENEMY_LOCK) - 4, new CommanderInput(1f, 0f, 0f, 0f, false, false));

        assertThat(commander.getComponent(Position.class).x).isLessThanOrEqualTo(50f);
    }

    /**
     * A man is held by a clinch, by ground throwing him off itself, or by jaws - never by a blow he
     * survived, since claws kill him (G0053). The hold is put on him directly, as those do.
     */
    @Test
    void firesNothingWhileTheCommanderIsHeld() {
        Simulation simulation = survivableWorld();
        CommanderInput shooting = new CommanderInput(0f, 0f, 90f, 50f, true, false);
        hold(simulation.spawnCommander(50f, 50f));

        steps(simulation, ticksIn(ENEMY_LOCK) - 4, shooting);

        assertThat(count(simulation, Projectile.class)).isZero();
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
        enemy.getComponent(Stunned.class).apply(AlphaWorlds.ticksIn(ENEMY_LOCK), 0f);

        simulation.step();

        assertThat(unhurt(commander)).isTrue();
    }

    /** The solver still owns where a held unit ends up, so a crowd is not stopped by one stunned body. */
    @Test
    void stillLetsAHeldUnitBeShovedOutOfTheWay() {
        Simulation simulation = AlphaWorlds.harmless();
        Entity commander = simulation.spawnCommander(50f, 50f);
        hold(commander);
        simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Position.class).x).isNotEqualTo(50f);
    }

    @Test
    void bleedsAKnockbackAwayOverTheStun() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        commander.getComponent(Stunned.class).apply(AlphaWorlds.ticksIn(ENEMY_LOCK), AlphaWorlds.KNOCKBACK_DECAY);
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

    /** A gun that declares a stun leaves a man it hits unable to act for as long as it says (G0053). */
    @Test
    void stunsAManAShotFromAStunningGunHits() {
        Simulation simulation = new Simulation(stunningSpit());
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnProjectile(48f, 50f, 1f, 0f, WeaponKind.SPIT, Side.ALIEN);

        simulation.step();

        assertThat(commander.getComponent(Stunned.class).active()).isTrue();
        assertThat(commander.getComponent(Appearance.class).state).isEqualTo(Appearance.State.STUNNED);
    }

    @Test
    void letsHimGoOnceTheStunHasRunOut() {
        Simulation simulation = new Simulation(stunningSpit());
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnProjectile(48f, 50f, 1f, 0f, WeaponKind.SPIT, Side.ALIEN);

        steps(simulation, ticksIn(STUN) + 3);

        assertThat(commander.getComponent(Stunned.class).active()).isFalse();
        assertThat(commander.getComponent(Appearance.class).state).isNotEqualTo(Appearance.State.STUNNED);
    }

    /** A stagger is a flash and a stun an opening: they must not read alike. */
    @Test
    void drawsAStaggerAsSomethingElse() {
        Simulation simulation = AlphaWorlds.harmless();
        simulation.spawnCommander(50f, 50f);
        Entity brute = simulation.spawnBrute(IN_THE_ARC_X, 50f);

        stepUntilStruck(simulation, brute);
        simulation.step();

        assertThat(brute.getComponent(Appearance.class).state).isEqualTo(Appearance.State.STAGGERED);
    }

    private static SimConfig stunningSpit() {
        SimConfig config = AlphaWorlds.config();
        return config.withWeapons(config.weapons().map(WeaponKind.SPIT, gun -> gun.withStunSeconds(STUN)));
    }

    private static void hold(Entity entity) {
        entity.getComponent(Stunned.class).apply(ticksIn(ENEMY_LOCK), 0f);
    }

    /** Swings east until the sweep has landed on the brute, and stops on that tick. */
    private static void stepUntilStruck(Simulation simulation, Entity brute) {
        for (int i = 0; i < ticksIn(MELEE_ACTIVE) + 4; i++) {
            simulation.step(SWING_EAST);
            if (brute.getComponent(Health.class).current < BRUTE_HEALTH) {
                return;
            }
        }
        throw new IllegalStateException("the sweep never reached the brute");
    }

    private static Simulation survivableWorld() {
        SimConfig config = AlphaWorlds.config();
        return new Simulation(config.withUnit(UnitKind.GRUNT, grunt -> grunt.withContactDamage(SURVIVABLE_HIT)));
    }
}
