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
                weapon(properties, defaults.weapon()),
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
                properties.getFloat("sim.commander.speed", defaults.speed()));
    }

    private static SimConfig.EnemyConfig enemy(GameProperties properties, SimConfig.EnemyConfig defaults) {
        return new SimConfig.EnemyConfig(
                properties.getFloat("sim.enemy.radius", defaults.radius()),
                properties.getFloat("sim.enemy.speed", defaults.speed()),
                properties.getInt("sim.enemy.count", defaults.count()));
    }

    private static SimConfig.WeaponConfig weapon(GameProperties properties, SimConfig.WeaponConfig defaults) {
        return new SimConfig.WeaponConfig(
                properties.getFloat("sim.weapon.cooldownSeconds", defaults.cooldownSeconds()),
                properties.getFloat("sim.weapon.projectileSpeed", defaults.projectileSpeed()),
                properties.getFloat("sim.weapon.projectileRadius", defaults.projectileRadius()));
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
