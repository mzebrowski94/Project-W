package pl.mzebrows.pwt.client;

import java.util.Locale;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.AvoidanceDef;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.DodgeDef;
import pl.mzebrows.pwt.sim.def.HeatDef;
import pl.mzebrows.pwt.sim.def.LeapDef;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.RangedDef;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WanderDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WeaveDef;

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
        Chances chances = chances(properties, "sim.ai.chance", defaults.chances(), defaults.chances());
        return new SimConfig(
                properties.getLong("sim.seed", defaults.seed()),
                properties.getFloat("sim.tickSeconds", defaults.tickSeconds()),
                properties.getInt("sim.maxTicksPerFrame", defaults.maxTicksPerFrame()),
                map(properties, defaults.map()),
                commander(properties, defaults.commander()),
                trooper(properties, defaults.trooper()),
                spawn(properties, defaults.spawn()),
                units(properties, defaults, chances),
                pickups(properties, defaults),
                weapons(properties, defaults),
                chances,
                melee(properties, defaults.melee()),
                blast(properties, defaults.blast()),
                grenade(properties, defaults.grenade()),
                rabbit(properties, defaults.rabbit()),
                barrel(properties, defaults.barrel()),
                casualty(properties, defaults.casualty()),
                action(properties, defaults.action()),
                controlPoint(properties, defaults.controlPoint()),
                turret(properties, defaults.turret()),
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
                properties.getFloat("sim.trooper.forwardDistance", defaults.forwardDistance()),
                properties.getFloat("sim.trooper.forwardSpeedFactor", defaults.forwardSpeedFactor()),
                properties.getFloat("sim.trooper.followDistance", defaults.followDistance()),
                properties.getFloat("sim.trooper.patchRadius", defaults.patchRadius()));
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
                properties.getFloat("sim.commander.respawnInvulnerableSeconds", defaults.respawnInvulnerableSeconds()),
                properties.getFloat("sim.commander.leashRadius", defaults.leashRadius()));
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
    private static Defs<UnitKind, UnitDef> units(GameProperties properties, SimConfig defaults, Chances shared) {
        Defs.Builder<UnitKind, UnitDef> builder = Defs.builder(UnitKind.class);
        for (UnitKind kind : UnitKind.values()) {
            builder.put(
                    kind,
                    unit(properties, "sim.unit." + kind.name().toLowerCase(Locale.ROOT), defaults.unit(kind), shared));
        }
        return builder.build();
    }

    /**
     * How often each occasional behaviour happens, under the given prefix and keyed by the constant's
     * own lowercase name.
     *
     * <p>A behaviour with no key of its own falls back through two levels, and which one wins is the
     * whole of what makes a strain override work. <strong>A strain that has already declared a
     * different number keeps it</strong> - that is what {@code defaults()} declaring a spitter far
     * hungrier for barrels than a grunt actually means - and a strain that is sitting on the game's
     * own number follows the shared block, so retuning a behaviour for everybody is one line in
     * {@code game.properties} and does not have to be repeated per strain.
     *
     * <p>{@code own} and {@code shared} are the same table for the shared block itself, where the
     * two levels collapse into one.
     */
    private static Chances chances(GameProperties properties, String prefix, Chances shared, Chances own) {
        Chances loaded = own;
        for (AiChance kind : AiChance.values()) {
            String key = prefix + "." + kind.name().toLowerCase(Locale.ROOT);
            float fallback = own.of(kind) == kind.standard() ? shared.of(kind) : own.of(kind);
            loaded = loaded.with(kind, properties.getFloat(key, fallback));
        }
        return loaded;
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

    private static UnitDef unit(GameProperties properties, String prefix, UnitDef defaults, Chances shared) {
        return new UnitDef(
                properties.getFloat(prefix + ".radius", defaults.radius()),
                properties.getFloat(prefix + ".speed", defaults.speed()),
                properties.getInt(prefix + ".count", defaults.count()),
                properties.getFloat(prefix + ".health", defaults.health()),
                properties.getFloat(prefix + ".contactDamage", defaults.contactDamage()),
                properties.getFloat(prefix + ".attackArcDegrees", defaults.attackArcDegrees()),
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
                ranged(properties, prefix + ".ranged", defaults.ranged()),
                weave(properties, prefix + ".weave", defaults.weave()),
                dodge(properties, prefix + ".dodge", defaults.dodge()),
                chances(properties, prefix + ".chance", shared, defaults.chances()),
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

    /**
     * How a strain shoots, under {@code <prefix>.ranged}. Which guns it carries - the one it starts
     * with and the one a barrel upgrades it to - is structure rather than tuning and stays in the
     * defaults, exactly as {@code evolvesTo} does.
     */
    private static RangedDef ranged(GameProperties properties, String prefix, RangedDef defaults) {
        return new RangedDef(
                properties.getBoolean(prefix + ".enabled", defaults.enabled()),
                properties.getFloat(prefix + ".range", defaults.range()),
                properties.getFloat(prefix + ".spreadDegrees", defaults.spreadDegrees()),
                properties.getBoolean(prefix + ".haltsToFire", defaults.haltsToFire()),
                defaults.gun(),
                properties.getBoolean(prefix + ".holdsGround", defaults.holdsGround()),
                properties.getFloat(prefix + ".arcDegrees", defaults.arcDegrees()),
                properties.getFloat(prefix + ".turnRate", defaults.turnRate()),
                properties.getFloat(prefix + ".closeRange", defaults.closeRange()),
                properties.getFloat(prefix + ".closeFactor", defaults.closeFactor()),
                properties.getInt(prefix + ".settleShots", defaults.settleShots()),
                properties.getFloat(prefix + ".settledSpreadFactor", defaults.settledSpreadFactor()),
                defaults.chargedGun());
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

    /** What an explosion does, under {@code sim.blast}. The radius belongs to whatever makes one. */
    private static SimConfig.BlastConfig blast(GameProperties properties, SimConfig.BlastConfig defaults) {
        return new SimConfig.BlastConfig(
                properties.getFloat("sim.blast.damage", defaults.damage()),
                properties.getFloat("sim.blast.durationSeconds", defaults.durationSeconds()),
                properties.getFloat("sim.blast.shakeStrength", defaults.shakeStrength()),
                properties.getFloat("sim.blast.shakeRadiusFactor", defaults.shakeRadiusFactor()));
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
                            fallback.projectileLook(),
                            properties.getFloat(prefix + ".blastRadius", fallback.blastRadius()),
                            heat(properties, prefix, fallback.heat()),
                            properties.getFloat(prefix + ".muzzleOffset", fallback.muzzleOffset())));
        }
        return builder.build();
    }

    /** How hot one gun gets, under {@code sim.weapon.<kind>.heat}. All zero means a gun that stays cold. */
    private static HeatDef heat(GameProperties properties, String prefix, HeatDef defaults) {
        return new HeatDef(
                properties.getFloat(prefix + ".heat.perShot", defaults.perShot()),
                properties.getFloat(prefix + ".heat.coolPerSecond", defaults.coolPerSecond()),
                properties.getFloat(prefix + ".heat.resumeBelow", defaults.resumeBelow()));
    }

    private static SimConfig.CollisionConfig collision(GameProperties properties, SimConfig.CollisionConfig defaults) {
        return new SimConfig.CollisionConfig(properties.getInt("sim.collision.passes", defaults.passes()));
    }

    /** The gun on a mount, under {@code sim.turret}. Which gun is on it is structure, not tuning. */
    private static SimConfig.TurretConfig turret(GameProperties properties, SimConfig.TurretConfig defaults) {
        return new SimConfig.TurretConfig(
                properties.getFloat("sim.turret.radius", defaults.radius()),
                properties.getFloat("sim.turret.health", defaults.health()),
                properties.getFloat("sim.turret.blastRadius", defaults.blastRadius()),
                properties.getFloat("sim.turret.turnRate", defaults.turnRate()),
                properties.getFloat("sim.turret.firingTurnFactor", defaults.firingTurnFactor()),
                properties.getFloat("sim.turret.range", defaults.range()),
                properties.getFloat("sim.turret.crewRadius", defaults.crewRadius()),
                properties.getFloat("sim.turret.quietSeconds", defaults.quietSeconds()),
                defaults.weapon());
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

    /** What the commander throws, under {@code sim.grenade}. */
    private static SimConfig.GrenadeConfig grenade(GameProperties properties, SimConfig.GrenadeConfig defaults) {
        return new SimConfig.GrenadeConfig(
                properties.getFloat("sim.grenade.cooldownSeconds", defaults.cooldownSeconds()),
                properties.getFloat("sim.grenade.speed", defaults.speed()),
                properties.getFloat("sim.grenade.range", defaults.range()),
                properties.getFloat("sim.grenade.radius", defaults.radius()),
                properties.getFloat("sim.grenade.blastRadius", defaults.blastRadius()));
    }

    /** How long a man who is shot off his feet lasts, and what it takes to pick him up. */
    private static SimConfig.CasualtyConfig casualty(GameProperties properties, SimConfig.CasualtyConfig defaults) {
        return new SimConfig.CasualtyConfig(
                properties.getFloat("sim.casualty.bleedOutSeconds", defaults.bleedOutSeconds()),
                properties.getFloat("sim.casualty.stabiliseSeconds", defaults.stabiliseSeconds()),
                properties.getFloat("sim.casualty.reviveHealthFraction", defaults.reviveHealthFraction()),
                properties.getFloat("sim.casualty.reviveInvulnerableSeconds", defaults.reviveInvulnerableSeconds()),
                properties.getFloat("sim.casualty.draggedBleedFactor", defaults.draggedBleedFactor()),
                properties.getFloat("sim.casualty.hauledStabiliseFactor", defaults.hauledStabiliseFactor()));
    }

    /** How close the commander has to be to do anything with his hands, under {@code sim.action}. */
    private static SimConfig.ActionConfig action(GameProperties properties, SimConfig.ActionConfig defaults) {
        return new SimConfig.ActionConfig(
                properties.getFloat("sim.action.reach", defaults.reach()),
                properties.getFloat("sim.action.dragSpeedFactor", defaults.dragSpeedFactor()),
                properties.getFloat("sim.action.dragLateralFactor", defaults.dragLateralFactor()),
                properties.getFloat("sim.action.dragGap", defaults.dragGap()));
    }

    /** The other pickup with numbers of its own, because it is the one that goes off. */
    private static SimConfig.BarrelConfig barrel(GameProperties properties, SimConfig.BarrelConfig defaults) {
        return new SimConfig.BarrelConfig(
                properties.getFloat("sim.barrel.health", defaults.health()),
                properties.getFloat("sim.barrel.blastRadius", defaults.blastRadius()),
                properties.getFloat("sim.barrel.bittenBlastRadius", defaults.bittenBlastRadius()));
    }

    /** How hard a strain refuses to walk in a straight line, under {@code <prefix>.weave}. */
    private static WeaveDef weave(GameProperties properties, String prefix, WeaveDef defaults) {
        return new WeaveDef(
                properties.getBoolean(prefix + ".enabled", defaults.enabled()),
                properties.getFloat(prefix + ".amplitude", defaults.amplitude()),
                properties.getFloat(prefix + ".wavelength", defaults.wavelength()));
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
