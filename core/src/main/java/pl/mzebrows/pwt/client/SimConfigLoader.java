package pl.mzebrows.pwt.client;

import java.util.Locale;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Builds a {@link SimConfig} from {@code game.properties}.
 *
 * <p>This lives in the client module because the simulation does no file I/O - it is handed its
 * configuration already parsed.
 */
public final class SimConfigLoader {

    private static final String FILE = "game.properties";

    private SimConfigLoader() {}

    /** Loads simulation config, falling back to {@link SimConfig#defaults()} per missing value. */
    public static SimConfig load() {
        SimConfig defaults = SimConfig.defaults();
        GameProperties properties = GameProperties.load(FILE);
        return new SimConfig(
                properties.getLong("sim.seed", defaults.seed()),
                properties.getFloat("sim.tickSeconds", defaults.tickSeconds()),
                properties.getInt("sim.maxTicksPerFrame", defaults.maxTicksPerFrame()),
                map(properties, defaults.map()),
                commander(properties, defaults.commander()),
                trooper(properties, defaults.trooper()),
                spawn(properties, defaults.spawn()),
                units(properties, defaults),
                pickups(properties, defaults),
                weapons(properties, defaults),
                melee(properties, defaults.melee()),
                rabbit(properties, defaults.rabbit()),
                controlPoint(properties, defaults.controlPoint()),
                obstacle(properties, defaults.obstacle()),
                collision(properties, defaults.collision()));
    }

    /**
     * The grid a level is laid out on. The arena is whatever this adds up to, so there is no separate
     * arena size to keep in step with it.
     */
    private static SimConfig.MapConfig map(GameProperties properties, SimConfig.MapConfig defaults) {
        return new SimConfig.MapConfig(
                properties.getInt("sim.map.cols", defaults.cols()),
                properties.getInt("sim.map.rows", defaults.rows()),
                properties.getFloat("sim.map.tileSize", defaults.tileSize()),
                properties.getInt("sim.map.regions", defaults.regions()));
    }

    /** The squad, under {@code sim.trooper}. Squad size is loadable but a scenario usually overrides it. */
    private static SimConfig.TrooperConfig trooper(GameProperties properties, SimConfig.TrooperConfig defaults) {
        return new SimConfig.TrooperConfig(
                properties.getFloat("sim.trooper.radius", defaults.radius()),
                properties.getFloat("sim.trooper.speed", defaults.speed()),
                properties.getInt("sim.trooper.squadSize", defaults.squadSize()),
                properties.getFloat("sim.trooper.health", defaults.health()),
                properties.getFloat("sim.trooper.deathThroesSeconds", defaults.deathThroesSeconds()),
                defaults.weapon(),
                avoidance(properties, "sim.trooper.avoidance", defaults.avoidance()),
                properties.getFloat("sim.trooper.urgency", defaults.urgency()),
                properties.getFloat("sim.trooper.scanSpread", defaults.scanSpread()),
                properties.getFloat("sim.trooper.scanMinSeconds", defaults.scanMinSeconds()),
                properties.getFloat("sim.trooper.scanMaxSeconds", defaults.scanMaxSeconds()),
                properties.getFloat("sim.trooper.arriveRadius", defaults.arriveRadius()),
                perception(properties, "sim.trooper.perception", defaults.perception()),
                properties.getFloat("sim.trooper.orderRadius", defaults.orderRadius()),
                properties.getFloat("sim.trooper.moveOutDistance", defaults.moveOutDistance()),
                properties.getFloat("sim.trooper.moveOutSpeedFactor", defaults.moveOutSpeedFactor()),
                properties.getFloat("sim.trooper.followDistance", defaults.followDistance()),
                properties.getFloat("sim.trooper.strikeChancePerSecond", defaults.strikeChancePerSecond()));
    }

    /** Where the player's side starts, under {@code sim.spawn}. */
    private static SimConfig.SpawnConfig spawn(GameProperties properties, SimConfig.SpawnConfig defaults) {
        return new SimConfig.SpawnConfig(
                properties.getInt("sim.spawn.col", defaults.col()),
                properties.getInt("sim.spawn.width", defaults.width()),
                properties.getInt("sim.spawn.height", defaults.height()));
    }

