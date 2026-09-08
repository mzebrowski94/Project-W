package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;

/**
 * Shared fixture for the alpha-phase system tests: one simulation config with numbers chosen to make
 * arithmetic obvious rather than to play well.
 *
 * <p>At 60 ticks per second a speed of 60 is exactly one world unit per tick, which is why the
 * speeds here are multiples of 60 - an assertion can then be read without a calculator.
 *
 * <p>New system tests should build worlds from here rather than assembling a {@link SimConfig} by
 * hand; a config literal in a test rots the moment the record gains a field.
 *
 * <p><strong>Leaping is off in the base config.</strong> Most tests are about something else and want
 * an enemy that walks predictably; {@link #leaping()} switches it on with a fixed wind-up so a test
 * can know which tick the jump comes on.
 */
public final class AlphaWorlds {

    public static final float TICK = 1f / 60f;
    public static final float ARENA = 100f;
    public static final float COMMANDER_RADIUS = 1f;
    public static final float COMMANDER_SPEED = 60f;
    public static final float ENEMY_RADIUS = 1f;
    public static final float ENEMY_SPEED = 30f;
    public static final float COOLDOWN = 0.5f;

    /**
     * One world unit per tick. Slow enough that a projectile fired in the middle of the arena is
     * still inside it many ticks later, so a test can count shots by counting live projectiles.
     */
    public static final float PROJECTILE_SPEED = 60f;

    public static final float PROJECTILE_RADIUS = 0.5f;
    public static final float OBSTACLE_RADIUS = 2f;

    public static final float COMMANDER_HEALTH = 100f;
    public static final float ENEMY_HEALTH = 100f;
    public static final float WEAPON_DAMAGE = 50f;
    public static final float ENEMY_CONTACT_DAMAGE = 100f;
    public static final float ENEMY_ATTACK_COOLDOWN = 1f;
    public static final float VICTIM_INVULNERABLE = 0.5f;

    /** Shorter than the enemy's attack cooldown, so a test can watch it end and the next blow land. */
    public static final float ENEMY_LOCK = 0.5f;

    /** Half a second, not the shipped three - a test that waits three seconds of ticks is a slow test. */
    public static final float DEATH_THROES = 0.5f;

    /** A second, not the shipped three - a test that waits three seconds of ticks is a slow test. */
    public static final float RESPAWN_DELAY = 1f;

    public static final float RESPAWN_MARGIN = 5f;
    public static final float RESPAWN_INVULNERABLE = 0.5f;

    public static final float MELEE_COOLDOWN = 0.5f;
    public static final float MELEE_ACTIVE = 0.1f;
    /** Short, like the shipped weapon: the sweep must not land until the target is nearly touching. */
    public static final float MELEE_REACH = 1.5f;

    public static final float MELEE_RADIUS = 1.2f;

    /** Enough to kill an enemy outright and leave a brute standing. That contrast is the mechanic. */
    public static final float MELEE_DAMAGE = 100f;

    public static final float MELEE_ARC = 180f;
    public static final float MELEE_LOCK = 0.25f;

    /** One world unit per tick, so a knocked-back brute's travel can be read straight off. */
    public static final float KNOCKBACK_SPEED = 60f;

    public static final float KNOCKBACK_DECAY = 60f;

    /** As long as the melee lock, so a lethal answer holds the two together for the whole beat. */
    public static final float ENEMY_THROES = 0.25f;

    public static final float BRUTE_RADIUS = 1f;
    public static final float BRUTE_SPEED = 30f;
    public static final float BRUTE_HEALTH = 300f;

    public static final float LEAP_TRIGGER_RANGE = 20f;

    /** One world unit per tick while airborne. */
    public static final float LEAP_SPEED = 60f;

    public static final float LEAP_AIRBORNE = 0.2f;
    public static final int LEAP_SEQUENCE = 2;
    public static final float LEAP_BETWEEN = 0.1f;
    public static final float LEAP_COOLDOWN = 1f;

    /** Fixed, so a test knows exactly which tick the jump comes on. Variety is tested on its own. */
    public static final float LEAP_HESITATION = 0.1f;

    private AlphaWorlds() {}

