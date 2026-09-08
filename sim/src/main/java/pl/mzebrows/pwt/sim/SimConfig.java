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
 * @param brute the heavy units that chase it and survive a hand strike
 * @param weapon the commander's gun
 * @param melee the commander's hand strike
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
        BruteConfig brute,
        WeaponConfig weapon,
        MeleeConfig melee,
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
                new CommanderConfig(2f, 28f, 100f, 1.5f, 3f, 6f, 2f),
                new EnemyConfig(
                        1.8f,
                        10.5f,
                        12,
                        100f,
                        100f,
                        1f,
                        0.6f,
                        1.5f,
                        1f,
                        new LeapConfig(true, 16f, 42f, 0.28f, 2, 0.18f, 1.6f, 0.08f, 0.22f)),
                new BruteConfig(
                        2.6f,
                        6.75f,
                        3,
                        250f,
                        100f,
                        1.2f,
                        0.6f,
                        1.5f,
                        1f,
                        new LeapConfig(true, 22f, 34f, 0.42f, 3, 0.26f, 2.4f, 0.18f, 0.42f)),
                new WeaponConfig(0.18f, 70f, 0.6f, 50f),
                new MeleeConfig(0.45f, 0f, 0.12f, 1.2f, 1.4f, 120f, 180f, 1f, 45f, 120f),
                new ObstacleConfig(3.5f, 8),
                new CollisionConfig(3));
    }

    /** This config seeded differently. The one field a replay test varies and a scenario never does. */
    public SimConfig withSeed(long newSeed) {
        return new SimConfig(
                newSeed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                commander,
                enemy,
                brute,
                weapon,
                melee,
                obstacle,
                collision);
    }

    /** This config with a different arena. Scenarios use these to vary one thing without restating the rest. */
    public SimConfig with(ArenaConfig newArena) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                newArena,
                commander,
                enemy,
                brute,
                weapon,
                melee,
                obstacle,
                collision);
    }

    /** This config with a different commander. */
    public SimConfig with(CommanderConfig newCommander) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                newCommander,
                enemy,
                brute,
                weapon,
                melee,
                obstacle,
                collision);
    }

    /** This config with different enemies. */
    public SimConfig with(EnemyConfig newEnemy) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                commander,
                newEnemy,
                brute,
                weapon,
                melee,
                obstacle,
                collision);
    }

    /** This config with different brutes. */
    public SimConfig with(BruteConfig newBrute) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                commander,
                enemy,
                newBrute,
                weapon,
                melee,
                obstacle,
                collision);
    }

    /** This config with a different weapon. */
    public SimConfig with(WeaponConfig newWeapon) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                commander,
                enemy,
                brute,
                newWeapon,
                melee,
                obstacle,
                collision);
    }

    /** This config with a different hand strike. */
    public SimConfig with(MeleeConfig newMelee) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                commander,
                enemy,
                brute,
                weapon,
                newMelee,
                obstacle,
                collision);
    }

    /** This config with different obstacles. */
    public SimConfig with(ObstacleConfig newObstacle) {
        return new SimConfig(
                seed,
                tickSeconds,
                maxTicksPerFrame,
                arena,
                commander,
                enemy,
                brute,
                weapon,
                melee,
                newObstacle,
                collision);
    }

    /**
     * The largest radius any entity can have, which sets the collision grid's cell size. Every body
     * is a circle (ADR 0007), so one number covers all of them.
     */
    public float largestRadius() {
        float units = Math.max(Math.max(commander.radius(), enemy.radius()), brute.radius());
        float shots = Math.max(weapon.projectileRadius(), melee.radius());
        return Math.max(Math.max(units, shots), obstacle.radius());
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
     * @param deathThroesSeconds how long its body stays on the field after a killing blow, before it
     *     is removed and the respawn countdown starts. The window the dying animation will play in.
     * @param respawnDelaySeconds how long after the body is removed before it comes back
     * @param respawnMargin how far in from the left edge of the arena it comes back, in world units
     * @param respawnInvulnerableSeconds protection on arrival, so it does not die again immediately
     */
    public record CommanderConfig(
            float radius,
            float speed,
            float health,
            float deathThroesSeconds,
            float respawnDelaySeconds,
            float respawnMargin,
            float respawnInvulnerableSeconds) {
        public CommanderConfig {
            requirePositive("commander radius", radius);
            requirePositive("commander speed", speed);
            requirePositive("commander health", health);
            requireNotNegative("commander deathThroesSeconds", deathThroesSeconds);
            requireNotNegative("commander respawnDelaySeconds", respawnDelaySeconds);
            requireNotNegative("commander respawnMargin", respawnMargin);
            requireNotNegative("commander respawnInvulnerableSeconds", respawnInvulnerableSeconds);
        }

        /** How fast it moves, everything else unchanged. */
        public CommanderConfig withSpeed(float newSpeed) {
            return new CommanderConfig(
                    radius,
                    newSpeed,
                    health,
                    deathThroesSeconds,
                    respawnDelaySeconds,
                    respawnMargin,
                    respawnInvulnerableSeconds);
        }

        /** How much it can take, everything else unchanged. */
        public CommanderConfig withHealth(float newHealth) {
            return new CommanderConfig(
                    radius,
                    speed,
                    newHealth,
                    deathThroesSeconds,
                    respawnDelaySeconds,
                    respawnMargin,
                    respawnInvulnerableSeconds);
        }

        /** How long the body lingers, everything else unchanged. */
        public CommanderConfig withDeathThroesSeconds(float newDeathThroesSeconds) {
            return new CommanderConfig(
                    radius,
                    speed,
                    health,
                    newDeathThroesSeconds,
                    respawnDelaySeconds,
                    respawnMargin,
                    respawnInvulnerableSeconds);
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
     * @param lockSeconds how long a landed blow holds both it and its victim still - the window the
     *     kill animation will play in
     * @param deathThroesSeconds how long its body stays on the field after a killing blow. Matching
     *     the commander's melee lock is what makes a lethal answer to a leap hold as a clinch for
     *     exactly as long as the commander is held by it.
     * @param leap how it closes the last of the distance
     */
    public record EnemyConfig(
            float radius,
            float speed,
            int count,
            float health,
            float contactDamage,
            float attackCooldownSeconds,
            float victimInvulnerableSeconds,
            float lockSeconds,
            float deathThroesSeconds,
            LeapConfig leap) {
        public EnemyConfig {
            requirePositive("enemy radius", radius);
            requirePositive("enemy speed", speed);
            requireNotNegative("enemy count", count);
            requirePositive("enemy health", health);
            requireNotNegative("enemy contactDamage", contactDamage);
            requireNotNegative("enemy attackCooldownSeconds", attackCooldownSeconds);
            requireNotNegative("enemy victimInvulnerableSeconds", victimInvulnerableSeconds);
            requireNotNegative("enemy lockSeconds", lockSeconds);
            requireNotNegative("enemy deathThroesSeconds", deathThroesSeconds);
        }

        /** How many to spawn, everything else unchanged. A scenario varying one field says only that. */
        public EnemyConfig withCount(int newCount) {
            return new EnemyConfig(
                    radius,
                    speed,
                    newCount,
                    health,
                    contactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    leap);
        }

        /** How hard one hits, everything else unchanged. */
        public EnemyConfig withContactDamage(float newContactDamage) {
            return new EnemyConfig(
                    radius,
                    speed,
                    count,
                    health,
                    newContactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    leap);
        }

        /** How it closes the distance, everything else unchanged. */
        public EnemyConfig withLeap(LeapConfig newLeap) {
            return new EnemyConfig(
                    radius,
                    speed,
                    count,
                    health,
                    contactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    newLeap);
        }
    }

    /**
     * The heavy units. Slower and tougher than an enemy, and the reason the hand strike needs a stun
     * at all: a brute survives one and is knocked back rather than killed.
     *
     * @param radius circular body, in world units
     * @param speed world units per second
     * @param count how many the scenario spawns
     * @param health damage one absorbs before dying; more than one hand strike, on purpose
     * @param contactDamage what it does to whatever it touches on the other side
     * @param attackCooldownSeconds how long before it can strike again
     * @param victimInvulnerableSeconds how long its victim is protected afterwards
     * @param lockSeconds how long a landed blow holds both it and its victim still
     * @param deathThroesSeconds how long its body stays on the field after a killing blow
     * @param leap how it closes the last of the distance; slower and better telegraphed than an
     *     enemy's, so the extra health does not simply make it unfightable
     */
    public record BruteConfig(
            float radius,
            float speed,
            int count,
            float health,
            float contactDamage,
            float attackCooldownSeconds,
            float victimInvulnerableSeconds,
            float lockSeconds,
            float deathThroesSeconds,
            LeapConfig leap) {
        public BruteConfig {
            requirePositive("brute radius", radius);
            requirePositive("brute speed", speed);
            requireNotNegative("brute count", count);
            requirePositive("brute health", health);
            requireNotNegative("brute contactDamage", contactDamage);
            requireNotNegative("brute attackCooldownSeconds", attackCooldownSeconds);
            requireNotNegative("brute victimInvulnerableSeconds", victimInvulnerableSeconds);
            requireNotNegative("brute lockSeconds", lockSeconds);
            requireNotNegative("brute deathThroesSeconds", deathThroesSeconds);
        }

        /** How many to spawn, everything else unchanged. */
        public BruteConfig withCount(int newCount) {
            return new BruteConfig(
                    radius,
                    speed,
                    newCount,
                    health,
                    contactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    leap);
        }

        /** How much it can take, everything else unchanged. */
        public BruteConfig withHealth(float newHealth) {
            return new BruteConfig(
                    radius,
                    speed,
                    count,
                    newHealth,
                    contactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    leap);
        }

        /** How hard one hits, everything else unchanged. */
        public BruteConfig withContactDamage(float newContactDamage) {
            return new BruteConfig(
                    radius,
                    speed,
                    count,
                    health,
                    newContactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    leap);
        }

        /** How it closes the distance, everything else unchanged. */
        public BruteConfig withLeap(LeapConfig newLeap) {
            return new BruteConfig(
                    radius,
                    speed,
                    count,
                    health,
                    contactDamage,
                    attackCooldownSeconds,
                    victimInvulnerableSeconds,
                    lockSeconds,
                    deathThroesSeconds,
                    newLeap);
        }
    }

    /**
     * How a unit throws itself at the commander to close the last of the distance.
     *
     * <p>Every field is per enemy kind, because that is the whole point: a fast thing that barely
     * telegraphs and a heavy thing that winds up visibly are the same system with different numbers.
     *
     * @param enabled whether this kind leaps at all; a unit configured off simply walks
     * @param triggerRange how close the commander must be before it commits, in world units
     * @param speed how fast it travels while airborne, in world units per second
     * @param airborneSeconds how long one leap lasts
     * @param sequenceLength how many leaps it strings together before it has to reset
     * @param betweenSeconds pause between the leaps of one sequence, while it lands and re-aims
     * @param cooldownSeconds how long after a whole sequence before it may start another
     * @param hesitationMinSeconds shortest wind-up before the first leap of a sequence
     * @param hesitationMaxSeconds longest wind-up before the first leap of a sequence. Drawn from the
     *     simulation's seeded {@link Rng}, so it varies between leaps and not between runs - the
     *     player has to read the tell rather than count the frames, and a replay still matches.
     */
    public record LeapConfig(
            boolean enabled,
            float triggerRange,
            float speed,
            float airborneSeconds,
            int sequenceLength,
            float betweenSeconds,
            float cooldownSeconds,
            float hesitationMinSeconds,
            float hesitationMaxSeconds) {
        public LeapConfig {
            requireNotNegative("leap triggerRange", triggerRange);
            requireNotNegative("leap speed", speed);
            requireNotNegative("leap airborneSeconds", airborneSeconds);
            requireNotNegative("leap betweenSeconds", betweenSeconds);
            requireNotNegative("leap cooldownSeconds", cooldownSeconds);
            requireNotNegative("leap hesitationMinSeconds", hesitationMinSeconds);
            requireNotNegative("leap hesitationMaxSeconds", hesitationMaxSeconds);
            if (sequenceLength < 1) {
                throw new IllegalArgumentException("leap sequenceLength must be at least 1, was " + sequenceLength);
            }
            if (hesitationMaxSeconds < hesitationMinSeconds) {
                throw new IllegalArgumentException("leap hesitationMaxSeconds must not be below hesitationMinSeconds");
            }
        }

        /** A kind that does not leap. */
        public static LeapConfig off() {
            return new LeapConfig(false, 0f, 0f, 0f, 1, 0f, 0f, 0f, 0f);
        }

        /** The same leap, switched on or off. */
        public LeapConfig withEnabled(boolean newEnabled) {
            return new LeapConfig(
                    newEnabled,
                    triggerRange,
                    speed,
                    airborneSeconds,
                    sequenceLength,
                    betweenSeconds,
                    cooldownSeconds,
                    hesitationMinSeconds,
                    hesitationMaxSeconds);
        }

        /** The same leap with a fixed wind-up, for a test that needs to know when the jump comes. */
        public LeapConfig withHesitation(float min, float max) {
            return new LeapConfig(
                    enabled,
                    triggerRange,
                    speed,
                    airborneSeconds,
                    sequenceLength,
                    betweenSeconds,
                    cooldownSeconds,
                    min,
                    max);
        }

        /** How many leaps in a sequence, everything else unchanged. */
        public LeapConfig withSequenceLength(int newSequenceLength) {
            return new LeapConfig(
                    enabled,
                    triggerRange,
                    speed,
                    airborneSeconds,
                    newSequenceLength,
                    betweenSeconds,
                    cooldownSeconds,
                    hesitationMinSeconds,
                    hesitationMaxSeconds);
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
     * The commander's hand strike: a short, fast sweep that answers something already committed to
     * a leap. Every field here is what will differ between melee weapons, which is why there is a
     * record rather than a handful of constants.
     *
     * @param cooldownSeconds minimum time between swings
     * @param windupSeconds delay between the input and the sweep appearing. Zero for a bare hand;
     *     the field exists because a heavier weapon is exactly this number being non-zero.
     * @param activeSeconds how long the sweep is in the world and able to connect
     * @param reach distance from the commander's centre to the centre of the sweep, in world units.
     *     Short on purpose: the sweep must not land until whatever it is answering is nearly touching,
     *     or a lethal hit kills at arm's length and the exchange reads as a swat instead of a clinch.
     *     The window the player gets is {@code activeSeconds}, not this - the sweep lingers and the
     *     leap flies into it - so shortening this moves when he must press, not how long he has.
     * @param radius circular body of the sweep itself
     * @param damage what a connecting sweep takes off what it hits
     * @param arcDegrees how far around the aim direction the sweep travels; 180 is a half-circle
     * @param lockSeconds how long a connecting sweep holds both the commander and its target still
     * @param knockbackSpeed how hard a surviving target is thrown, in world units per second
     * @param knockbackDecay how fast that throw bleeds off, in world units per second per second
     */
    public record MeleeConfig(
            float cooldownSeconds,
            float windupSeconds,
            float activeSeconds,
            float reach,
            float radius,
            float damage,
            float arcDegrees,
            float lockSeconds,
            float knockbackSpeed,
            float knockbackDecay) {
        public MeleeConfig {
            requireNotNegative("melee cooldownSeconds", cooldownSeconds);
            requireNotNegative("melee windupSeconds", windupSeconds);
            requirePositive("melee activeSeconds", activeSeconds);
            requireNotNegative("melee reach", reach);
            requirePositive("melee radius", radius);
            requireNotNegative("melee damage", damage);
            requireNotNegative("melee arcDegrees", arcDegrees);
            requireNotNegative("melee lockSeconds", lockSeconds);
            requireNotNegative("melee knockbackSpeed", knockbackSpeed);
            requireNotNegative("melee knockbackDecay", knockbackDecay);
        }

        /** How hard one swing hits, everything else unchanged. */
        public MeleeConfig withDamage(float newDamage) {
            return new MeleeConfig(
                    cooldownSeconds,
                    windupSeconds,
                    activeSeconds,
                    reach,
                    radius,
                    newDamage,
                    arcDegrees,
                    lockSeconds,
                    knockbackSpeed,
                    knockbackDecay);
        }

        /** How hard a survivor is thrown, everything else unchanged. */
        public MeleeConfig withKnockbackSpeed(float newKnockbackSpeed) {
            return new MeleeConfig(
                    cooldownSeconds,
                    windupSeconds,
                    activeSeconds,
                    reach,
                    radius,
                    damage,
                    arcDegrees,
                    lockSeconds,
                    newKnockbackSpeed,
                    knockbackDecay);
        }

        /** How long before the sweep appears, everything else unchanged. */
        public MeleeConfig withWindupSeconds(float newWindupSeconds) {
            return new MeleeConfig(
                    cooldownSeconds,
                    newWindupSeconds,
                    activeSeconds,
                    reach,
                    radius,
                    damage,
                    arcDegrees,
                    lockSeconds,
                    knockbackSpeed,
                    knockbackDecay);
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