    private static SimConfig.CommanderConfig commander(GameProperties properties, SimConfig.CommanderConfig defaults) {
        return new SimConfig.CommanderConfig(
                properties.getFloat("sim.commander.radius", defaults.radius()),
                properties.getFloat("sim.commander.speed", defaults.speed()),
                properties.getFloat("sim.commander.health", defaults.health()),
                properties.getFloat("sim.commander.deathThroesSeconds", defaults.deathThroesSeconds()),
                properties.getFloat("sim.commander.respawnDelaySeconds", defaults.respawnDelaySeconds()),
                properties.getFloat("sim.commander.respawnMargin", defaults.respawnMargin()),
                properties.getFloat("sim.commander.respawnInvulnerableSeconds", defaults.respawnInvulnerableSeconds()));
    }

    /**
     * Every strain, each under {@code sim.unit.<kind>} with the same keys below it.
     *
     * <p>One loop over the enum rather than a method per strain: adding a strain is a constant, a
     * default and a block in the file, and nothing here changes. A strain with no block in the file
     * simply falls back to its default, field by field.
     *
     * <p>What a strain <em>becomes</em> and what it is <em>drawn as</em> are deliberately not
     * loadable. Both are structure rather than tuning - an evolution chain read out of a properties
     * file could be edited into a loop, and a look is not a number anyone balances.
     */
    private static Defs<UnitKind, UnitDef> units(GameProperties properties, SimConfig defaults) {
        Defs.Builder<UnitKind, UnitDef> builder = Defs.builder(UnitKind.class);
        for (UnitKind kind : UnitKind.values()) {
            builder.put(
                    kind, unit(properties, "sim.unit." + kind.name().toLowerCase(Locale.ROOT), defaults.unit(kind)));
        }
        return builder.build();
    }

    /**
     * Every kind of pickup, each under {@code sim.pickup.<kind>}.
     *
     * <p>Only the size is loadable. What a pickup <em>grants</em> is structure rather than tuning -
     * which effect it has and which strain it names - and it stays in {@code SimConfig} for the same
     * reason a strain's {@code evolvesTo} does. The number that balances an eating evolution is
     * {@code feedToEvolve} on the strain, and that is loaded.
     */
    private static Defs<PickupKind, PickupDef> pickups(GameProperties properties, SimConfig defaults) {
        Defs.Builder<PickupKind, PickupDef> builder = Defs.builder(PickupKind.class);
        for (PickupKind kind : PickupKind.values()) {
            PickupDef fallback = defaults.pickup(kind);
            String prefix = "sim.pickup." + kind.name().toLowerCase(Locale.ROOT);
            builder.put(kind, fallback.withRadius(properties.getFloat(prefix + ".radius", fallback.radius())));
        }
        return builder.build();
    }

    private static UnitDef unit(GameProperties properties, String prefix, UnitDef defaults) {
        return new UnitDef(
                properties.getFloat(prefix + ".radius", defaults.radius()),
                properties.getFloat(prefix + ".speed", defaults.speed()),
                properties.getInt(prefix + ".count", defaults.count()),
                properties.getFloat(prefix + ".health", defaults.health()),
                properties.getFloat(prefix + ".contactDamage", defaults.contactDamage()),
                properties.getFloat(prefix + ".attackCooldownSeconds", defaults.attackCooldownSeconds()),
                properties.getFloat(prefix + ".victimInvulnerableSeconds", defaults.victimInvulnerableSeconds()),
                properties.getFloat(prefix + ".lockSeconds", defaults.lockSeconds()),
                properties.getFloat(prefix + ".deathThroesSeconds", defaults.deathThroesSeconds()),
                properties.getInt(prefix + ".feedToEvolve", defaults.feedToEvolve()),
                defaults.evolvesTo(),
                perception(properties, prefix + ".perception", defaults.perception()),
                wander(properties, prefix + ".wander", defaults.wander()),
                avoidance(properties, prefix + ".avoidance", defaults.avoidance()),
                leap(properties, prefix + ".leap", defaults.leap()),
                defaults.look());
    }

