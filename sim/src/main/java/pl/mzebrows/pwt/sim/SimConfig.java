package pl.mzebrows.pwt.sim;

/**
 * Tunables the simulation reads at construction time. Loaded from {@code game.properties} by the
 * client module - the simulation itself never touches the filesystem.
 *
 * <p>Grouped into nested records rather than one flat parameter list so a system can be handed only
 * the group it needs, and so adding a tunable touches one constructor instead of every call site.
 *
 * @param seed seed for the simulation's {@link Rng}
 * @param tickSeconds length of one fixed simulation step
 * @param maxTicksPerFrame ceiling on catch-up steps, so a stalled frame cannot spiral
 * @param arena the rectangle the level is played inside
 * @param commander the player-controlled unit
 * @param enemy the units that chase it
 * @param weapon the commander's gun
 * @param obstacle the static circles that block movement
 * @param collision how hard the constraint solver works
 */
public record SimConfig(
        long seed,
        float tickSeconds,
        int maxTicksPerFrame,
        ArenaConfig arena,
        CommanderConfig commander,
        EnemyConfig enemy,
        WeaponConfig weapon,
        ObstacleConfig obstacle,
        CollisionConfig collision) {

    public SimConfig {
        if (tickSeconds <= 0f) {
            throw new IllegalArgumentException("tickSeconds must be positive, was " + tickSeconds);
        }
        if (maxTicksPerFrame < 1) {
            throw new IllegalArgumentException("maxTicksPerFrame must be at least 1, was " + maxTicksPerFrame);
        }
    }

    /** Values used when {@code game.properties} is missing or unreadable. */
    public static SimConfig defaults() {
        return new SimConfig(
                1L,
                1f / 60f,
                5,
                new ArenaConfig(160f, 90f),
                new CommanderConfig(2f, 28f, 100f, 3f, 6f, 2f),
                new EnemyConfig(1.8f, 14f, 12, 100f, 100f, 1f, 0.6f),
                new WeaponConfig(0.18f, 70f, 0.6f, 50f),
                new ObstacleConfig(3.5f, 8),
                new CollisionConfig(3));
    }

    /** This config with a different arena. Scenarios use these to vary one thing without restating the rest. */
    public SimConfig with(ArenaConfig newArena) {
        return new SimConfig(
                seed, tickSeconds, maxTicksPerFrame, newArena, commander, enemy, weapon, obstacle, collision);
    }

    /** This config with a different commander. */
    public SimConfig with(CommanderConfig newCommander) {
        return new SimConfig(
                seed, tickSeconds, maxTicksPerFrame, arena, newCommander, enemy, weapon, obstacle, collision);
    }

    /** This config with different enemies. */
    public SimConfig with(EnemyConfig newEnemy) {
        return new SimConfig(
                seed, tickSeconds, maxTicksPerFrame, arena, commander, newEnemy, weapon, obstacle, collision);
    }

    /** This config with a different weapon. */
    public SimConfig with(WeaponConfig newWeapon) {
        return new SimConfig(
                seed, tickSeconds, maxTicksPerFrame, arena, commander, enemy, newWeapon, obstacle, collision);
    }

    /** This config with different obstacles. */
    public SimConfig with(ObstacleConfig newObstacle) {
        return new SimConfig(
                seed, tickSeconds, maxTicksPerFrame, arena, commander, enemy, weapon, newObstacle, collision);
    }

    /**
     * The largest radius any entity can have, which sets the collision grid's cell size. Every body
     * is a circle (ADR 0007), so one number covers all of them.
     */
    public float largestRadius() {
        return Math.max(
                Math.max(commander.radius(), enemy.radius()), Math.max(weapon.projectileRadius(), obstacle.radius()));
    }

    /**
     * How hard the constraint solver works.
     *
     * @param passes how many times per tick overlap resolution and arena clamping are applied. One
     *     is enough for open space; a unit squeezed between an obstacle and a wall needs the second,
     *     and the third is headroom. Raising it costs time and buys stability, never behaviour.
     */
    public record CollisionConfig(int passes) {
        public CollisionConfig {
            if (passes < 1) {
                throw new IllegalArgumentException("collision passes must be at least 1, was " + passes);
            }
        }
    }

    /**
     * The rectangle a level is played inside, in world units. Not a body - the boundary is handled
     * by clamping a position, never by a pairwise test.
     *
     * @param width arena width in world units
     * @param height arena height in world units
     */
    public record ArenaConfig(float width, float height) {
        public ArenaConfig {
            requirePositive("arena width", width);
            requirePositive("arena height", height);
        }
    }

    /**
     * The player-controlled unit.
     *
     * @param radius its circular body, in world units
     * @param speed how fast it moves, in world units per second
     * @param health damage it absorbs before dying
     * @param respawnDelaySeconds how long after dying before it comes back
     * @param respawnMargin how far in from the left edge of the arena it comes back, in world units
     * @param respawnInvulnerableSeconds protection on arrival, so it does not die again immediately
     */
    public record CommanderConfig(
            float radius,
            float speed,
            float health,
            float respawnDelaySeconds,
            float respawnMargin,
            float respawnInvulnerableSeconds) {
        public CommanderConfig {
            requirePositive("commander radius", radius);
            requirePositive("commander speed", speed);
            requirePositive("commander health", health);
            requireNotNegative("commander respawnDelaySeconds", respawnDelaySeconds);
            requireNotNegative("commander respawnMargin", respawnMargin);
            requireNotNegative("commander respawnInvulnerableSeconds", respawnInvulnerableSeconds);
        }

        /** How fast it moves, everything else unchanged. */
        public CommanderConfig withSpeed(float newSpeed) {
            return new CommanderConfig(
                    radius, newSpeed, health, respawnDelaySeconds, respawnMargin, respawnInvulnerableSeconds);
        }

        /** How much it can take, everything else unchanged. */
        public CommanderConfig withHealth(float newHealth) {
            return new CommanderConfig(
                    radius, speed, newHealth, respawnDelaySeconds, respawnMargin, respawnInvulnerableSeconds);
        }
    }

    /**
     * The units that chase the commander.
     *
     * @param radius circular body, in world units
     * @param speed world units per second; slower than the commander, or there is no escaping them
     * @param count how many the scenario spawns
     * @param health damage one absorbs before dying
     * @param contactDamage what it does to whatever it touches on the other side
     * @param attackCooldownSeconds how long before it can strike again
     * @param victimInvulnerableSeconds how long its victim is protected afterwards, so a crowd cannot
     *     delete a unit in a single tick
     */
    public record EnemyConfig(
            float radius,
            float speed,
            int count,
            float health,
            float contactDamage,
            float attackCooldownSeconds,
            float victimInvulnerableSeconds) {
        public EnemyConfig {
            requirePositive("enemy radius", radius);
            requirePositive("enemy speed", speed);
            requireNotNegative("enemy count", count);
            requirePositive("enemy health", health);
            requireNotNegative("enemy contactDamage", contactDamage);
            requireNotNegative("enemy attackCooldownSeconds", attackCooldownSeconds);
            requireNotNegative("enemy victimInvulnerableSeconds", victimInvulnerableSeconds);
        }

        /** How many to spawn, everything else unchanged. A scenario varying one field says only that. */
        public EnemyConfig withCount(int newCount) {
            return new EnemyConfig(
                    radius, speed, newCount, health, contactDamage, attackCooldownSeconds, victimInvulnerableSeconds);
        }

        /** How hard one hits, everything else unchanged. */
        public EnemyConfig withContactDamage(float newContactDamage) {
            return new EnemyConfig(
                    radius, speed, count, health, newContactDamage, attackCooldownSeconds, victimInvulnerableSeconds);
        }
    }

    /**
     * The commander's gun.
     *
     * @param cooldownSeconds minimum time between shots
     * @param projectileSpeed world units per second
     * @param projectileRadius circular body of a fired projectile
     * @param damage what one projectile takes off what it hits
     */
    public record WeaponConfig(float cooldownSeconds, float projectileSpeed, float projectileRadius, float damage) {
        public WeaponConfig {
            requireNotNegative("weapon cooldownSeconds", cooldownSeconds);
            requirePositive("weapon projectileSpeed", projectileSpeed);
            requirePositive("weapon projectileRadius", projectileRadius);
            requireNotNegative("weapon damage", damage);
        }
    }

    /**
     * The static circles that block movement and projectiles.
     *
     * @param radius circular body, in world units
     * @param count how many the scenario spawns
     */
    public record ObstacleConfig(float radius, int count) {
        public ObstacleConfig {
            requirePositive("obstacle radius", radius);
            requireNotNegative("obstacle count", count);
        }

        /** How many to scatter, radius unchanged. */
        public ObstacleConfig withCount(int newCount) {
            return new ObstacleConfig(radius, newCount);
        }
    }

    private static void requirePositive(String name, float value) {
        if (value <= 0f) {
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        }
    }

    private static void requireNotNegative(String name, float value) {
        if (value < 0f) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }
}
