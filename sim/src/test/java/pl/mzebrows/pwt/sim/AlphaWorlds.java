package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import java.util.List;
import java.util.function.UnaryOperator;
import pl.mzebrows.pwt.map.Maps;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.ApproachDef;
import pl.mzebrows.pwt.sim.def.ArmourDef;
import pl.mzebrows.pwt.sim.def.ArmouryDef;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.GripDef;
import pl.mzebrows.pwt.sim.def.GroundDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.HeatDef;
import pl.mzebrows.pwt.sim.def.HopDef;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.RangedDef;
import pl.mzebrows.pwt.sim.def.ShakeDef;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.TrooperGrenadeDef;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WeaponType;
import pl.mzebrows.pwt.sim.def.WeaveDef;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;
import pl.mzebrows.pwt.sim.system.upgrade.DraftDef;
import pl.mzebrows.pwt.sim.system.upgrade.Effect;
import pl.mzebrows.pwt.sim.system.upgrade.Stat;
import pl.mzebrows.pwt.sim.system.upgrade.StatDef;
import pl.mzebrows.pwt.sim.system.upgrade.Target;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

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

    /** The boss's body: several grunts across, so a ring of shots leaves it well clear of itself. */
    public static final float BOSS_RADIUS = 4f;

    /** Deep enough that nothing a test fires by accident takes it down. */
    public static final int BOSS_HEALTH = 5000;

    public static final float COOLDOWN = 0.5f;

    /**
     * One world unit per tick. Slow enough that a projectile fired in the middle of the arena is
     * still inside it many ticks later, so a test can count shots by counting live projectiles.
     */
    public static final float PROJECTILE_SPEED = 60f;

    public static final float PROJECTILE_RADIUS = 0.5f;
    public static final float OBSTACLE_RADIUS = 2f;

    /** As the game ships it: one wound on his feet, and the next puts him down (G0053). */
    public static final int COMMANDER_MAX_WOUNDS = 1;

    public static final int ENEMY_HEALTH = 100;
    public static final int WEAPON_DAMAGE = 50;
    public static final int ENEMY_CONTACT_DAMAGE = 100;

    /**
     * All round in the base fixture. Most combat tests are about what a blow costs rather than about
     * which way the thing was pointing, and an arc would make every one of them a test about
     * approach angles as well.
     */
    public static final float ENEMY_ATTACK_ARC = PerceptionDef.ALL_ROUND;

    /** Narrow, for the tests that are about the arc: wide enough to walk into and out of. */
    public static final float ENEMY_NARROW_ATTACK_ARC = 90f;

    public static final float ENEMY_ATTACK_COOLDOWN = 1f;
    public static final float VICTIM_INVULNERABLE = 0.5f;

    /** Shorter than the enemy's attack cooldown, so a test can watch it end and the next blow land. */
    public static final float ENEMY_LOCK = 0.5f;

    /** Half a second, not the shipped three - a test that waits three seconds of ticks is a slow test. */
    public static final float DEATH_THROES = 0.5f;

    /** A second, not the shipped three - a test that waits three seconds of ticks is a slow test. */
    public static final float RESPAWN_DELAY = 1f;

    /** How long the shield a commander comes back behind lasts (G0053). */
    public static final float RESPAWN_SHIELD = 0.5f;

    /**
     * How far a commander may get from the middle of the commanders, in the fixtures. A tenth of the
     * arena, so a leashed pair is visibly held and a test does not have to run for a second to see it.
     */
    public static final float LEASH_RADIUS = 10f;

    public static final float MELEE_COOLDOWN = 0.5f;
    public static final float MELEE_ACTIVE = 0.1f;
    /** Short, like the shipped weapon: the sweep must not land until the target is nearly touching. */
    public static final float MELEE_REACH = 1.5f;

    public static final float MELEE_RADIUS = 1.2f;

    /** Enough to kill an enemy outright and leave a brute standing. That contrast is the mechanic. */
    public static final int MELEE_DAMAGE = 100;

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
    public static final int BARREL_HEALTH = 1;

    /** Wider than the barrel by a long way: what a barrel is for is what happens around it. */
    public static final float BARREL_BLAST_RADIUS = 8f;

    /** Half of it, so a test can stand something inside one circle and outside the other. */
    public static final float BARREL_BITTEN_BLAST_RADIUS = 4f;

    /** The mine, in round numbers: a small thing with a trigger a test can walk in and out of. */
    public static final float MINE_RADIUS = 1f;

    /** Wide enough that a test can put something a clear distance outside it. */
    public static final float MINE_TRIGGER_RADIUS = 4f;

    /** Half a second, not the shipped one: a test that waits out a real fuse is a slow test. */
    public static final float MINE_FUSE = 0.5f;

    public static final int MINE_HEALTH = 1;

    /** Off the blast fixture's own radius, so a test cannot confuse the two. */
    public static final float MINE_BLAST_RADIUS = 5f;

    public static final float MINE_FLASH_FROM = 0.3f;

    public static final float MINE_FLASH_TO = 0.1f;

    /** Half a second: long enough to be interrupted in a test, short enough not to slow one down. */
    public static final float CASUALTY_STABILISE = 0.5f;

    /** A handful of ticks: long enough to see a second wound turned away, short enough to wait out. */
    public static final float WOUND_GRACE = 0.25f;

    /** How long ground that wounds a man throws him off itself for. */
    public static final float WOUND_SHOVE = 0.2f;

    /** Half his pace while wounded: round, so a test reads the cost straight off. */
    public static final float WOUND_PACE = 0.5f;

    /** Twice the time between his shots while wounded. */
    public static final float WOUND_COOLDOWN = 2f;

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

    /**
     * Half of it, for the commander's own clock. Deliberately a different number from the one above:
     * a test that asserts he is on his own clock would pass by accident if the two agreed.
     */
    public static final float COMMANDER_BLEED_OUT = 1f;

    /** How many shots one pull of a scattering gun sends in the fixtures. Small enough to count. */
    public static final int PELLETS = 3;

    /** A cone wide enough that a pellet visibly leaves off true, and narrow enough to stay a cone. */
    public static final float SCATTER_DEGREES = 40f;

    /** A flight length short enough that a test can watch a shot go out without waiting. */
    public static final float FLAME_RANGE = 10f;

    /** Round numbers for a nest, so a test can count ticks to a brood rather than guess at one. */
    public static final float NEST_RADIUS = 2f;

    public static final int NEST_HEALTH = 100;

    /** Short, because a test that waits out a thirty-second period is a test nobody runs. */
    public static final float NEST_PERIOD = 1f;

    /** Well inside a period, so a test can tell the first brood from the ones that follow it. */
    public static final float NEST_FIRST_BROOD = 0.25f;

    /** Fixed rather than a range, so a test can assert a count instead of a band. */
    public static final int BROOD = 2;

    public static final float NEST_HATCH_RADIUS = 6f;

    /** How far off every man on our side a hatched creature is put down - a body and a half of room. */
    public static final float NEST_HATCH_KEEP_OFF = 4f;

    /** Short enough that a test can stand something just outside it and have it stay asleep. */
    public static final float NEST_WAKE_RADIUS = 20f;

    public static final float NEST_ROUSE_RADIUS = 30f;

    /** Wide enough to catch two units either side of the middle and leave a third of them clear. */
    public static final float BLAST_RADIUS = 6f;

    /** Enough to kill anything in the fixture, which is the rule: a blast is fatal or it is nothing. */
    public static final int BLAST_DAMAGE = 1000;

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
    public static final int BRUTE_HEALTH = 300;

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
    public static final int RABBIT_HEALTH = 1;

    public static final float RABBIT_THROES = 0.2f;

    /** Further than a shot travels in the time it takes to arrive, so the rabbit always gets to think. */
    public static final float DODGE_REACTION_RANGE = 25f;

    public static final float DODGE_SPEED = 60f;
    public static final float DODGE_DURATION = 0.3f;
    public static final float DODGE_COMMIT = 0.5f;

    /** Wide enough that a shot passing two units away still counts as coming at it. */
    public static final float DODGE_MARGIN = 1.5f;

    /**
     * The gait, as round numbers: half a second in the air and half a second sitting, which makes
     * the burst exactly twice the pace and one whole cycle exactly one second of walking.
     */
    public static final float HOP_SECONDS = 0.5f;

    public static final float REST_SECONDS = 0.5f;

    /** How many circles one burst of venom leaves, and how big each of them is. */
    public static final int SLICK_BLOBS = 3;

    public static final float SLICK_RADIUS = 2f;

    /** Well inside the radius, so every circle of a fixture spatter is on top of the burst. */
    public static final float SLICK_SPREAD = 1f;

    public static final float SLICK_SECONDS = 1f;

    /** Far above any health in the fixture: standing in one is a decision, not a graze. */
    public static final int SLICK_DAMAGE = 1000;

    /** How long a man one circle has put down is protected from the rest of the same spatter. */
    public static final float SLICK_INVULNERABLE = 0.5f;

    /** How hard a slick throws a man it wounds off itself, in world units per second. */
    public static final float SLICK_SHOVE_SPEED = 20f;

    /** How much of its speed a thicket leaves each side. He wades; they barely notice. */
    public static final float THICKET_PLAYER_FACTOR = 0.25f;

    public static final float THICKET_ALIEN_FACTOR = 0.75f;

    /**
     * The body a gun's {@code bloatPerShot} is quoted against.
     *
     * <p>Exactly the fixture enemy's own radius, so one shot against an ordinary enemy is worth
     * exactly what the gun says and a test can count shots. Anything bigger is proportionally more
     * work, which is the half a test about a brute is actually about.
     */
    public static final float BLOAT_REFERENCE_RADIUS = ENEMY_RADIUS;

    /**
     * How much of a shot a thing still on its feet banks.
     *
     * <p>One in the fixture, deliberately: almost every test here fires at something that is either
     * alive throughout or dead throughout, and a fraction in between would make every one of them
     * arithmetic. The one test that is about the fraction sets its own.
     */
    public static final float BLOAT_LIVING_FACTOR = 1f;

    /** Slow enough that a test can watch a charge go down, and fast enough not to take all day. */
    public static final float BLOAT_SETTLE_PER_SECOND = 0.5f;

    /**
     * How wide the circle a body leaves, as a multiple of its own radius. Six against the fixture's
     * radius of one, so a test can put a bystander inside one and another outside it.
     */
    public static final float BLOAT_BLAST_FACTOR = 6f;

    /** How long a charged body has left to lie there, refreshed while there is any charge on it. */
    public static final float BLOAT_HOLD = 0.5f;

    /**
     * How much of a body one shot of the fixture's beam pumps up.
     *
     * <p>A quarter, so four hits fill one and a test can count ticks to a burst instead of running
     * a gun for three seconds. The shipped number is far smaller, because the shipped number is
     * what balances the mechanic against the heat bar and this one only has to be reachable.
     */
    public static final float LASER_BLOAT_PER_SHOT = 0.25f;

    /** How much of his own speed the beam leaves the man holding it down, and for how long after. */
    public static final float LASER_SELF_SLOW_FACTOR = 0.6f;

    public static final float LASER_SELF_SLOW_SECONDS = 0.2f;

    /** How far back a shot of the beam is drawn. Never read by the simulation; here so the table builds. */
    public static final float LASER_TRAIL = 40f;

    /** How much of its speed the beam leaves a unit it lands on, and for how long after the last hit. */
    public static final float LASER_SLOW_FACTOR = 0.5f;

    public static final float LASER_SLOW_SECONDS = 0.5f;

    /**
     * How far a shot of venom flies before it bursts of its own accord.
     *
     * <p>The fixture used to give it none, which is why nothing ever noticed that a <em>miss</em>
     * left no spatter at all: with no reach the shot flew until it left the arena, and a burst
     * outside the map lays nothing. The shipped gun has had a reach since it was written - it is
     * what makes a frog a problem about where you are standing - so the fixture has one too, set
     * comfortably past the lane the tests fire down.
     */
    public static final float VENOM_REACH = 45f;

    /**
     * How many circles of fire one explosion leaves, and how big each is against the blast itself.
     *
     * <p><strong>Fire is off in the base config</strong>, exactly as leaping is: most tests here are
     * about something other than what an explosion leaves, and a fixture that burned the ground after
     * every barrel would make every blast test a test about fire as well. {@link #burning()} switches
     * it on.
     */
    public static final int FIRE_BLOBS = 3;

    public static final float FIRE_RADIUS_FACTOR = 0.25f;

    /** Well inside the radius, so every circle of a fixture fire is on top of the blast. */
    public static final float FIRE_SPREAD_FACTOR = 0.25f;

    /** The same number twice, so a test knows exactly which tick the ground goes out on. */
    public static final float FIRE_SECONDS = 1f;

    public static final int FIRE_DAMAGE = 1000;

    public static final float FIRE_INVULNERABLE = 0.5f;

    /** No ramps in the fixture: a circle that changes size is a test about the ramp, not about fire. */
    public static final float FIRE_GROW = 0f;

    public static final float FIRE_FADE = 0f;

    /** How hard fire throws a man it wounds off itself, in world units per second. */
    public static final float FIRE_SHOVE_SPEED = 20f;

    public static final float SPITTER_RADIUS = 1f;

    public static final float SPITTER_SPEED = 30f;

    public static final int SPITTER_HEALTH = 100;

    /** Shorter than the detection range, so a spitter notices well before it may shoot. */
    public static final float SPIT_RANGE = 15f;

    /** Half a second, so a test can count the shots a spitter gets off without waiting about. */
    public static final float SPIT_COOLDOWN = 0.5f;

    /** One world unit per tick, so a spit's travel can be read straight off. */
    public static final float SPIT_SPEED = 60f;

    /** Two of them put a man of any kind down, which is what the shipped numbers do too. */
    public static final int SPIT_DAMAGE = 50;

    /** Wide enough that a test can tell a spread shot from a straight one without a microscope. */
    public static final float SPIT_SPREAD_DEGREES = 40f;

    /** Small, so a test about a charged shot is not also a test about what its blast happened to catch. */
    public static final float CHARGED_SPIT_BLAST_RADIUS = 4f;

    /** How wide a shooter's field of view is in {@link #watchingItsArc()}: narrow, so a test can leave it. */
    public static final float SPIT_ARC_DEGREES = 90f;

    /** The distance a shooter prefers in {@link #holdingAPreferredDistance()}: half its reach. */
    public static final float SPIT_CLOSE_RANGE = SPIT_RANGE * 0.5f;

    /**
     * How fast it swings onto a target there, as a fraction of the angle left per second. Slow
     * enough that a test can watch it out of its arc for a moment and then watch it arrive.
     */
    public static final float SPIT_TURN_RATE = 1f;

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

    /** How many of each gun the armoury deck holds: one, so a full shuffle deals every gun once. */
    public static final int ARMOURY_CARDS = 1;

    /** The fewest and the most a crate is dealt (G0056): two or three, as shipped. */
    public static final int CRATE_FEWEST = 2;

    public static final int CRATE_MOST = 3;

    /** What Full Crates adds to the fewest: enough to fill every crate. */
    public static final int FULL_CRATES_MORE = 2;

    /** What one mouthful is worth here, so a test can count them. */
    public static final Grant MOUTHFUL = new Grant.Feed(1);

    /** Mouthfuls a grunt needs before it turns into a brute, in {@link #evolving()}. */
    public static final int FEED_TO_EVOLVE = 3;

    /** Slower and far harder-hitting than the sidearm, so a swap is visible in one shot. */
    public static final float RAILGUN_COOLDOWN = 1f;

    public static final int RAILGUN_DAMAGE = 400;

    /**
     * How long a slug waits between the blows it lands. Above zero is a shot that goes through
     * things; it is the fixture's only piercing gun, so a test about passing through has one to
     * fire and every other test still has shots that stop where they hit.
     *
     * <p>Chosen against the fixture rather than against the game: a shot 0.5 across crossing a body
     * 1 across at 60 a second is over it in 0.05 seconds, so one body owes one blow.
     */
    public static final float RAILGUN_PIERCE_INTERVAL = 0.05f;

    /** A third of the bar per shot, so a test can count to four and watch the gun stop itself. */
    public static final float TURRET_HEAT_PER_SHOT = 0.34f;

    /** Slow against the rate of fire, so holding the trigger wins, and fast enough to watch it fall. */
    public static final float HEAT_COOL_PER_SECOND = 0.4f;

    /** Half a bar. A test can tell "no longer full" from "back in your hands" at this distance. */
    public static final float HEAT_RESUME_BELOW = 0.5f;

    /** Far past a trooper's own sight, so a test can stand something where only the gun reaches it. */
    public static final float TURRET_RANGE = 60f;

    /** How far a trooper will divert for a free gun in the fixtures. */
    public static final float TURRET_CREW_RADIUS = 20f;

    /** A second, not the shipped six - a test that waits out a man's patience is a slow test. */
    public static final float TURRET_QUIET_SECONDS = 1f;

    public static final float TURRET_RADIUS = 2f;

    /** Three times a trooper's, as the shipped one is: a gun is worth working for. */
    public static final int TURRET_HEALTH = 300;

    /** Wide enough to catch whoever was on it, narrow enough not to reach the next thing along. */
    public static final float TURRET_BLAST_RADIUS = 5f;

    /** Slow enough that a test can watch one come round rather than find it already there. */
    public static final float TURRET_TURN_RATE = 2f;

    /** A quarter of that while the trigger is down, so the difference is visible in a second. */
    public static final float TURRET_FIRING_TURN_FACTOR = 0.25f;

    /** Long enough to tell from where it was fired, short enough to finish inside the arena. */
    public static final float BAZOOKA_RANGE = 20f;

    /** The grenade's circle: a rocket's blast is a grenade's, at range. */
    public static final float BAZOOKA_BLAST_RADIUS = 4f;

    /** One rocket fills the bar; this is two seconds to the next. */
    public static final float BAZOOKA_COOL_PER_SECOND = 0.5f;

    public static final float BAZOOKA_RECOIL_SPEED = 20f;

    public static final float BAZOOKA_RECOIL_SECONDS = 0.2f;

    /** How hard a rocket's pull shakes the ground under the man firing it, how far, and how long. */
    public static final float BAZOOKA_SHAKE_STRENGTH = 1f;

    public static final float BAZOOKA_SHAKE_RADIUS = 10f;

    public static final float BAZOOKA_SHAKE_SECONDS = 0.2f;

    /** A disc comes off this many things and stops at the next: enough to count, few enough to watch. */
    public static final int DISC_BOUNCES = 2;

    /** Longer than any of the disc tests fly, so only the one about its reach meets it. */
    public static final float DISC_RANGE = 60f;

    /** Four bolts stun an ordinary body: few enough to fire in a test, more than one so there is a build. */
    public static final int PULSE_STUN_AFTER_HITS = 4;

    /** Long enough to fire bolts into while it runs, short enough to wait out. */
    public static final float PULSE_STUN_SECONDS = 0.5f;

    /** An ordinary body is the one the pulse rifle's hits are quoted against. */
    public static final float SHOCK_REFERENCE_RADIUS = ENEMY_RADIUS;

    /** From full, two seconds to nothing. */
    public static final float SHOCK_DRAIN_PER_SECOND = 0.5f;

    /** How far clear of its holder's body every shot and throw leaves. */
    public static final float MUZZLE_GAP = 0.1f;

    /** Round, so which muzzle a shot came out of can be read straight off where it is. */
    public static final float TURRET_MUZZLE_OFFSET = 1f;

    /** Short, so a test can count two shots out of it without waiting about. */
    public static final float TURRET_COOLDOWN = 0.1f;

    /** Less than a sidearm's, which is the trade: a turret buys rate and speed with damage. */
    public static final int TURRET_DAMAGE = 25;

    public static final float TROOPER_RADIUS = 1f;

    public static final float TROOPER_SPEED = 20f;

    public static final int TROOPER_MAX_WOUNDS = 1;

    /** Small on purpose: a test that spawns a squad should be able to name every member of it. */
    public static final int SQUAD_SIZE = 3;

    /** Headroom for reinforcements. Small, so a test can fill the squad without spawning a crowd. */
    public static final int SQUAD_GROWTH = 3;

    /** Two tiles and a bit, so a man who forms up on the commander is plainly beside him. */
    public static final float TROOPER_REINFORCEMENT_RADIUS = 7f;

    /** Outside the point's own radius, as the shipped one is. */
    public static final float POINT_REINFORCEMENT_RADIUS = 12f;

    /** Fixed rather than a range, so a test counting crates is not also testing the dice. */
    public static final int POINT_REINFORCEMENTS = 2;

    /** What one crate is worth. Three, as the shipped one is. */
    public static final int CRATE_TROOPERS = 3;

    /** Round, so a test can step a whole pulse without counting fractions of a tick. */
    public static final float SHINE_SECONDS = 2f;

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

    /**
     * Nothing occasional happens in the base fixture.
     *
     * <p>Every {@link AiChance} is switched off, so no test is at the mercy of a roll it did not ask
     * for. A test about one of them switches that one on - {@link #willing(AiChance)} - which also
     * keeps the switched-off ones drawing nothing, so the stream a test does care about is not being
     * consumed behind its back.
     */
    public static Chances noChances() {
        return Chances.none();
    }

    /** The base config with one occasional behaviour certain to happen, for a test that is about it. */
    public static SimConfig willing(AiChance kind) {
        return config().withChance(kind, CERTAIN);
    }

    /**
     * Far more than one per second, so a behaviour rolled once per tick happens on the first tick it
     * is possible. A test about what a unit does when it takes a chance should not also be a test
     * about how often it takes one.
     */
    public static final float CERTAIN = 1000f;

    /** How far the commander's voice carries in the fixtures. */
    public static final float ORDER_RADIUS = 22f;

    public static final float FORWARD_DISTANCE = 40f;

    public static final float FORWARD_FACTOR = 1.6f;

    public static final float FOLLOW_DISTANCE = 7f;

    /**
     * How wide of a point or of the commander a trooper walks, when a test switches the spread on. Off
     * in the fixture, as it is for every strain, so no other test's geometry moves under it.
     */
    public static final ApproachDef TROOPER_APPROACH = new ApproachDef(true, 8f, 10f);

    /** How far ahead of the commander a follower aims, when a test switches it on. */
    public static final float FOLLOW_AHEAD_SECONDS = 1f;

    /** How long a man holds a taken point before following a commander who has gone on ahead. */
    public static final float HOLD_SECONDS = 2f;

    /** How much one man's hold differs from that, either way. */
    public static final float HOLD_JITTER_SECONDS = 0.5f;

    /** How near a point a commander counts as being at it. Wider than a point, narrower than the arena. */
    public static final float RALLY_RADIUS = 15f;

    /** How long a walking trooper may make almost no ground before he counts as stuck. */
    public static final float BLOCK_SECONDS = 1f;

    /** How much ground in that time still counts as getting somewhere. */
    public static final float BLOCK_DISTANCE = 1f;

    /** How long a stuck trooper backs off to one side before trying his way again. */
    public static final float DETOUR_SECONDS = 0.5f;

    /** How long a trooper who has been hit looks all round for what hit him. */
    public static final float ALERT_SECONDS = 2f;

    /** How much nearer something coming for somebody counts as being. More than the head start a nest has in the tests. */
    public static final float THREAT_BONUS = 15f;

    /** How much further something counts as being for every man already shooting at it. */
    public static final float SHARED_TARGET_PENALTY = 8f;

    /** How far a wounded trooper walks for a medkit. */
    public static final float MEDKIT_REACH = 20f;

    /** How far inside his gun's reach a man with a short gun walks before he plants himself. */
    public static final float CLOSE_IN_FACTOR = 0.8f;

    /** A trooper's wait between grenades: long, so a test sees one man throw once. */
    public static final float TROOPER_GRENADE_COOLDOWN = 10f;

    /** Aliens that must stand within the blast where a trooper's grenade lands. */
    public static final int MIN_CLUSTER = 3;

    /** How far past the blast the nearest man of ours must stand for a trooper to throw. */
    public static final float SAFETY_MARGIN = 4f;

    /** At most one trooper grenade per this many seconds, across the squad. */
    public static final float SQUAD_GAP_SECONDS = 6f;

    /** How far from the post the squad roams in the fixtures. Round, and well inside the test arena. */
    public static final float TROOPER_PATCH = 20f;

    public static final int SPAWN_COL = 1;

    public static final int SPAWN_WIDTH = 2;

    public static final int SPAWN_HEIGHT = 5;

    /**
     * Men off the field when a level starts. Plenty, so a test about something else never runs a
     * commander out of lives; a test about the reserve names its own.
     */
    public static final int RESERVE_START = 50;

    public static final int RESERVE_MAX = 60;

    /** What the two guns that run hot in the fixtures declare. Both, so one test covers either. */
    private static final HeatDef HOT = new HeatDef(TURRET_HEAT_PER_SHOT, HEAT_COOL_PER_SECOND, HEAT_RESUME_BELOW);

    /**
     * A table filled over the shipped one, so a kind the game gains needs nothing here until a test is
     * about it: whatever the fixture puts replaces the shipped definition, and a kind it leaves out
     * keeps the shipped numbers, passed through {@code quiet} wherever the game asks every kind at once.
     *
     * <p>Not for upgrades: a shipped one would be dealt in every draft and checked against this deck,
     * so {@link #upgrades()} states every kind.
     */
    private static <K extends Enum<K>, V> Defs.Builder<K, V> onShipped(
            Class<K> type, Defs<K, V> shipped, UnaryOperator<V> quiet) {
        Defs.Builder<K, V> builder = Defs.builder(type);
        for (K kind : type.getEnumConstants()) {
            builder.put(kind, quiet.apply(shipped.of(kind)));
        }
        return builder;
    }

    /**
     * What every kind of ground is worth to walk on in the fixture.
     *
     * <p>A thicket is the one that is worth anything, and it is a harsher number here than the game
     * ships: a test asserting that something arrives later has to be able to tell, and halving a
     * speed over twenty world units is a difference a test can measure without running for a minute.
     */
    private static Defs<Terrain, GroundDef> thickets() {
        Defs.Builder<Terrain, GroundDef> builder = Defs.builder(Terrain.class);
        for (Terrain terrain : Terrain.values()) {
            builder.put(
                    terrain,
                    terrain == Terrain.THICKET
                            ? new GroundDef(THICKET_PLAYER_FACTOR, THICKET_ALIEN_FACTOR)
                            : GroundDef.clear());
        }
        return builder.build();
    }

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
                        COMMANDER_MAX_WOUNDS,
                        COMMANDER_BLEED_OUT,
                        DEATH_THROES,
                        RESPAWN_DELAY,
                        RESPAWN_SHIELD,
                        LEASH_RADIUS,
                        WeaponKind.SIDEARM),
                new SimConfig.TrooperConfig(
                        TROOPER_RADIUS,
                        TROOPER_SPEED,
                        SQUAD_SIZE,
                        SQUAD_GROWTH,
                        TROOPER_REINFORCEMENT_RADIUS,
                        TROOPER_MAX_WOUNDS,
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
                        FORWARD_DISTANCE,
                        FORWARD_FACTOR,
                        FOLLOW_DISTANCE,
                        TROOPER_PATCH,
                        ApproachDef.off(),
                        0f,
                        HOLD_SECONDS,
                        HOLD_JITTER_SECONDS,
                        RALLY_RADIUS,
                        BLOCK_SECONDS,
                        BLOCK_DISTANCE,
                        DETOUR_SECONDS,
                        ALERT_SECONDS,
                        THREAT_BONUS,
                        SHARED_TARGET_PENALTY,
                        MEDKIT_REACH,
                        CLOSE_IN_FACTOR,
                        new TrooperGrenadeDef(TROOPER_GRENADE_COOLDOWN, MIN_CLUSTER, SAFETY_MARGIN, SQUAD_GAP_SECONDS)),
                new SimConfig.SpawnConfig(SPAWN_COL, SPAWN_WIDTH, SPAWN_HEIGHT),
                // Never musters: men turning up every so often would move every count in every test.
                new SimConfig.ReserveConfig(RESERVE_START, RESERVE_MAX, 0f, 2, 3),
                onShipped(UnitKind.class, SimConfig.defaults().units(), UnaryOperator.identity())
                        .put(UnitKind.GRUNT, strain(ENEMY_RADIUS, ENEMY_SPEED, ENEMY_HEALTH, Look.GRUNT))
                        .put(UnitKind.BRUTE, strain(BRUTE_RADIUS, BRUTE_SPEED, BRUTE_HEALTH, Look.BRUTE))
                        .put(UnitKind.SPITTER, strain(SPITTER_RADIUS, SPITTER_SPEED, SPITTER_HEALTH, Look.SPITTER))
                        .put(UnitKind.RUNNER, strain(ENEMY_RADIUS, ENEMY_SPEED, ENEMY_HEALTH, Look.RUNNER))
                        .put(UnitKind.FROG, strain(ENEMY_RADIUS, ENEMY_SPEED, ENEMY_HEALTH, Look.FROG))
                        .put(UnitKind.ANT, strain(ENEMY_RADIUS, ENEMY_SPEED, ENEMY_HEALTH, Look.ANT))
                        // Rooted, big and deep, with no barrage: the fixtures about one switch it on. Shock never
                        // builds on
                        // it, as in the game (G0059).
                        .put(
                                UnitKind.BOSS_GRUNT,
                                strain(BOSS_RADIUS, 0f, BOSS_HEALTH, Look.BOSS_GRUNT)
                                        .withShockable(false))
                        .build(),
                onShipped(
                                PickupKind.class,
                                SimConfig.defaults().pickups(),
                                pickup -> pickup.withArmoury(ArmouryDef.off()))
                        .put(
                                PickupKind.MUSHROOM,
                                new PickupDef(PICKUP_RADIUS, Look.MUSHROOM, Grant.NOTHING, MOUTHFUL, 0f))
                        .put(
                                PickupKind.FLOWER,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.FLOWER,
                                        Grant.NOTHING,
                                        new Grant.Mutate(UnitKind.BRUTE),
                                        0f))
                        .put(
                                PickupKind.SUPPLY_DROP,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.SUPPLY_DROP,
                                        new Grant.Reinforce(CRATE_TROOPERS),
                                        Grant.NOTHING,
                                        SHINE_SECONDS))
                        .put(
                                PickupKind.ARMOURY_RAILGUN,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_RAILGUN,
                                        new Grant.Arm(WeaponKind.RAILGUN),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        new ArmouryDef(ARMOURY_CARDS, false)))
                        .put(
                                PickupKind.ARMOURY_FLAMER,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_FLAMER,
                                        new Grant.Arm(WeaponKind.FLAMER),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        new ArmouryDef(ARMOURY_CARDS, false)))
                        .put(
                                PickupKind.ARMOURY_SHOTGUN,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_SHOTGUN,
                                        new Grant.Arm(WeaponKind.SHOTGUN),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        new ArmouryDef(ARMOURY_CARDS, false)))
                        .put(
                                PickupKind.ARMOURY_LASER,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_LASER,
                                        new Grant.Arm(WeaponKind.LASER),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        new ArmouryDef(ARMOURY_CARDS, false)))
                        .put(
                                PickupKind.ARMOURY_BAZOOKA,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_BAZOOKA,
                                        new Grant.Arm(WeaponKind.BAZOOKA),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        // Out of the fixture's deck, so the deck the crate tests count is the one they
                                        // were written for.
                                        ArmouryDef.off()))
                        .put(
                                PickupKind.ARMOURY_PULSE,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_PULSE,
                                        new Grant.Arm(WeaponKind.PULSE),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        // Out of the deck, as the bazooka is.
                                        ArmouryDef.off()))
                        .put(
                                PickupKind.ARMOURY_DISC,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.ARMOURY_DISC,
                                        new Grant.Arm(WeaponKind.DISC_GUN),
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        // Out of the deck, as the bazooka is.
                                        ArmouryDef.off()))
                        .put(
                                PickupKind.CRATE,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.CRATE,
                                        Grant.NOTHING,
                                        Grant.NOTHING,
                                        SHINE_SECONDS,
                                        new ArmouryDef(0, true)))
                        .put(PickupKind.RABBIT, new PickupDef(RABBIT_RADIUS, Look.RABBIT, Grant.NOTHING, MOUTHFUL, 0f))
                        .put(
                                PickupKind.BARREL,
                                new PickupDef(PICKUP_RADIUS, Look.BARREL, Grant.NOTHING, Grant.DETONATE, 0f))
                        .put(
                                PickupKind.MEDKIT,
                                new PickupDef(
                                        PICKUP_RADIUS,
                                        Look.MEDKIT,
                                        new Grant.Mend(),
                                        Grant.NOTHING,
                                        0f,
                                        new ArmouryDef(ARMOURY_CARDS, false)))
                        .build(),
                onShipped(WeaponKind.class, SimConfig.defaults().weapons(), UnaryOperator.identity())
                        .put(
                                WeaponKind.SIDEARM,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        WEAPON_DAMAGE,
                                        1,
                                        0f,
                                        Look.PROJECTILE,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.BALLISTIC))
                        .put(
                                WeaponKind.SPIT,
                                new WeaponDef(
                                        SPIT_COOLDOWN,
                                        SPIT_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        SPIT_DAMAGE,
                                        1,
                                        0f,
                                        Look.SPIT,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.NONE))
                        .put(
                                WeaponKind.SPEW,
                                new WeaponDef(
                                        SPIT_COOLDOWN,
                                        SPIT_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        SPIT_DAMAGE,
                                        1,
                                        0f,
                                        Look.SPEW,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.NONE))
                        .put(
                                WeaponKind.CHARGED_SPIT,
                                new WeaponDef(
                                        SPIT_COOLDOWN,
                                        SPIT_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        SPIT_DAMAGE,
                                        1,
                                        0f,
                                        Look.CHARGED_SPIT,
                                        CHARGED_SPIT_BLAST_RADIUS,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.NONE))
                        .put(
                                WeaponKind.RAILGUN,
                                new WeaponDef(
                                        RAILGUN_COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        RAILGUN_DAMAGE,
                                        1,
                                        0f,
                                        Look.SLUG,
                                        0f,
                                        SlickDef.off(),
                                        HOT,
                                        0f,
                                        RAILGUN_PIERCE_INTERVAL,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.ENERGY))
                        .put(
                                WeaponKind.FLAMER,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        FLAME_RANGE,
                                        WEAPON_DAMAGE,
                                        PELLETS,
                                        SCATTER_DEGREES,
                                        Look.FLAME,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.HEAVY))
                        // The beam: it stops at the first thing it touches, and it runs hot. The
                        // fixture keeps both, because both are what every test about it is about.
                        .put(
                                WeaponKind.LASER,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        WEAPON_DAMAGE,
                                        1,
                                        0f,
                                        Look.LASER,
                                        0f,
                                        SlickDef.off(),
                                        HOT,
                                        0f,
                                        0f,
                                        LASER_SLOW_FACTOR,
                                        LASER_SLOW_SECONDS,
                                        LASER_SELF_SLOW_FACTOR,
                                        LASER_SELF_SLOW_SECONDS,
                                        LASER_TRAIL,
                                        LASER_BLOAT_PER_SHOT,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.ENERGY))
                        .put(
                                WeaponKind.BAZOOKA,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        BAZOOKA_RANGE,
                                        WEAPON_DAMAGE,
                                        1,
                                        0f,
                                        Look.ROCKET,
                                        BAZOOKA_BLAST_RADIUS,
                                        SlickDef.off(),
                                        new HeatDef(1f, BAZOOKA_COOL_PER_SECOND, 0f),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.WOUND,
                                        BAZOOKA_RECOIL_SPEED,
                                        BAZOOKA_RECOIL_SECONDS,
                                        0,
                                        new ShakeDef(
                                                BAZOOKA_SHAKE_STRENGTH, BAZOOKA_SHAKE_RADIUS, BAZOOKA_SHAKE_SECONDS),
                                        0,
                                        WeaponType.HEAVY))
                        // The pulse rifle: an ordinary shot whose stun is built by several of them.
                        .put(
                                WeaponKind.PULSE,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        WEAPON_DAMAGE,
                                        1,
                                        0f,
                                        Look.BOLT,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        PULSE_STUN_SECONDS,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        PULSE_STUN_AFTER_HITS,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.ENERGY))
                        // The disc gun: an ordinary shot that comes off what it hits, with a reach, cold.
                        .put(
                                WeaponKind.DISC_GUN,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        DISC_RANGE,
                                        WEAPON_DAMAGE,
                                        1,
                                        0f,
                                        Look.DISC,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        DISC_BOUNCES,
                                        WeaponType.ENERGY))
                        .put(
                                WeaponKind.SHOTGUN,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        WEAPON_DAMAGE,
                                        PELLETS,
                                        0f,
                                        Look.PELLET,
                                        0f,
                                        SlickDef.off(),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.BALLISTIC))
                        .put(
                                WeaponKind.VENOM,
                                new WeaponDef(
                                        COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        VENOM_REACH,
                                        0,
                                        1,
                                        0f,
                                        Look.VENOM,
                                        0f,
                                        new SlickDef(
                                                SLICK_BLOBS,
                                                SLICK_RADIUS,
                                                SLICK_SPREAD,
                                                SLICK_SECONDS,
                                                SLICK_SECONDS,
                                                SLICK_DAMAGE,
                                                SLICK_INVULNERABLE,
                                                0f,
                                                0f,
                                                Look.SLICK,
                                                SLICK_SHOVE_SPEED),
                                        HeatDef.none(),
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.NONE,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.NONE))
                        .put(
                                WeaponKind.TURRET_GUN,
                                new WeaponDef(
                                        TURRET_COOLDOWN,
                                        PROJECTILE_SPEED,
                                        PROJECTILE_RADIUS,
                                        0f,
                                        TURRET_DAMAGE,
                                        1,
                                        0f,
                                        Look.TURRET_SHOT,
                                        0f,
                                        SlickDef.off(),
                                        HOT,
                                        TURRET_MUZZLE_OFFSET,
                                        0f,
                                        1f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        Harm.WOUND,
                                        Harm.DOWN,
                                        0f,
                                        0f,
                                        0,
                                        ShakeDef.off(),
                                        0,
                                        WeaponType.NONE))
                        .build(),
                MUZZLE_GAP,
                thickets(),
                noChances(),
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
                new SimConfig.BlastConfig(BLAST_DAMAGE, BLAST_SECONDS, BLAST_SHAKE, BLAST_SHAKE_REACH, Harm.DOWN),
                new SimConfig.FireConfig(0, 0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f),
                new SimConfig.BloatConfig(
                        BLOAT_REFERENCE_RADIUS,
                        BLOAT_LIVING_FACTOR,
                        BLOAT_SETTLE_PER_SECOND,
                        BLOAT_BLAST_FACTOR,
                        BLOAT_HOLD),
                new SimConfig.ShockConfig(SHOCK_REFERENCE_RADIUS, SHOCK_DRAIN_PER_SECOND),
                new SimConfig.GrenadeConfig(
                        GRENADE_COOLDOWN,
                        GRENADE_SPEED,
                        GRENADE_RANGE,
                        GRENADE_RADIUS,
                        GRENADE_BLAST_RADIUS,
                        Harm.WOUND),
                new SimConfig.RabbitConfig(RABBIT_SPEED, RABBIT_HEALTH, RABBIT_THROES, WanderDef.off(), DodgeDef.off()),
                new SimConfig.BarrelConfig(BARREL_HEALTH, BARREL_BLAST_RADIUS, BARREL_BITTEN_BLAST_RADIUS),
                new SimConfig.MineConfig(
                        MINE_RADIUS,
                        MINE_TRIGGER_RADIUS,
                        MINE_FUSE,
                        MINE_HEALTH,
                        MINE_BLAST_RADIUS,
                        MINE_FLASH_FROM,
                        MINE_FLASH_TO),
                new SimConfig.CasualtyConfig(
                        CASUALTY_BLEED_OUT,
                        CASUALTY_STABILISE,
                        REVIVE_INVULNERABLE,
                        DRAGGED_BLEED_FACTOR,
                        HAULED_STABILISE_FACTOR),
                new SimConfig.WoundConfig(WOUND_GRACE, WOUND_SHOVE, WOUND_PACE, WOUND_COOLDOWN, 0),
                new SimConfig.ActionConfig(ACTION_REACH, DRAG_SPEED_FACTOR, DRAG_LATERAL_FACTOR, DRAG_GAP),
                new SimConfig.NestConfig(
                        NEST_RADIUS,
                        NEST_HEALTH,
                        NEST_PERIOD,
                        NEST_FIRST_BROOD,
                        BROOD,
                        BROOD,
                        BROOD,
                        BROOD,
                        NEST_HATCH_RADIUS,
                        NEST_HATCH_KEEP_OFF,
                        NEST_WAKE_RADIUS,
                        NEST_ROUSE_RADIUS),
                new SimConfig.ControlPointConfig(
                        POINT_RADIUS,
                        POINT_CAPTURE_SECONDS,
                        POINT_AGGRO_RADIUS,
                        POINT_REINFORCEMENT_RADIUS,
                        POINT_REINFORCEMENTS,
                        POINT_REINFORCEMENTS),
                new SimConfig.TurretConfig(
                        TURRET_RADIUS,
                        TURRET_HEALTH,
                        TURRET_BLAST_RADIUS,
                        TURRET_TURN_RATE,
                        TURRET_FIRING_TURN_FACTOR,
                        TURRET_RANGE,
                        TURRET_CREW_RADIUS,
                        TURRET_QUIET_SECONDS,
                        WeaponKind.TURRET_GUN),
                new SimConfig.ObstacleConfig(OBSTACLE_RADIUS, 0),
                new SimConfig.CollisionConfig(3),
                new DraftDef(DRAFT_OPTIONS, 1, 1, 1, List.of(0, TIER_TWO_FROM)),
                stats(),
                upgrades(),
                new AdaptationDef(0f, 0, 0),
                new SimConfig.SwarmConfig(100),
                new SimConfig.MedkitConfig(0),
                new SimConfig.CrateConfig(CRATE_FEWEST, CRATE_MOST));
    }

    /** Cards a draft deals here - more than the catalogue has, so every draft offers all it can. */
    public static final int DRAFT_OPTIONS = 3;

    /** Drafts earned before a tier-two upgrade is dealt. */
    public static final int TIER_TWO_FROM = 2;

    /** What one rank of Larger Army adds to a crate, and to the squad's headroom. */
    public static final int ARMY_MEN = 1;

    public static final int ARMY_HEADROOM = 2;

    /** What one rank of Weapon Handling takes off a cooldown, in percent - large, so a test can see it. */
    public static final int HANDLING_PERCENT = -25;

    /** Every stat: a scaled one may come down by half or double; a count has no floor above zero. */
    public static Defs<Stat, StatDef> stats() {
        Defs.Builder<Stat, StatDef> stats = Defs.builder(Stat.class);
        for (Stat stat : Stat.values()) {
            stats.put(stat, stat.form() == Stat.Form.SCALE ? new StatDef(-50, 100, 0) : new StatDef(0, 0, 0));
        }
        return stats.build();
    }

    /** The shipped two, with numbers a test can see move, and the swarm's one adaptation. */
    public static Defs<UpgradeKind, UpgradeDef> upgrades() {
        return Defs.<UpgradeKind, UpgradeDef>builder(UpgradeKind.class)
                .put(
                        UpgradeKind.LARGER_ARMY,
                        new UpgradeDef(
                                Side.PLAYER,
                                10,
                                3,
                                1,
                                null,
                                0,
                                List.of(
                                        new Effect.Count(Stat.MEN_PER_CRATE, Target.SIDE, ARMY_MEN),
                                        new Effect.Count(Stat.SQUAD_HEADROOM, Target.SIDE, ARMY_HEADROOM))))
                .put(
                        UpgradeKind.WEAPON_HANDLING,
                        new UpgradeDef(
                                Side.PLAYER,
                                10,
                                3,
                                1,
                                null,
                                0,
                                List.of(new Effect.Scale(Stat.FIRE_COOLDOWN, Target.TEAM, HANDLING_PERCENT))))
                // The rest are switched off (weight zero), so a draft here offers the two above and a test
                // about dealing can say which; a test about one of these holds it directly.
                .put(UpgradeKind.MARKSMEN, off(2, new Effect.Scale(Stat.GUN_DAMAGE, Target.SQUAD, MARKSMEN_PERCENT)))
                .put(
                        UpgradeKind.FIELD_MEDICINE,
                        off(
                                2,
                                new Effect.Chance(AiChance.AID_A_TROOPER, Target.SQUAD, MEDICINE_PER_SECOND),
                                new Effect.Chance(AiChance.AID_THE_COMMANDER, Target.SQUAD, MEDICINE_PER_SECOND)))
                .put(
                        UpgradeKind.GRENADIER,
                        off(3, new Effect.Scale(Stat.GRENADE_COOLDOWN, Target.CHOOSER, GRENADIER_PERCENT)))
                .put(UpgradeKind.FORCED_MARCH, off(2, new Effect.Scale(Stat.SPEED, Target.SQUAD, MARCH_PERCENT)))
                .put(
                        UpgradeKind.FIRST_AID,
                        off(2, new Effect.Scale(Stat.STABILISE_SPEED, Target.TEAM, FIRST_AID_PERCENT)))
                .put(UpgradeKind.THICK_SKIN, off(2, new Effect.Count(Stat.MAX_WOUNDS, Target.TEAM, 1)))
                .put(
                        UpgradeKind.SELF_TREATMENT,
                        off(1, new Effect.Count(Stat.MEND_SECONDS, Target.CHOOSER, SELF_TREATMENT_SECONDS)))
                .put(
                        UpgradeKind.FIREPOWER,
                        off(
                                2,
                                new Effect.Stock(PickupKind.ARMOURY_FLAMER, FIREPOWER_STOCK),
                                new Effect.Gun(WeaponKind.FLAMER, Stat.GUN_DAMAGE, Target.TEAM, FIREPOWER_PERCENT)))
                .put(
                        UpgradeKind.EXPLOSIVES,
                        off(1, new Effect.Chance(AiChance.THROW_A_GRENADE, Target.SQUAD, EXPLOSIVES_PER_SECOND)))
                .put(UpgradeKind.FULL_CRATES, off(1, new Effect.Count(Stat.CRATE_LEAST, Target.SIDE, FULL_CRATES_MORE)))
                // The swarm's one: dealt whenever an adaptation is due, since nothing else on its side is.
                .put(
                        UpgradeKind.RAPID_SPIT,
                        new UpgradeDef(
                                Side.ALIEN,
                                10,
                                3,
                                1,
                                null,
                                0,
                                List.of(new Effect.Breed(UnitKind.SPITTER, Stat.FIRE_COOLDOWN, SPIT_PERCENT))))
                .build();
    }

    public static final int MARKSMEN_PERCENT = 15;
    public static final float MEDICINE_PER_SECOND = 0.2f;
    public static final int GRENADIER_PERCENT = -20;
    public static final int MARCH_PERCENT = 10;
    public static final int FIRST_AID_PERCENT = 25;
    public static final int SELF_TREATMENT_SECONDS = 1;
    public static final int FIREPOWER_STOCK = 1;
    public static final int FIREPOWER_PERCENT = 25;

    /** What one rank of Rapid Spit takes off a spitter's cooldown, in percent. */
    public static final int SPIT_PERCENT = -25;

    /** Explosives' chance a second: high, so a test sees a man throw at his first opportunity. */
    public static final float EXPLOSIVES_PER_SECOND = 1000f;

    /** An upgrade no draft here deals, for a test to hold directly. */
    private static UpgradeDef off(int ranks, Effect... effects) {
        return new UpgradeDef(Side.PLAYER, 0, ranks, 1, null, 0, List.of(effects));
    }

    /**
     * A strain that differs from the other only in the three numbers a test ever cares about. Nothing
     * evolves and nothing leaps here; both are switched on by the fixtures that are about them.
     */
    private static UnitDef strain(float radius, float speed, int health, Look look) {
        return new UnitDef(
                radius,
                speed,
                0,
                health,
                ENEMY_CONTACT_DAMAGE,
                ENEMY_ATTACK_ARC,
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
                HopDef.off(),
                RangedDef.off(),
                ApproachDef.off(),
                WeaveDef.off(),
                DodgeDef.off(),
                GripDef.off(),
                ArmourDef.off(),
                BarrageDef.off(),
                true,
                noChances(),
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

    /** Player one giving an order on this tick and holding nothing. */
    public static PlayerInputs ordering(Order order) {
        return new PlayerInputs().order(0, order);
    }

    /**
     * The harness's invulnerability, on from the next tick until switched off. Sets the level's flag
     * directly rather than stepping, so a test's tick count is not moved by switching it.
     */
    public static void godMode(Simulation simulation) {
        simulation.level().commandersInvulnerable = true;
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
     * A world where the supply drop is worth having to <em>both</em> sides.
     *
     * <p>Nothing in the shipped table is contested any more - a drop and a weapons cache are the
     * player's alone, and everything that grows is the aliens' - so this is what a test about the
     * grant-per-side mechanism has to build for itself. The mechanism is still the design; it is the
     * table that stopped using both halves.
     */
    public static Simulation contesting() {
        return new Simulation(config().withPickups(
                        config().pickups().map(PickupKind.SUPPLY_DROP, drop -> drop.withAlienGrant(MOUTHFUL))));
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
                strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, SPIT_SPREAD_DEGREES, true, WeaponKind.SPIT))));
    }

    /** Shooting straight, for a test that wants to know exactly where a shot went. */
    public static Simulation shootingStraight() {
        return shootingStraight(Maps.uniform(ARENA_TILES, ARENA_TILES, TILE, Terrain.PLAIN));
    }

    /** Shooting straight on ground of your own, for a test about what a shooter will not shoot through. */
    public static Simulation shootingStraight(TileMap map) {
        return new Simulation(
                everyStrain(
                        config(), strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.SPIT))),
                map);
    }

    /**
     * A world where every strain gets about in hops. Walking is the base config for the reason not
     * leaping is: a test about something else wants a creature that comes at you predictably.
     */
    public static Simulation hopping() {
        return new Simulation(
                everyStrain(config(), strain -> strain.withHop(new HopDef(true, HOP_SECONDS, REST_SECONDS))));
    }

    /**
     * The base config with fire switched on: every explosion leaves burning ground behind it.
     *
     * <p>Off everywhere else, because a fixture that burned the ground after every barrel would make
     * every test about an explosion a test about fire as well.
     */
    public static SimConfig burning() {
        return burning(config());
    }

    /** The same, applied to a config a test has already adjusted. */
    public static SimConfig burning(SimConfig config) {
        return config.withFire(new SimConfig.FireConfig(
                FIRE_BLOBS,
                FIRE_RADIUS_FACTOR,
                FIRE_SPREAD_FACTOR,
                FIRE_SECONDS,
                FIRE_SECONDS,
                FIRE_DAMAGE,
                FIRE_INVULNERABLE,
                FIRE_GROW,
                FIRE_FADE,
                FIRE_SHOVE_SPEED));
    }

    /** A world where every strain throws venom, so a test about ruined ground has somebody to ruin it. */
    public static Simulation spattering() {
        return new Simulation(everyStrain(
                config(), strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.VENOM))));
    }

    /**
     * A world where every strain shoots straight, holds its ground, and only fires at what is inside
     * a narrow field of view. The base shooting fixture has eyes all round on purpose, so a test
     * about where a shot goes is not also a test about whether the thing had turned far enough.
     */
    public static Simulation watchingItsArc() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.SPIT)
                        .withArcDegrees(SPIT_ARC_DEGREES)
                        .withTurnRate(SPIT_TURN_RATE)
                        .withHoldsGround(true))));
    }

    /**
     * A world where every shooter has a distance it prefers, and so walks to that rather than to the
     * edge of its reach. The other shooting fixtures name none, which leaves them stopping as soon
     * as they can reach at all - simpler to assert against, and not what the shipped spitter does.
     */
    public static Simulation holdingAPreferredDistance() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.SPIT)
                        .withHoldsGround(true)
                        .withCloseRange(SPIT_CLOSE_RANGE))));
    }

    /** A world where every strain settles its aim over three shots, for a test about the settling. */
    public static Simulation settlingAim() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, SPIT_SPREAD_DEGREES, true, WeaponKind.SPIT)
                        .withSettleShots(3)
                        .withSettledSpreadFactor(0.1f))));
    }

    /** A world where every strain carries a charged gun once it has eaten something explosive. */
    public static Simulation chargedByBarrels() {
        return new Simulation(everyStrain(
                        config(),
                        strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.SPIT)
                                .withChargedGun(WeaponKind.CHARGED_SPIT)))
                .withChance(AiChance.BITE_A_BARREL, CERTAIN));
    }

    /**
     * A world where every shooter holds its ground and ambles about its patch while it fires.
     *
     * <p>Wandering is off in the base fixture, so a shooter there stands perfectly still - which is
     * the bug this exists to catch rather than the behaviour.
     */
    public static Simulation shufflingShooters() {
        return new Simulation(everyStrain(
                config(),
                strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.SPIT)
                                .withHoldsGround(true))
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
     * A world where every strain has to notice the commander first, ambles until it does, and goes
     * for an explosive it can see and use.
     *
     * <p>Three things have to be true at once for that to happen at all, which is why it is a fixture
     * rather than a line in a test: the unit must not be hunting anybody - the commander outranks
     * food absolutely - it must have a secondary range to notice food within, and its strain must
     * name a gun a barrel can charge, or a barrel is something to be wary of rather than to fetch.
     *
     * <p>Ambling is deliberately left off. Going for food is navigation rather than wandering, and a
     * unit strolling about its patch blunders into things by accident - which is exactly what a test
     * about whether it went on purpose must not allow.
     */
    public static Simulation huntingBarrels() {
        return new Simulation(everyStrain(
                        config(),
                        strain -> strain.withRanged(RangedDef.plain(SPIT_RANGE, 0f, true, WeaponKind.SPIT)
                                        .withHoldsGround(true)
                                        .withChargedGun(WeaponKind.CHARGED_SPIT))
                                .withPerception(PerceptionDef.allRound(
                                        false, true, DETECTION_RANGE, LOSE_RANGE, SECONDARY_RANGE)))
                .withChance(AiChance.BITE_A_BARREL, CERTAIN));
    }

    /** How wide a spreading strain closes in the fixtures. Wide enough to read off a single tick. */
    public static final float APPROACH_OFFSET = 8f;

    /** Round, and well inside the lane a test walks something down, so the fan has room to open. */
    public static final float APPROACH_CLOSE_RANGE = 10f;

    /**
     * A world where the grunt takes its own line in and nothing else does.
     *
     * <p>Only the one strain, for the reason {@link #weaving} names: a test about a spread wants
     * something walking straight at him beside it to compare against, and the fixture is the
     * comparison.
     */
    public static Simulation spreading() {
        return new Simulation(config().withUnit(
                        UnitKind.GRUNT,
                        grunt -> grunt.withApproach(new ApproachDef(true, APPROACH_OFFSET, APPROACH_CLOSE_RANGE))));
    }

    /** How hard a weaving strain leans off its course in the fixtures. Wide enough to see. */
    public static final float WEAVE_AMPLITUDE = 0.6f;

    /** Round, and short enough that a test sees two whole cycles in forty ticks. */
    public static final float WEAVE_WAVELENGTH = 8f;

    /**
     * A world where the runner weaves and nothing else does.
     *
     * <p>Only the one strain, deliberately: every test about the weave wants something walking in a
     * straight line beside it to compare against, and the fixture is the comparison.
     */
    public static Simulation weaving() {
        return new Simulation(config().withUnit(
                        UnitKind.RUNNER,
                        runner -> runner.withWeave(new WeaveDef(true, WEAVE_AMPLITUDE, WEAVE_WAVELENGTH))));
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
                everyStrain(config(), strain -> strain.withContactDamage(0).withLeap(leapConfig())));
    }

    /** Whether a man has come to no harm at all: no wound, not down, not in his throes (G0053). */
    public static boolean unhurt(Entity man) {
        Vitality vitality = man.getComponent(Vitality.class);
        return vitality.wounds == 0 && !vitality.begun && !killed(man);
    }

    /** Whether anything has been killed: in its throes, or marked dead already. */
    public static boolean killed(Entity entity) {
        Dying throes = entity.getComponent(Dying.class);
        return (throes != null && throes.begun) || entity.getComponent(Dead.class) != null;
    }

    /**
     * A world where a creature's claws only reach what is in front of it. All round in the base
     * config, so a test about what a blow costs is never also a test about which way the thing was
     * walking when it landed.
     */
    public static Simulation clawingForwardOnly() {
        return new Simulation(everyStrain(config(), strain -> strain.withAttackArcDegrees(ENEMY_NARROW_ATTACK_ARC)));
    }

    /** Enemies that walk but cannot hurt anything, for tests about a swing rather than about a fight. */
    public static Simulation harmless() {
        return new Simulation(everyStrain(config(), strain -> strain.withContactDamage(0)));
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
        // And a slow, because the seam this body exists to test is exactly the one a slow is spent
        // at: everything in the game that is driven carries one.
        entity.add(engine.createComponent(Slowed.class));
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