    /**
     * What a strain can notice, under its own prefix.
     *
     * <p>{@code alwaysAggressive} is loadable so a scenario or a build can turn detection off
     * wholesale and get back the standing fight every existing arena was tuned around.
     */
    private static PerceptionDef perception(GameProperties properties, String prefix, PerceptionDef defaults) {
        return new PerceptionDef(
                properties.getBoolean(prefix + ".alwaysAggressive", defaults.alwaysAggressive()),
                properties.getBoolean(prefix + ".losesInterest", defaults.losesInterest()),
                properties.getFloat(prefix + ".detectionRange", defaults.detectionRange()),
                properties.getFloat(prefix + ".loseRange", defaults.loseRange()),
                properties.getFloat(prefix + ".secondaryRange", defaults.secondaryRange()),
                properties.getFloat(prefix + ".arcDegrees", defaults.arcDegrees()),
                properties.getBoolean(prefix + ".requiresClearLine", defaults.requiresClearLine()));
    }

    /** How a strain idles, under its own prefix. */
    private static WanderDef wander(GameProperties properties, String prefix, WanderDef defaults) {
        return new WanderDef(
                properties.getBoolean(prefix + ".enabled", defaults.enabled()),
                properties.getFloat(prefix + ".radius", defaults.radius()),
                properties.getFloat(prefix + ".speedFactor", defaults.speedFactor()),
                properties.getFloat(prefix + ".arriveRadius", defaults.arriveRadius()),
                properties.getFloat(prefix + ".pauseMinSeconds", defaults.pauseMinSeconds()),
                properties.getFloat(prefix + ".pauseMaxSeconds", defaults.pauseMaxSeconds()),
                properties.getFloat(prefix + ".waypointTimeoutSeconds", defaults.waypointTimeoutSeconds()));
    }

    /** How far ahead a strain looks for things in its way, under its own prefix. */
    private static AvoidanceDef avoidance(GameProperties properties, String prefix, AvoidanceDef defaults) {
        return new AvoidanceDef(
                properties.getBoolean(prefix + ".enabled", defaults.enabled()),
                properties.getFloat(prefix + ".scanRange", defaults.scanRange()),
                properties.getFloat(prefix + ".lookaheadSeconds", defaults.lookaheadSeconds()),
                properties.getFloat(prefix + ".weight", defaults.weight()));
    }

    /** Every strain configures its leap under its own prefix; the keys below it are the same. */
    private static LeapDef leap(GameProperties properties, String prefix, LeapDef defaults) {
        return new LeapDef(
                properties.getBoolean(prefix + ".enabled", defaults.enabled()),
                properties.getFloat(prefix + ".triggerRange", defaults.triggerRange()),
                properties.getFloat(prefix + ".speed", defaults.speed()),
                properties.getFloat(prefix + ".airborneSeconds", defaults.airborneSeconds()),
                properties.getInt(prefix + ".sequenceLength", defaults.sequenceLength()),
                properties.getFloat(prefix + ".betweenSeconds", defaults.betweenSeconds()),
                properties.getFloat(prefix + ".cooldownSeconds", defaults.cooldownSeconds()),
                properties.getFloat(prefix + ".hesitationMinSeconds", defaults.hesitationMinSeconds()),
                properties.getFloat(prefix + ".hesitationMaxSeconds", defaults.hesitationMaxSeconds()));
    }

    private static SimConfig.MeleeConfig melee(GameProperties properties, SimConfig.MeleeConfig defaults) {
        return new SimConfig.MeleeConfig(
                properties.getFloat("sim.melee.cooldownSeconds", defaults.cooldownSeconds()),
                properties.getFloat("sim.melee.windupSeconds", defaults.windupSeconds()),
                properties.getFloat("sim.melee.activeSeconds", defaults.activeSeconds()),
                properties.getFloat("sim.melee.reach", defaults.reach()),
                properties.getFloat("sim.melee.radius", defaults.radius()),
                properties.getFloat("sim.melee.damage", defaults.damage()),
                properties.getFloat("sim.melee.arcDegrees", defaults.arcDegrees()),
                properties.getFloat("sim.melee.lockSeconds", defaults.lockSeconds()),
                properties.getBoolean("sim.melee.locksCommittedOnly", defaults.locksCommittedOnly()),
                properties.getFloat("sim.melee.clinchCarrySpeed", defaults.clinchCarrySpeed()),
                properties.getFloat("sim.melee.clinchCarryDecay", defaults.clinchCarryDecay()),
                properties.getFloat("sim.melee.knockbackSpeed", defaults.knockbackSpeed()),
                properties.getFloat("sim.melee.knockbackDecay", defaults.knockbackDecay()));
    }

