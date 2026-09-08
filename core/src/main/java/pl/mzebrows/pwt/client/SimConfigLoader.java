package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.SimConfig;

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
                arena(properties, defaults.arena()),
                commander(properties, defaults.commander()),
                enemy(properties, defaults.enemy()),
                brute(properties, defaults.brute()),
                weapon(properties, defaults.weapon()),
                melee(properties, defaults.melee()),
                obstacle(properties, defaults.obstacle()),
                collision(properties, defaults.collision()));
    }

    private static SimConfig.ArenaConfig arena(GameProperties properties, SimConfig.ArenaConfig defaults) {
        return new SimConfig.ArenaConfig(
                properties.getFloat("sim.arena.width", defaults.width()),
                properties.getFloat("sim.arena.height", defaults.height()));
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

    private static SimConfig.EnemyConfig enemy(GameProperties properties, SimConfig.EnemyConfig defaults) {
        return new SimConfig.EnemyConfig(
                properties.getFloat("sim.enemy.radius", defaults.radius()),
                properties.getFloat("sim.enemy.speed", defaults.speed()),
                properties.getInt("sim.enemy.count", defaults.count()),
                properties.getFloat("sim.enemy.health", defaults.health()),
                properties.getFloat("sim.enemy.contactDamage", defaults.contactDamage()),
                properties.getFloat("sim.enemy.attackCooldownSeconds", defaults.attackCooldownSeconds()),
                properties.getFloat("sim.enemy.victimInvulnerableSeconds", defaults.victimInvulnerableSeconds()),
                properties.getFloat("sim.enemy.lockSeconds", defaults.lockSeconds()),
                properties.getFloat("sim.enemy.deathThroesSeconds", defaults.deathThroesSeconds()),
                leap(properties, "sim.enemy.leap", defaults.leap()));
    }

    private static SimConfig.BruteConfig brute(GameProperties properties, SimConfig.BruteConfig defaults) {
        return new SimConfig.BruteConfig(
                properties.getFloat("sim.brute.radius", defaults.radius()),
                properties.getFloat("sim.brute.speed", defaults.speed()),
                properties.getInt("sim.brute.count", defaults.count()),
                properties.getFloat("sim.brute.health", defaults.health()),
                properties.getFloat("sim.brute.contactDamage", defaults.contactDamage()),
                properties.getFloat("sim.brute.attackCooldownSeconds", defaults.attackCooldownSeconds()),
                properties.getFloat("sim.brute.victimInvulnerableSeconds", defaults.victimInvulnerableSeconds()),
                properties.getFloat("sim.brute.lockSeconds", defaults.lockSeconds()),
                properties.getFloat("sim.brute.deathThroesSeconds", defaults.deathThroesSeconds()),
                leap(properties, "sim.brute.leap", defaults.leap()));
    }

    /** Every enemy kind configures its leap under its own prefix; the keys below it are the same. */
    private static SimConfig.LeapConfig leap(GameProperties properties, String prefix, SimConfig.LeapConfig defaults) {
        return new SimConfig.LeapConfig(
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

    private static SimConfig.WeaponConfig weapon(GameProperties properties, SimConfig.WeaponConfig defaults) {
        return new SimConfig.WeaponConfig(
                properties.getFloat("sim.weapon.cooldownSeconds", defaults.cooldownSeconds()),
                properties.getFloat("sim.weapon.projectileSpeed", defaults.projectileSpeed()),
                properties.getFloat("sim.weapon.projectileRadius", defaults.projectileRadius()),
                properties.getFloat("sim.weapon.damage", defaults.damage()));
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
