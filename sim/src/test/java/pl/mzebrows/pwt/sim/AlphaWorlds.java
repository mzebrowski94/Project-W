package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.function.UnaryOperator;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.RangedDef;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

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
 * <p><strong>Every strain is always aggressive in the base config.</strong> Most tests are about what
 * a unit does once it is fighting rather than about whether it has noticed; {@link #watching()}
 * switches detection on for the tests that are about noticing.
 *
 * <p><strong>Leaping is off in the base config.</strong> Most tests are about something else and want
 * an enemy that walks predictably; {@link #leaping()} switches it on with a fixed wind-up so a test
 * can know which tick the jump comes on.
 */
public final class AlphaWorlds {

    public static final float TICK = 1f / 60f;
    /** Five world units, as the game ships. Twenty of them square is the 100-unit test arena. */
    public static final float TILE = 5f;

    public static final int ARENA_TILES = 20;
    public static final float ARENA = ARENA_TILES * TILE;
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

    /**
     * How fast a body finishes its lunge into the commander, and how fast that bleeds off. It has to
     * carry further than the sweep can leave between them - {@code MELEE_REACH + MELEE_RADIUS -
     * COMMANDER_RADIUS}, or 1.7 here - and {@code SimConfig} refuses a config where it does not, because
     * a carry that stops short leaves the freeze playing across exactly the gap it exists to close.
     */
    public static final float CLINCH_CARRY_SPEED = 25f;

    public static final float CLINCH_CARRY_DECAY = 150f;

    /** One world unit per tick, so a knocked-back brute's travel can be read straight off. */
    public static final float KNOCKBACK_SPEED = 60f;

    public static final float KNOCKBACK_DECAY = 60f;

    /** One world unit per tick, so a throw's travel can be read straight off. */
    public static final float GRENADE_SPEED = 60f;

    /** Twenty units of flight: far enough to bounce inside the test arena, short enough to wait out. */
    public static final float GRENADE_RANGE = 20f;

    public static final float GRENADE_RADIUS = 0.5f;

    /** Small, so a test about the throw is not also a test about what the blast happened to catch. */
    public static final float GRENADE_BLAST_RADIUS = 4f;

    /** Shorter than a whole flight, so a test can watch a second grenade join the first in the air. */
    public static final float GRENADE_COOLDOWN = 0.2f;

    /** One of anything sets it off. A barrel is a trigger, not something to grind down. */
    public static final float BARREL_HEALTH = 1f;

    /** Wider than the barrel by a long way: what a barrel is for is what happens around it. */
    public static final float BARREL_BLAST_RADIUS = 8f;

    /** Half a second: long enough to be interrupted in a test, short enough not to slow one down. */
    public static final float CASUALTY_STABILISE = 0.5f;

    /** Half a bar back. Enough that a revived man is visibly not a whole one. */
    public static final float REVIVE_HEALTH_FRACTION = 0.5f;

    public static final float REVIVE_INVULNERABLE = 0.25f;

    /** Stopped while he is hauled, as the game ships it. */
    public static final float DRAGGED_BLEED_FACTOR = 0f;

    /** Double, not the shipped two and a half: round, so a test can read the cost straight off. */
    public static final float HAULED_STABILISE_FACTOR = 2f;

    /** Half pace while hauling, so a test can read the distance covered straight off. */
    public static final float DRAG_SPEED_FACTOR = 0.5f;

    public static final float DRAG_LATERAL_FACTOR = 0.25f;

    public static final float DRAG_GAP = 0.5f;

    /** Arm's length in the fixtures, and round, so a test can stand just inside or just outside it. */
    public static final float ACTION_REACH = 3f;

    /**
     * Two seconds, not the shipped eight - a test that waits out a bleed-out is a slow test. Several
     * times {@link #COOLDOWN} on purpose, so a test can watch a man lie there through a whole weapon
     * cooldown and see that he does not use it.
     */
    public static final float CASUALTY_BLEED_OUT = 2f;

    /** Wide enough to catch two units either side of the middle and leave a third of them clear. */
    public static final float BLAST_RADIUS = 6f;

    /** Enough to kill anything in the fixture, which is the rule: a blast is fatal or it is nothing. */
    public static final float BLAST_DAMAGE = 1000f;

    /** Long enough that a test can put something into the tail of a blast and watch it walk away. */
    public static final float BLAST_SECONDS = 0.2f;

    /** How hard the ground moves. Nothing in the simulation reads it; the view is the client's. */
    public static final float BLAST_SHAKE = 3f;

    /** How far that carries, as a multiple of the blast's own radius. */
    public static final float BLAST_SHAKE_REACH = 2.5f;

    /** As long as the melee lock, so a lethal answer holds the two together for the whole beat. */
    public static final float ENEMY_THROES = 0.25f;

    public static final float BRUTE_RADIUS = 1f;
    public static final float BRUTE_SPEED = 30f;
    public static final float BRUTE_HEALTH = 300f;

    /** How close the commander must come before a watching strain notices him, in {@link #watching()}. */
    public static final float DETECTION_RANGE = 20f;

    /** How far he must get to be lost again. Wider than the detection range, which is the hysteresis. */
    public static final float LOSE_RANGE = 30f;

    /** How close something edible has to be before an idle unit goes for it. Shorter than either. */
    public static final float SECONDARY_RANGE = 10f;

    /** How far an idle unit roams from where it was spawned, in {@link #wandering()}. */
    public static final float WANDER_RADIUS = 10f;

    /** Half speed while idle, so an ambling unit reads differently from one that has seen you. */
    public static final float WANDER_SPEED_FACTOR = 0.5f;

    public static final float WANDER_ARRIVE_RADIUS = 1f;

    /** Fixed, so a test knows exactly how long a unit stands still between waypoints. */
    public static final float WANDER_PAUSE = 0.2f;

    public static final float WANDER_TIMEOUT = 3f;

    /** How close something has to be, surface to surface, before it is steered around. */
    public static final float AVOID_RANGE = 12f;

    public static final float AVOID_LOOKAHEAD = 0.5f;
    public static final float AVOID_WEIGHT = 2f;

    /** Big enough to be worth shooting at and small enough to slip a shot. */
    public static final float RABBIT_RADIUS = 1f;

    public static final float RABBIT_SPEED = 30f;

    /** One hit, whatever hit it. Denying a meal should not be a marksmanship exercise twice over. */
    public static final float RABBIT_HEALTH = 1f;

    public static final float RABBIT_THROES = 0.2f;

    /** Further than a shot travels in the time it takes to arrive, so the rabbit always gets to think. */
    public static final float DODGE_REACTION_RANGE = 25f;

    public static final float DODGE_SPEED = 60f;
    public static final float DODGE_DURATION = 0.3f;
    public static final float DODGE_COMMIT = 0.5f;

    /** Wide enough that a shot passing two units away still counts as coming at it. */
    public static final float DODGE_MARGIN = 1.5f;

    public static final float SPITTER_RADIUS = 1f;

    public static final float SPITTER_SPEED = 30f;

    public static final float SPITTER_HEALTH = 100f;

    /** Shorter than the detection range, so a spitter notices well before it may shoot. */
    public static final float SPIT_RANGE = 15f;

    /** Half a second, so a test can count the shots a spitter gets off without waiting about. */
    public static final float SPIT_COOLDOWN = 0.5f;

    /** One world unit per tick, so a spit's travel can be read straight off. */
    public static final float SPIT_SPEED = 60f;

    /** Two of them put a man of any kind down, which is what the shipped numbers do too. */
    public static final float SPIT_DAMAGE = 50f;

    /** Wide enough that a test can tell a spread shot from a straight one without a microscope. */
    public static final float SPIT_SPREAD_DEGREES = 40f;

    public static final float LEAP_TRIGGER_RANGE = 20f;

    /** One world unit per tick while airborne. */
    public static final float LEAP_SPEED = 60f;

    public static final float LEAP_AIRBORNE = 0.2f;
    public static final int LEAP_SEQUENCE = 2;
    public static final float LEAP_BETWEEN = 0.1f;
    public static final float LEAP_COOLDOWN = 1f;

    /** Fixed, so a test knows exactly which tick the jump comes on. Variety is tested on its own. */
    public static final float LEAP_HESITATION = 0.1f;

    /** Big enough that a unit spawned on a pickup is touching it on the first tick. */
    public static final float PICKUP_RADIUS = 1f;

    /** What one mouthful is worth here, so a test can count them. */
    public static final Grant MOUTHFUL = new Grant.Feed(1);

    /** Mouthfuls a grunt needs before it turns into a brute, in {@link #evolving()}. */
    public static final int FEED_TO_EVOLVE = 3;

    /** Slower and far harder-hitting than the sidearm, so a swap is visible in one shot. */
    public static final float RAILGUN_COOLDOWN = 1f;

    public static final float RAILGUN_DAMAGE = 400f;

    public static final float TROOPER_RADIUS = 1f;

    public static final float TROOPER_SPEED = 20f;

    public static final float TROOPER_HEALTH = 100f;

    /** Small on purpose: a test that spawns a squad should be able to name every member of it. */
    public static final int SQUAD_SIZE = 3;

    /** Wide enough that a squad fits inside it, as the shipped one is. */
    public static final float POINT_RADIUS = 6f;

    /** Short, so a test that waits out a capture does not wait out five seconds of ticks. */
    public static final float POINT_CAPTURE_SECONDS = 1f;

    public static final float POINT_AGGRO_RADIUS = 26f;

    /** Half and half, so a test can see both the advance and the weave in one run. */
    public static final float TROOPER_URGENCY = 0.5f;

    public static final float TROOPER_SCAN_SPREAD = 0.8f;

    /** Fixed rather than a range, so a test counting ticks to a sweep is not guessing. */
    public static final float TROOPER_SCAN_SECONDS = 1f;

    public static final float TROOPER_ARRIVE_RADIUS = 3f;

    public static final float TROOPER_SIGHT = 34f;

    public static final float TROOPER_HOLD = 42f;

    public static final float TROOPER_ARC = 100f;

    /** Off in the base fixture: a rare event firing at random would make every other test flaky. */
    public static final float TROOPER_STRIKE_CHANCE = 0f;

    /** How far the commander's voice carries in the fixtures. */
    public static final float ORDER_RADIUS = 22f;

    public static final float MOVE_OUT_DISTANCE = 40f;

    public static final float MOVE_OUT_FACTOR = 1.6f;

    public static final float FOLLOW_DISTANCE = 7f;

    public static final int SPAWN_COL = 1;

    public static final int SPAWN_WIDTH = 2;

    public static final int SPAWN_HEIGHT = 5;

    private AlphaWorlds() {}

    public static SimConfig config() {
        return new SimConfig(
                1L,
                TICK,
                5,
                new SimConfig.MapConfig(ARENA_TILES, ARENA_TILES, TILE, 1),
                new SimConfig.CommanderConfig(
                        COMMANDER_RADIUS,
                        COMMANDER_SPEED,
                        COMMANDER_HEALTH,
                        DEATH_THROES,
                        RESPAWN_DELAY,
                        RESPAWN_MARGIN,
                        RESPAWN_INVULNERABLE),
                new SimConfig.TrooperConfig(
                        TROOPER_RADIUS,
                        TROOPER_SPEED,
                        SQUAD_SIZE,
                        TROOPER_HEALTH,
                        DEATH_THROES,
                        WeaponKind.SIDEARM,
                        AvoidanceDef.off(),
                        TROOPER_URGENCY,
                        TROOPER_SCAN_SPREAD,
                        TROOPER_SCAN_SECONDS,
                        TROOPER_SCAN_SECONDS,
                        TROOPER_ARRIVE_RADIUS,
                        new PerceptionDef(false, true, TROOPER_SIGHT, TROOPER_HOLD, 0f, TROOPER_ARC, true),
                        ORDER_RADIUS,
                        MOVE_OUT_DISTANCE,
                        MOVE_OUT_FACTOR,
                        FOLLOW_DISTANCE,
                        TROOPER_STRIKE_CHANCE),
                new SimConfig.SpawnConfig(SPAWN_COL, SPAWN_WIDTH, SPAWN_HEIGHT),
                Defs.<UnitKind, UnitDef>builder(UnitKind.class)
                        .put(UnitKind.GRUNT, strain(ENEMY_RADIUS, ENEMY_SPEED, ENEMY_HEALTH, Look.GRUNT))
                        .put(UnitKind.BRUTE, strain(BRUTE_RADIUS, BRUTE_SPEED, BRUTE_HEALTH, Look.BRUTE))
                        .put(UnitKind.SPITTER, strain(SPITTER_RADIUS, SPITTER_SPEED, SPITTER_HEALTH, Look.SPITTER))
                        .build(),
                Defs.<PickupKind, PickupDef>builder(PickupKind.class)
                        .put(PickupKind.MUSHROOM, new PickupDef(PICKUP_RADIUS, Look.MUSHROOM, Grant.NOTHING, MOUTHFUL))
                        .put(
                                PickupKind.FLOWER,
                                new PickupDef(
                                        PICKUP_RADIUS, Look.FLOWER, Grant.NOTHING, new Grant.Mutate(UnitKind.BRUTE)))
                        .put(
                                PickupKind.FROZEN_TROOPER,
                                new PickupDef(PICKUP_RADIUS, Look.FROZEN_TROOPER, new Grant.Reinforce(1), MOUTHFUL))
                        .put(
                                PickupKind.ARMOURY,
                                new PickupDef(PICKUP_RADIUS, Look.ARMOURY, new Grant.Arm(WeaponKind.RAILGUN), MOUTHFUL))
                        .put(PickupKind.RABBIT, new PickupDef(RABBIT_RADIUS, Look.RABBIT, Grant.NOTHING, MOUTHFUL))
                        .put(
                                PickupKind.BARREL,
                                new PickupDef(PICKUP_RADIUS, Look.BARREL, Grant.NOTHING, Grant.DETONATE))
                        .build(),
                Defs.<WeaponKind, WeaponDef>builder(WeaponKind.class)
                        .put(
                                WeaponKind.SIDEARM,
                                new WeaponDef(
                                        COOLDOWN, PROJECTILE_SPEED, PROJECTILE_RADIUS, WEAPON_DAMAGE, Look.PROJECTILE))
                        .put(
                                WeaponKind.SPIT,
                                new WeaponDef(SPIT_COOLDOWN, SPIT_SPEED, PROJECTILE_RADIUS, SPIT_DAMAGE, Look.SPIT))
                        .put(
                                WeaponKind.RAILGUN,
                                new WeaponDef(
                                        RAILGUN_COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        RAILGUN_DAMAGE,
                                        Look.SLUG))
                        .build(),
                new SimConfig.MeleeConfig(
                        MELEE_COOLDOWN,
                        0f,
                        MELEE_ACTIVE,
                        MELEE_REACH,
                        MELEE_RADIUS,
                        MELEE_DAMAGE,
                        MELEE_ARC,
                        MELEE_LOCK,
                        true,
                        CLINCH_CARRY_SPEED,
                        CLINCH_CARRY_DECAY,
                        KNOCKBACK_SPEED,
                        KNOCKBACK_DECAY),
                new SimConfig.BlastConfig(BLAST_DAMAGE, BLAST_SECONDS, BLAST_SHAKE, BLAST_SHAKE_REACH),
                new SimConfig.GrenadeConfig(
                        GRENADE_COOLDOWN, GRENADE_SPEED, GRENADE_RANGE, GRENADE_RADIUS, GRENADE_BLAST_RADIUS),
                new SimConfig.RabbitConfig(RABBIT_SPEED, RABBIT_HEALTH, RABBIT_THROES, WanderDef.off(), DodgeDef.off()),
                new SimConfig.BarrelConfig(BARREL_HEALTH, BARREL_BLAST_RADIUS),
                new SimConfig.CasualtyConfig(
                        CASUALTY_BLEED_OUT,
                        CASUALTY_STABILISE,
                        REVIVE_HEALTH_FRACTION,
                        REVIVE_INVULNERABLE,
                        DRAGGED_BLEED_FACTOR,
                        HAULED_STABILISE_FACTOR),
                new SimConfig.ActionConfig(ACTION_REACH, DRAG_SPEED_FACTOR, DRAG_LATERAL_FACTOR, DRAG_GAP),
                new SimConfig.ControlPointConfig(POINT_RADIUS, POINT_CAPTURE_SECONDS, POINT_AGGRO_RADIUS),
                new SimConfig.ObstacleConfig(OBSTACLE_RADIUS, 0),
                new SimConfig.CollisionConfig(3));
    }

    /**
     * A strain that differs from the other only in the three numbers a test ever cares about. Nothing
     * evolves and nothing leaps here; both are switched on by the fixtures that are about them.
     */
    private static UnitDef strain(float radius, float speed, float health, Look look) {
        return new UnitDef(
                radius,
                speed,
                0,
                health,
                ENEMY_CONTACT_DAMAGE,
                ENEMY_ATTACK_COOLDOWN,
                VICTIM_INVULNERABLE,
                ENEMY_LOCK,
                ENEMY_THROES,
                0,
                null,
                PerceptionDef.alwaysAggressive(DETECTION_RANGE, LOSE_RANGE),
                WanderDef.off(),
                AvoidanceDef.off(),
                LeapDef.off(),
                RangedDef.off(),
                look);
    }

    /** The same change applied to every strain, for a fixture that wants all of them to behave alike. */
    public static SimConfig everyStrain(SimConfig config, UnaryOperator<UnitDef> change) {
        SimConfig changed = config;
        for (UnitKind kind : UnitKind.values()) {
            changed = changed.withUnit(kind, change);
        }
        return changed;
    }

    /** A world on a map of your own - open ground everywhere else, so only the terrain under test differs. */
    public static Simulation on(TileMap map) {
        return new Simulation(config(), map);
    }

    /** A world on a map of your own with a config of your own, for a test that needs both. */
    public static Simulation on(SimConfig config, TileMap map) {
        return new Simulation(config, map);
    }

    /** How wide a cone a strain that only notices what is in front of it looks through. */
    public static final float ARC_DEGREES = 90f;

    /**
     * A world where every strain notices only what is inside its facing arc, and only through open
     * ground - what a trooper declares (G0015). Enemies do not declare either; this fixture exists so
     * the tests can be written without a trooper in the world.
     */
    public static Simulation watchingInFront(TileMap map) {
        return new Simulation(
                everyStrain(
                        config(),
                        strain -> strain.withPerception(new PerceptionDef(
                                false, true, DETECTION_RANGE, LOSE_RANGE, SECONDARY_RANGE, ARC_DEGREES, true))),
                map);
    }

    /** The test grid, filled with open ground, ready for a test to lay rock into. */
    public static TileMap.Builder ground() {
        return TileMap.builder(ARENA_TILES, ARENA_TILES, TILE).fill(Terrain.PLAIN);
    }

    public static Simulation simulation() {
        return new Simulation(config());
    }

    /** A leap with a fixed wind-up, so a test can count ticks to the jump instead of guessing. */
    public static LeapDef leapConfig() {
        return new LeapDef(
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

    /**
     * A world where grunts grow into brutes. The base config deliberately has nothing evolving, so a
     * test about something else never has a unit turn into a different unit underneath it.
     */
    public static Simulation evolving() {
        return new Simulation(config().withUnit(
                        UnitKind.GRUNT,
                        grunt -> grunt.withFeedToEvolve(FEED_TO_EVOLVE).withEvolvesTo(UnitKind.BRUTE)));
    }

    /**
     * A world where every strain has to notice the commander before it hunts him, at the ranges above.
     * The base config has them always aggressive, so a test about something else never has a pack
     * quietly stand down halfway through it.
     */
    public static Simulation watching() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withPerception(
                        PerceptionDef.allRound(false, true, DETECTION_RANGE, LOSE_RANGE, SECONDARY_RANGE))));
    }

    /**
     * A world where every strain both has to notice the commander and ambles about while it has not.
     * Idling is off in the base config for the same reason leaping is: a test about something else
     * wants an enemy that stays where it was put.
     */
    public static Simulation wandering() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withPerception(
                                PerceptionDef.allRound(false, true, DETECTION_RANGE, LOSE_RANGE, SECONDARY_RANGE))
                        .withWander(new WanderDef(
                                true,
                                WANDER_RADIUS,
                                WANDER_SPEED_FACTOR,
                                WANDER_ARRIVE_RADIUS,
                                WANDER_PAUSE,
                                WANDER_PAUSE,
                                WANDER_TIMEOUT))));
    }

    /**
     * A world where every strain steers around what is in its way. Off in the base config, because a
     * test about where a unit ends up wants it to walk in a straight line to get there.
     */
    public static Simulation avoiding() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withAvoidance(new AvoidanceDef(true, AVOID_RANGE, AVOID_LOOKAHEAD, AVOID_WEIGHT))));
    }

    /**
     * A world where rabbits dodge and stand still otherwise. Wandering is off so a test about a
     * sidestep is not reading a stroll, and the numbers are round so a sidestep can be read off.
     */
    public static Simulation rabbits() {
        return new Simulation(config().withRabbit(config().rabbit()
                .withDodge(new DodgeDef(
                        true, DODGE_REACTION_RANGE, DODGE_SPEED, DODGE_DURATION, DODGE_COMMIT, DODGE_MARGIN))));
    }

    /**
     * A world where every strain holds its ground and shoots, at the ranges above. Shooting is off
     * in the base config for the same reason leaping is: a test about something else wants an enemy
     * that walks at you predictably and does not fill the arena with projectiles.
     */
    public static Simulation shooting() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withRanged(
                        new RangedDef(true, SPIT_RANGE, SPIT_SPREAD_DEGREES, true, WeaponKind.SPIT))));
    }

    /** Shooting straight, for a test that wants to know exactly where a shot went. */
    public static Simulation shootingStraight() {
        return shootingStraight(Maps.uniform(ARENA_TILES, ARENA_TILES, TILE, Terrain.PLAIN));
    }

    /** Shooting straight on ground of your own, for a test about what a shooter will not shoot through. */
    public static Simulation shootingStraight(TileMap map) {
        return new Simulation(
                everyStrain(
                        config(),
                        strain -> strain.withRanged(new RangedDef(true, SPIT_RANGE, 0f, true, WeaponKind.SPIT))),
                map);
    }

    /** The base world with every strain leaping on a fixed wind-up. */
    public static Simulation leaping() {
        return new Simulation(everyStrain(config(), strain -> strain.withLeap(leapConfig())));
    }

    /**
     * Leaping enemies that cannot hurt anything. Most leap tests are about where a unit goes and when,
     * and a lethal enemy landing on the commander ends the run before the interesting part.
     */
    public static Simulation harmlessLeaping() {
        return new Simulation(
                everyStrain(config(), strain -> strain.withContactDamage(0f).withLeap(leapConfig())));
    }

    /** Enemies that walk but cannot hurt anything, for tests about a swing rather than about a fight. */
    public static Simulation harmless() {
        return new Simulation(everyStrain(config(), strain -> strain.withContactDamage(0f)));
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
     * A bare body that moves only where something asks it to, with no perception, no behaviour and no
     * role. Nothing in the game spawns one; it lets the seam between deciding and moving be tested
     * without a commander, a strain and a fight in the fixture.
     */
    public static Entity spawnSteered(Simulation simulation, float x, float y) {
        PooledEngine engine = simulation.engine();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Steering.class));
        engine.addEntity(entity);
        return entity;
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
