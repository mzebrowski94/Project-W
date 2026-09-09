package pl.mzebrows.pwt.client;

import java.util.Locale;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Defs;
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
                units(properties, defaults),
                pickups(properties, defaults),
                weapons(properties, defaults),
                melee(properties, defaults.melee()),
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
                properties.getFloat(prefix + ".detectionRange", defaults.detectionRange()),
                properties.getFloat(prefix + ".loseRange", defaults.loseRange()));
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

    private static SimConfig.ObstacleConfig obstacle(GameProperties properties, SimConfig.ObstacleConfig defaults) {
        return new SimConfig.ObstacleConfig(
                properties.getFloat("sim.obstacle.radius", defaults.radius()),
                properties.getInt("sim.obstacle.count", defaults.count()));
    }
}