    public static SimConfig config() {
        return new SimConfig(
                1L,
                TICK,
                5,
                new SimConfig.ArenaConfig(ARENA, ARENA),
                new SimConfig.CommanderConfig(
                        COMMANDER_RADIUS,
                        COMMANDER_SPEED,
                        COMMANDER_HEALTH,
                        DEATH_THROES,
                        RESPAWN_DELAY,
                        RESPAWN_MARGIN,
                        RESPAWN_INVULNERABLE),
                new SimConfig.EnemyConfig(
                        ENEMY_RADIUS,
                        ENEMY_SPEED,
                        0,
                        ENEMY_HEALTH,
                        ENEMY_CONTACT_DAMAGE,
                        ENEMY_ATTACK_COOLDOWN,
                        VICTIM_INVULNERABLE,
                        ENEMY_LOCK,
                        ENEMY_THROES,
                        SimConfig.LeapConfig.off()),
                new SimConfig.BruteConfig(
                        BRUTE_RADIUS,
                        BRUTE_SPEED,
                        0,
                        BRUTE_HEALTH,
                        ENEMY_CONTACT_DAMAGE,
                        ENEMY_ATTACK_COOLDOWN,
                        VICTIM_INVULNERABLE,
                        ENEMY_LOCK,
                        ENEMY_THROES,
                        SimConfig.LeapConfig.off()),
                new SimConfig.WeaponConfig(COOLDOWN, PROJECTILE_SPEED, PROJECTILE_RADIUS, WEAPON_DAMAGE),
                new SimConfig.MeleeConfig(
                        MELEE_COOLDOWN,
                        0f,
                        MELEE_ACTIVE,
                        MELEE_REACH,
                        MELEE_RADIUS,
                        MELEE_DAMAGE,
                        MELEE_ARC,
                        MELEE_LOCK,
                        KNOCKBACK_SPEED,
                        KNOCKBACK_DECAY),
                new SimConfig.ObstacleConfig(OBSTACLE_RADIUS, 0),
                new SimConfig.CollisionConfig(3));
    }

    public static Simulation simulation() {
        return new Simulation(config());
    }

    /** A leap with a fixed wind-up, so a test can count ticks to the jump instead of guessing. */
    public static SimConfig.LeapConfig leapConfig() {
        return new SimConfig.LeapConfig(
                true,
                LEAP_TRIGGER_RANGE,
                LEAP_SPEED,
                LEAP_AIRBORNE,
                LEAP_SEQUENCE,
                LEAP_BETWEEN,
                LEAP_COOLDOWN,
                LEAP_HESITATION,
                LEAP_HESITATION);
    }

    /** The base world with both enemy kinds leaping on a fixed wind-up. */
    public static Simulation leaping() {
        SimConfig config = config();
        return new Simulation(config.with(config.enemy().withLeap(leapConfig()))
                .with(config.brute().withLeap(leapConfig())));
    }

    /**
     * Leaping enemies that cannot hurt anything. Most leap tests are about where a unit goes and when,
     * and a lethal enemy landing on the commander ends the run before the interesting part.
     */
    public static Simulation harmlessLeaping() {
        SimConfig config = config();
        return new Simulation(config.with(config.enemy().withContactDamage(0f).withLeap(leapConfig()))
                .with(config.brute().withContactDamage(0f).withLeap(leapConfig())));
    }

    /** Enemies that walk but cannot hurt anything, for tests about a swing rather than about a fight. */
    public static Simulation harmless() {
        SimConfig config = config();
        return new Simulation(config.with(config.enemy().withContactDamage(0f))
                .with(config.brute().withContactDamage(0f)));
    }

    /** How many ticks make up this many seconds, for tests that wait out a cooldown or a respawn. */
    public static int ticksIn(float seconds) {
        return Math.round(seconds / TICK);
    }

    public static int count(Simulation simulation, Class<? extends Component> marker) {
        return simulation.engine().getEntitiesFor(Family.all(marker).get()).size();
    }

    /**
     * How many of these are still on their feet. Everything that dies now leaves a body for its
     * throes, so a plain count answers "how many are on the field", which is rarely the question a
     * combat test is asking.
     */
    public static int countLiving(Simulation simulation, Class<? extends Component> marker) {
        ImmutableArray<Entity> all =
                simulation.engine().getEntitiesFor(Family.all(marker).get());
        int living = 0;
        for (int i = 0; i < all.size(); i++) {
            Dying dying = all.get(i).getComponent(Dying.class);
            if (dying == null || !dying.begun) {
                living++;
            }
        }
        return living;
    }

    /** Steps the world the given number of times with nothing driving the commander. */
    public static void steps(Simulation simulation, int count) {
        for (int i = 0; i < count; i++) {
            simulation.step();
        }
    }

    /** Steps the world the given number of times with the same input every tick. */
    public static void steps(Simulation simulation, int count, CommanderInput input) {
        for (int i = 0; i < count; i++) {
            simulation.step(input);
        }
    }

    /**
     * A bare moving body - position, velocity and extent, and no role at all. Nothing in the game
     * spawns one; it lets movement and determinism be tested without dragging a commander, a weapon
     * and an arena into the fixture.
     *
     * <p>It has no {@code Collider}, so the constraint solver ignores it entirely. That is the point:
     * a movement test should not have to reason about collision.
     */
    public static Entity spawnBody(
            Simulation simulation, float x, float y, float velocityX, float velocityY, float radius) {
        PooledEngine engine = simulation.engine();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(velocityX, velocityY));
        entity.add(engine.createComponent(Body.class).set(radius));
        engine.addEntity(entity);
        return entity;
    }
}
