package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_ATTACK_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WEAPON_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.clawingForwardOnly;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * What contact costs. One rule covers claws and bullets alike: nothing happens between two entities
 * on the same side, and across sides whoever carries an attack that is off cooldown lands it.
 */
class CombatTest {

    /** Enough that the commander survives a few hits, so a cooldown is something a test can watch. */
    private static final float SURVIVABLE_HIT = 40f;

    /** Where the enemy stands. The commander is put far to its right, so it walks that way. */
    private static final float ENEMY_X = 50f;

    private static final float LANE = 50f;

    /** Far enough right to be walked towards for the whole test, and invulnerable throughout. */
    private static final float FAR_RIGHT = 90f;

    /** Overlapping the enemy, on the side it is walking away from. */
    private static final float BEHIND_THE_ENEMY = ENEMY_X - 1.5f;

    /** Overlapping the enemy, dead ahead of it. */
    private static final float IN_FRONT_OF_THE_ENEMY = ENEMY_X + 1.5f;

    /**
     * Walking into the back of something that has not turned round used to kill outright, which read
     * as the game cheating: there was nothing on screen saying the thing could reach behind itself.
     * A creature's claws reach into an arc in front of it, and which way it is going is which way it
     * is facing.
     */
    @Test
    void landsNoBlowOnWhatIsBehindIt() {
        Simulation simulation = clawingForwardOnly();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnCommander(FAR_RIGHT, LANE);
        simulation.spawnEnemy(ENEMY_X, LANE);
        Entity behind = simulation.spawnTrooper(BEHIND_THE_ENEMY, LANE);

        simulation.step();

        assertThat(behind.getComponent(Health.class).current).isEqualTo(AlphaWorlds.TROOPER_HEALTH);
    }

    @Test
    void landsOneOnWhatItIsWalkingAt() {
        Simulation simulation = clawingForwardOnly();
        simulation.setCommanderInvulnerable(true);
        simulation.spawnCommander(FAR_RIGHT, LANE);
        simulation.spawnEnemy(ENEMY_X, LANE);
        Entity ahead = simulation.spawnTrooper(IN_FRONT_OF_THE_ENEMY, LANE);

        simulation.step();

        assertThat(ahead.getComponent(Health.class).current).isLessThan(AlphaWorlds.TROOPER_HEALTH);
    }

    /** Standing perfectly still, it can turn on the spot, so a blind spot is not a place to live. */
    @Test
    void reachesAllRoundWhileItIsNotGoingAnywhere() {
        Simulation simulation = clawingForwardOnly();
        Entity enemy = simulation.spawnEnemy(ENEMY_X, LANE);
        Entity behind = simulation.spawnTrooper(BEHIND_THE_ENEMY, LANE);

        enemy.getComponent(Perception.class).aggro = false;
        steps(simulation, 1);

        assertThat(behind.getComponent(Health.class).current).isLessThan(AlphaWorlds.TROOPER_HEALTH);
    }

    @Test
    void takesTwoShotsToKillAnEnemy() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        hit(simulation, enemy);

        assertThat(enemy.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH - WEAPON_DAMAGE);
        assertThat(count(simulation, Enemy.class)).isEqualTo(1);
    }

    @Test
    void killsAnEnemyWithTheSecondShot() {
        Simulation simulation = simulation();
        Entity enemy = simulation.spawnEnemy(50f, 50f);

        hit(simulation, enemy);
        hit(simulation, enemy);

        assertThat(countLiving(simulation, Enemy.class)).isZero();
    }

    @Test
    void spendsAProjectileOnTheEnemyItHits() {
        Simulation simulation = simulation();
        simulation.spawnEnemy(50f, 50f);

        hit(simulation, null);

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void letsAProjectilePassThroughItsOwnSide() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnProjectile(48f, 50f, 1f, 0f, Side.PLAYER);

        simulation.step();

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
        assertThat(commander.getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH);
    }

    @Test
    void letsAnAlienProjectileHitTheCommander() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnProjectile(48f, 50f, 1f, 0f, Side.ALIEN);

        simulation.step();

        assertThat(commander.getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH - WEAPON_DAMAGE);
    }

    /**
     * The killing is instant; only the removal waits. The body lying on the field for its throes is
     * the dying animation's window and not a reprieve - it is out of health and cannot act again.
     */
    @Test
    void killsTheCommanderTheMomentAnEnemyTouchesIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Health.class).current).isLessThanOrEqualTo(0f);
        assertThat(commander.getComponent(Dying.class).begun).isTrue();
    }

    @Test
    void neverLetsEnemiesHurtEachOther() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnEnemy(50f, 50f);
        Entity second = simulation.spawnEnemy(50.2f, 50f);

        for (int i = 0; i < 60; i++) {
            simulation.step();
        }

        assertThat(first.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(second.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    @Test
    void makesAnEnemyWaitOutItsCooldownBetweenStrikes() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);

        for (int i = 0; i < ticksIn(ENEMY_ATTACK_COOLDOWN) - 2; i++) {
            simulation.step();
        }

        assertThat(commander.getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH - SURVIVABLE_HIT);
    }

    @Test
    void letsAnEnemyStrikeAgainOnceItsCooldownHasPassed() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);

        for (int i = 0; i < ticksIn(ENEMY_ATTACK_COOLDOWN) + 4; i++) {
            simulation.step();
        }

        assertThat(commander.getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH - SURVIVABLE_HIT * 2f);
    }

    /** A crowd must not delete a unit in a single tick, which is what the victim's recovery window is for. */
    @Test
    void letsOnlyOneOfACrowdLandAHitAtATime() {
        Simulation simulation = survivableWorld();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.spawnEnemy(50f, 50f);
        simulation.spawnEnemy(50.1f, 50f);
        simulation.spawnEnemy(49.9f, 50f);

        simulation.step();

        assertThat(commander.getComponent(Health.class).current).isEqualTo(COMMANDER_HEALTH - SURVIVABLE_HIT);
    }

    /** Fires a projectile into whatever is at (50, 50) and lets it land. */
    private static void hit(Simulation simulation, Entity ignored) {
        simulation.spawnProjectile(49.5f, 50f, 1f, 0f);
        simulation.step();
    }

    private static Simulation survivableWorld() {
        SimConfig config = AlphaWorlds.config();
        return new Simulation(config.withUnit(UnitKind.GRUNT, grunt -> grunt.withContactDamage(SURVIVABLE_HIT)));
    }
}