    /**
     * Every gun, each under {@code sim.weapon.<kind>} with the same keys. What its shots look like
     * stays in {@code SimConfig}: a look is not a number anyone balances.
     */
    private static Defs<WeaponKind, WeaponDef> weapons(GameProperties properties, SimConfig defaults) {
        Defs.Builder<WeaponKind, WeaponDef> builder = Defs.builder(WeaponKind.class);
        for (WeaponKind kind : WeaponKind.values()) {
            WeaponDef fallback = defaults.weapon(kind);
            String prefix = "sim.weapon." + kind.name().toLowerCase(Locale.ROOT);
            builder.put(
                    kind,
                    new WeaponDef(
                            properties.getFloat(prefix + ".cooldownSeconds", fallback.cooldownSeconds()),
                            properties.getFloat(prefix + ".projectileSpeed", fallback.projectileSpeed()),
                            properties.getFloat(prefix + ".projectileRadius", fallback.projectileRadius()),
                            properties.getFloat(prefix + ".damage", fallback.damage()),
                            fallback.projectileLook()));
        }
        return builder.build();
    }

    private static SimConfig.CollisionConfig collision(GameProperties properties, SimConfig.CollisionConfig defaults) {
        return new SimConfig.CollisionConfig(properties.getInt("sim.collision.passes", defaults.passes()));
    }

    /** What it takes to hold a point, under {@code sim.controlPoint}. */
    private static SimConfig.ControlPointConfig controlPoint(
            GameProperties properties, SimConfig.ControlPointConfig defaults) {
        return new SimConfig.ControlPointConfig(
                properties.getFloat("sim.controlPoint.radius", defaults.radius()),
                properties.getFloat("sim.controlPoint.captureSeconds", defaults.captureSeconds()),
                properties.getFloat("sim.controlPoint.aggroRadius", defaults.aggroRadius()));
    }

    /** The one pickup with numbers of its own, because it is the one that moves. */
    private static SimConfig.RabbitConfig rabbit(GameProperties properties, SimConfig.RabbitConfig defaults) {
        return new SimConfig.RabbitConfig(
                properties.getFloat("sim.rabbit.speed", defaults.speed()),
                properties.getFloat("sim.rabbit.health", defaults.health()),
                properties.getFloat("sim.rabbit.deathThroesSeconds", defaults.deathThroesSeconds()),
                wander(properties, "sim.rabbit.wander", defaults.wander()),
                dodge(properties, "sim.rabbit.dodge", defaults.dodge()));
    }

    /** How a creature answers a shot, under its own prefix. */
    private static DodgeDef dodge(GameProperties properties, String prefix, DodgeDef defaults) {
        return new DodgeDef(
                properties.getBoolean(prefix + ".enabled", defaults.enabled()),
                properties.getFloat(prefix + ".reactionRange", defaults.reactionRange()),
                properties.getFloat(prefix + ".speed", defaults.speed()),
                properties.getFloat(prefix + ".durationSeconds", defaults.durationSeconds()),
                properties.getFloat(prefix + ".commitSeconds", defaults.commitSeconds()),
                properties.getFloat(prefix + ".margin", defaults.margin()));
    }

    private static SimConfig.ObstacleConfig obstacle(GameProperties properties, SimConfig.ObstacleConfig defaults) {
        return new SimConfig.ObstacleConfig(
                properties.getFloat("sim.obstacle.radius", defaults.radius()),
                properties.getInt("sim.obstacle.count", defaults.count()));
    }
}
