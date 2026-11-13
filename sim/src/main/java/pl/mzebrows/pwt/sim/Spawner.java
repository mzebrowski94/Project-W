package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Approach;
import pl.mzebrows.pwt.sim.component.Armour;
import pl.mzebrows.pwt.sim.component.Armoury;
import pl.mzebrows.pwt.sim.component.Avoidance;
import pl.mzebrows.pwt.sim.component.Barrage;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Dodge;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Drafts;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Focus;
import pl.mzebrows.pwt.sim.component.Geyser;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Hop;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Lair;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Pop;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.Presence;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Shock;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.SlicksOnDeath;
import pl.mzebrows.pwt.sim.component.Sling;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Wander;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.component.Weave;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WeaponType;
import pl.mzebrows.pwt.sim.def.Whole;

/**
 * The one place that builds entity state, so no system has to remember which components a commander
 * or a projectile is made of - or reach for the engine to create one.
 *
 * <p>Systems that spawn take one of these rather than {@link Simulation}, so a system can be tested
 * without a whole world. Marking dead lives here too: it creates a pooled component.
 *
 * <p>Every spawn seeds {@code PreviousPosition} at the spawn point, or the first drawn frame streaks
 * in from the origin.
 */
public final class Spawner {

    private final PooledEngine engine;
    private final SimConfig config;

    private final ImmutableArray<Entity> levels;

    Spawner(PooledEngine engine, SimConfig config) {
        this.engine = engine;
        this.config = config;
        this.levels = engine.getEntitiesFor(Family.all(Level.class).get());
    }

    /**
     * Puts a finished entity into the world with its {@link Handle}. Every spawn ends here, so every
     * entity has one and handles rise in the order entities are added - which is the order every
     * family keeps (ADR 0013), and what makes {@link Handles#resolve} a binary search.
     */
    private void admit(Entity entity) {
        Level level = entity.getComponent(Level.class);
        if (level == null) {
            level = levels.first().getComponent(Level.class);
        }
        entity.add(engine.createComponent(Handle.class)).getComponent(Handle.class).id = level.nextHandle++;
        engine.addEntity(entity);
    }

    /** A duration from the config in the whole ticks every timer counts ({@link Ticks}). */
    private int ticks(float seconds) {
        return Ticks.of(seconds, config.tickSeconds());
    }

    /** How long the work takes on a man who was hauled: the stabilise and its factor, as one duration. */
    private int hauledStabiliseTicks() {
        return ticks(config.casualty().stabiliseSeconds() * config.casualty().hauledStabiliseFactor());
    }

    /** Spawns the player-controlled unit, armed, at full health, and ready to fire. */
    public Entity spawnCommander(float x, float y) {
        return spawnCommander(x, y, 0);
    }

    /** The same, seated at one player's slot. */
    public Entity spawnCommander(float x, float y, int slot) {
        SimConfig.CommanderConfig commander = config.commander();
        SimConfig.MeleeConfig melee = config.melee();
        SimConfig.GrenadeConfig grenade = config.grenade();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Commander.class).set(slot));
        // His life's count, from nothing: a respawn is a new man with a new tally (G0044).
        entity.add(engine.createComponent(Tally.class));
        entity.add(engine.createComponent(Focus.class));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(commander.radius()));
        entity.add(engine.createComponent(Speed.class).set(commander.speed()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Intent.class));
        // No health: a man is hurt in wounds (G0053), and anything may raise a shield in front of them.
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Shield.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(ticks(commander.deathThroesSeconds())));
        entity.add(engine.createComponent(RespawnsOnDeath.class)
                .set(commander.respawnDelaySeconds(), commander.respawnShieldSeconds()));
        entity.add(engine.createComponent(Weapon.class)
                .set(commander.weapon(), config.weapon(commander.weapon()), config.tickSeconds()));
        entity.add(engine.createComponent(Melee.class)
                .set(
                        ticks(melee.cooldownSeconds()),
                        ticks(melee.windupSeconds()),
                        melee.activeSeconds(),
                        melee.reach(),
                        melee.radius(),
                        melee.damage(),
                        melee.arcDegrees(),
                        ticks(melee.lockSeconds()),
                        melee.locksCommittedOnly(),
                        melee.clinchCarrySpeed(),
                        melee.clinchCarryDecay(),
                        melee.knockbackSpeed(),
                        melee.knockbackDecay()));
        entity.add(engine.createComponent(Thrower.class)
                .set(
                        ticks(grenade.cooldownSeconds()),
                        grenade.speed(),
                        grenade.range(),
                        grenade.radius(),
                        grenade.blastRadius(),
                        grenade.most()));
        entity.add(engine.createComponent(Taking.class));
        // He can be shot off his feet like his men (G0023); his bleed-out is his own, read only here.
        entity.add(engine.createComponent(Vitality.class)
                .set(
                        ticks(commander.bleedOutSeconds()),
                        ticks(config.casualty().stabiliseSeconds()),
                        hauledStabiliseTicks(),
                        commander.maxWounds(),
                        ticks(config.wound().graceSeconds()),
                        config.wound().speedFactor(),
                        config.wound().cooldownFactor()));
        entity.add(engine.createComponent(Action.class).set(config.action().reach()));
        entity.add(engine.createComponent(Drag.class)
                .set(
                        config.action().dragSpeedFactor(),
                        config.action().dragLateralFactor(),
                        config.action().dragGap()));
        entity.add(engine.createComponent(Crew.class));
        // Carried always, like his men: a leap that grips takes him as readily as one of them (G0045).
        entity.add(engine.createComponent(Gripped.class));
        // Carried always: what he holds changes, and a gun that does not run hot leaves it at zero.
        entity.add(engine.createComponent(Heat.class));
        entity.add(engine.createComponent(Slowed.class));
        // What a body is made of does not depend on its side.
        entity.add(engine.createComponent(Bloat.class));
        entity.add(engine.createComponent(Shock.class));
        // Inert until a gun that shakes the ground is fired from his hands; read by the view, never the simulation.
        entity.add(engine.createComponent(Shake.class));
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .harming(config.blast().harm()));
        entity.add(engine.createComponent(Appearance.class).set(Look.COMMANDER, slot));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns one trooper: a member of the squad, on the commander's side.
     *
     */
    public Entity spawnTrooper(float x, float y) {
        SimConfig.TrooperConfig trooper = config.trooper();
        SimConfig.MeleeConfig melee = config.melee();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Trooper.class)
                .sees(
                        trooper.perception().detectionRange(),
                        trooper.perception().loseRange(),
                        trooper.perception().arcCosine(),
                        trooper.perception().requiresClearLine(),
                        trooper.perception().allRoundRange())
                .look(1f, 0f));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(trooper.radius()));
        entity.add(engine.createComponent(Speed.class).set(trooper.speed()));
        entity.add(engine.createComponent(Steering.class));
        // Carried always by everything that moves itself: adding it on the first hit would mutate a
        // family mid-iteration.
        entity.add(engine.createComponent(Slowed.class));
        // Carried always, as his commander's: a gun he picks up runs hot in his hands as in anyone's.
        entity.add(engine.createComponent(Heat.class));
        // Carried by everything that can be killed; inert until a beam fills it (zero means off, G0018).
        entity.add(engine.createComponent(Bloat.class));
        entity.add(engine.createComponent(Shock.class));
        // Inert until a gun that shakes the ground is fired from his hands; read by the view, never the simulation.
        entity.add(engine.createComponent(Shake.class));
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .harming(config.blast().harm()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(ticks(trooper.deathThroesSeconds())));
        entity.add(engine.createComponent(Vitality.class)
                .set(
                        ticks(config.casualty().bleedOutSeconds()),
                        ticks(config.casualty().stabiliseSeconds()),
                        hauledStabiliseTicks(),
                        trooper.maxWounds(),
                        ticks(config.wound().graceSeconds()),
                        config.wound().speedFactor(),
                        config.wound().cooldownFactor()));
        entity.add(engine.createComponent(Shield.class));
        entity.add(engine.createComponent(Dragged.class));
        // The pack's spread (G0033), seeded from where he was put down so nothing is drawn.
        entity.add(engine.createComponent(Approach.class)
                .set(
                        trooper.approach().enabled(),
                        trooper.approach().offset(),
                        trooper.approach().closeRange())
                .bearingFrom(x, y));
        entity.add(engine.createComponent(Avoidance.class)
                .set(
                        trooper.avoidance().enabled(),
                        trooper.avoidance().scanRange(),
                        trooper.avoidance().lookaheadSeconds(),
                        trooper.avoidance().weight()));
        entity.add(engine.createComponent(Weapon.class)
                .set(trooper.weapon(), config.weapon(trooper.weapon()), config.tickSeconds()));
        entity.add(engine.createComponent(Melee.class)
                .set(
                        ticks(melee.cooldownSeconds()),
                        ticks(melee.windupSeconds()),
                        melee.activeSeconds(),
                        melee.reach(),
                        melee.radius(),
                        melee.damage(),
                        melee.arcDegrees(),
                        ticks(melee.lockSeconds()),
                        melee.locksCommittedOnly(),
                        melee.clinchCarrySpeed(),
                        melee.clinchCarryDecay(),
                        melee.knockbackSpeed(),
                        melee.knockbackDecay()));
        // The commander's throw with a trooper's own how-often, carried from spawn and off until an
        // upgrade turns the chance up (G0051): a man who arrives after Explosives throws like the rest.
        entity.add(engine.createComponent(Thrower.class)
                .set(
                        ticks(trooper.grenade().cooldownSeconds()),
                        config.grenade().speed(),
                        config.grenade().range(),
                        config.grenade().radius(),
                        config.grenade().blastRadius(),
                        0));
        entity.add(engine.createComponent(Taking.class));
        // Hands, as the commander's, so work on a casualty is published the same way.
        entity.add(engine.createComponent(Action.class).set(config.action().reach()));
        // A trooper has hands for a gun on a mount too, though nothing decides to use one yet.
        entity.add(engine.createComponent(Crew.class));
        entity.add(engine.createComponent(Gripped.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.TROOPER));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a chasing unit of the given strain, built entirely from that strain's definition.
     *
     * <p>A strain is its numbers: adding one is a definition and no code (G0008).
     */
    public Entity spawnUnit(UnitKind kind, float x, float y) {
        return spawnUnit(kind, x, y, 0);
    }

    /**
     * The same, looking the given way rather than east, as everything else starts: what rises among the players
     * faces them (G0048).
     */
    public Entity spawnUnit(UnitKind kind, float x, float y, float faceX, float faceY) {
        Entity entity = spawnUnit(kind, x, y, 0);
        entity.getComponent(Facing.class).face(faceX, faceY);
        return entity;
    }

    /**
     * The same, slung (G0074): off the ground along a direction fixed now, at this speed, for this long - a leap that
     * nothing can hurt and that passes through every body but scenery - and harmless for {@code landingTicks} once it
     * is down. Stamped with the wave of whatever slung it, so a wave is not cleared while its riders are still coming.
     */
    public Entity spawnSlung(
            UnitKind kind,
            float x,
            float y,
            int wave,
            float directionX,
            float directionY,
            float speed,
            int airborneTicks,
            int landingTicks) {
        Entity entity = spawnUnit(kind, x, y, wave);
        entity.getComponent(Facing.class).face(directionX, directionY);
        entity.getComponent(Leap.class).sling(directionX, directionY, speed, airborneTicks, landingTicks, x, y);
        // In the air it is carried by its flight and held off scenery like any rider, rooted on the ground or not
        // (G0083).
        entity.getComponent(Collider.class).throughBodies = true;
        entity.getComponent(Collider.class).movable = true;
        entity.getComponent(Velocity.class).set(directionX * speed, directionY * speed);
        return entity;
    }

    /**
     * The same, for one sent by a wave: {@code wave} counts from one, and zero is a unit nothing sent.
     * The stamp is what says a wave is cleared once all it sent is down.
     */
    public Entity spawnUnit(UnitKind kind, float x, float y, int wave) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Enemy.class));
        Strain strain = engine.createComponent(Strain.class);
        strain.wave = wave;
        entity.add(strain);
        entity.add(engine.createComponent(Faction.class).set(Side.ALIEN));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class));
        entity.add(engine.createComponent(Speed.class));
        entity.add(engine.createComponent(Steering.class));
        entity.add(engine.createComponent(Slowed.class));
        // Carried by everything that can be killed; inert until a beam fills it (zero means off, G0018).
        entity.add(engine.createComponent(Bloat.class));
        entity.add(engine.createComponent(Shock.class));
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .harming(config.blast().harm()));
        // Its own blood, which its strain names the circles of and the swarm's toxic blood adds to (G0076).
        entity.add(engine.createComponent(SlicksOnDeath.class)
                .set(config.blood().shape())
                .bodily(0));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class));
        entity.add(engine.createComponent(ContactDamage.class));
        entity.add(engine.createComponent(Perception.class));
        entity.add(engine.createComponent(Wander.class).anchor(x, y));
        entity.add(engine.createComponent(Avoidance.class));
        entity.add(engine.createComponent(Leap.class));
        entity.add(engine.createComponent(Ranged.class));
        // Seeded from where it was put down, so a pack fans out and weaves out of step with nothing
        // drawn. Set here, not in applyStrain: evolving must not rewrite an individual's line.
        entity.add(engine.createComponent(Approach.class).bearingFrom(x, y));
        entity.add(engine.createComponent(Weave.class).phaseFrom(x, y));
        // Its gait too, for the same reason.
        entity.add(engine.createComponent(Hop.class).phaseFrom(x, y));
        entity.add(engine.createComponent(Dodge.class));
        entity.add(engine.createComponent(Grip.class));
        if (config.unit(kind).fuse().enabled()) {
            // Its fuse (G0083): a mine's clock, which nothing near it can start. Only on a strain that has one - a
            // mine is what carries one - and no strain evolves into or out of one.
            entity.add(engine.createComponent(Mine.class));
        }
        entity.add(engine.createComponent(Armour.class));
        entity.add(engine.createComponent(Barrage.class));
        // Empty on every strain but the one that carries riders (G0074).
        entity.add(engine.createComponent(Sling.class));
        // Inert until a barrage that roars winds up; read by the view, never the simulation.
        entity.add(engine.createComponent(Shake.class));
        entity.add(engine.createComponent(Weapon.class));
        entity.add(engine.createComponent(Taking.class));
        entity.add(engine.createComponent(Feed.class));
        entity.add(engine.createComponent(Appearance.class));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        applyStrain(entity, kind);
        admit(entity);
        return entity;
    }

    /**
     * Writes a strain's definition into components the entity already has.
     *
     * <p><strong>The single writer</strong>, for spawning and evolving alike, so an evolved unit is
     * indistinguishable from one spawned in that strain. It changes field values, never the component
     * set, so it is safe mid-tick.
     *
     * <p>It does not rewrite {@code Wander.anchor}, {@code Weave.travelled} or {@code Approach.bearing}:
     * facts about the individual. Health is set to the new strain's maximum - it arrives whole.
     */
    public void applyStrain(Entity entity, UnitKind kind) {
        UnitDef def = config.unit(kind);
        entity.getComponent(Strain.class).set(kind);
        entity.getComponent(Body.class).set(def.radius());
        entity.getComponent(Speed.class).set(def.speed());
        entity.getComponent(SlicksOnDeath.class).bodily(def.deathSlicks());
        // The boss takes none: a stun would not pause its barrage (G0048, G0059).
        entity.getComponent(Shock.class).immuneIf(!def.shockable());
        entity.getComponent(Health.class).set(config.healthOf(kind));
        entity.getComponent(Dying.class).set(ticks(def.deathThroesSeconds()));
        entity.getComponent(ContactDamage.class)
                .set(def.contactDamage(), ticks(def.attackCooldownSeconds()), ticks(def.victimInvulnerableSeconds()))
                // Claws with nothing in them are no claws: zero is off (G0057).
                .enabledIf(def.contactDamage() > 0)
                .reachingWithin(def.attackArcCosine())
                .setLock(ticks(def.lockSeconds()), false, 0f, 0f, 0f, 0f);
        entity.getComponent(Perception.class)
                .set(
                        def.perception().alwaysAggressive(),
                        def.perception().losesInterest(),
                        def.perception().detectionRange(),
                        def.perception().loseRange(),
                        def.perception().secondaryRange(),
                        def.perception().arcCosine(),
                        def.perception().requiresClearLine(),
                        def.perception().allRoundRange());
        entity.getComponent(Wander.class)
                .set(
                        def.wander().enabled(),
                        def.wander().radius(),
                        def.wander().speedFactor(),
                        def.wander().arriveRadius(),
                        def.wander().pauseMinSeconds(),
                        def.wander().pauseMaxSeconds(),
                        ticks(def.wander().waypointTimeoutSeconds()),
                        def.wander().driftPerStop());
        entity.getComponent(Avoidance.class)
                .set(
                        def.avoidance().enabled(),
                        def.avoidance().scanRange(),
                        def.avoidance().lookaheadSeconds(),
                        def.avoidance().weight());
        entity.getComponent(Leap.class)
                .set(
                        def.leap().enabled(),
                        def.leap().triggerRange(),
                        def.leap().speed(),
                        ticks(def.leap().airborneSeconds()),
                        def.leap().sequenceLength(),
                        ticks(def.leap().betweenSeconds()),
                        ticks(def.leap().cooldownSeconds()),
                        def.leap().hesitationMinSeconds(),
                        def.leap().hesitationMaxSeconds(),
                        Whole.fraction(def.leap().deflectChance()));
        entity.getComponent(Hop.class)
                .set(
                        def.hop().enabled(),
                        def.hop().hopSeconds(),
                        def.hop().restSeconds(),
                        def.hop().burst());
        entity.getComponent(Approach.class)
                .set(
                        def.approach().enabled(),
                        def.approach().offset(),
                        def.approach().closeRange());
        entity.getComponent(Weave.class)
                .set(def.weave().enabled(), def.weave().amplitude(), def.weave().wavelength());
        entity.getComponent(Dodge.class)
                .set(
                        def.dodge().enabled(),
                        def.dodge().reactionRange(),
                        def.dodge().speed(),
                        ticks(def.dodge().durationSeconds()),
                        ticks(def.dodge().commitSeconds()),
                        def.dodge().margin());
        entity.getComponent(Grip.class)
                .set(
                        def.grip().enabled(),
                        def.grip().carrySpeed(),
                        ticks(def.grip().holdSeconds()),
                        def.grip().escapeKnockbackSpeed(),
                        ticks(config.wound().shoveSeconds()),
                        def.grip().escapeHarm(),
                        def.grip().pins(),
                        def.grip().byTouch(),
                        def.grip().spentOnFinish());
        // A fuse is a mine's clock with nothing to set it off, running from the start (G0083).
        Mine fuse = entity.getComponent(Mine.class);
        if (fuse != null) {
            fuse.set(
                    0f,
                    ticks(def.fuse().seconds()),
                    config.mine().flashFromSeconds() / config.tickSeconds(),
                    config.mine().flashToSeconds() / config.tickSeconds());
        }
        entity.getComponent(Armour.class)
                .set(
                        def.armour().enabled(),
                        def.armour().arcCosine(),
                        def.armour().centreCosine(),
                        def.armour().centreSine());
        entity.getComponent(Barrage.class)
                .set(
                        def.barrage(),
                        ticks(def.barrage().windUpSeconds()),
                        ticks(def.barrage().restSeconds()),
                        ticks(def.barrage().roar().seconds()));
        entity.getComponent(Sling.class)
                .set(
                        def.sling(),
                        ticks(def.sling().windUpSeconds()),
                        ticks(def.sling().restSeconds()),
                        ticks(def.sling().landingSeconds()));
        // A strain that never walks is scenery to the solver: what it meets is pushed out of its way (G0048).
        entity.getComponent(Collider.class).movable = !def.rooted();
        entity.getComponent(Ranged.class)
                .set(
                        def.ranged().enabled(),
                        def.ranged().range(),
                        def.ranged().halfSpreadRadians(),
                        def.ranged().haltsToFire(),
                        def.ranged().holdsGround(),
                        def.ranged().arcCosine(),
                        def.ranged().turnRate(),
                        def.ranged().closeRange(),
                        def.ranged().closeFactor(),
                        def.ranged().settleShots(),
                        def.ranged().settledSpreadFactor(),
                        def.ranged().chargedGun());
        if (def.ranged().enabled()) {
            // A shooter starts out looking where everything else does, not already at you. A
            // strain that does not shoot publishes no aim, so its facing stays its heading.
            entity.getComponent(Ranged.class).aim(1f, 0f);
        }
        // The commander's gun table and component: a shooting strain needs no firing code.
        entity.getComponent(Weapon.class)
                .set(def.ranged().gun(), config.weapon(def.ranged().gun()), config.tickSeconds());
        // How readily this strain bites into something it should be wary of.
        entity.getComponent(Taking.class)
                .set(def.chances().of(AiChance.BITE_A_BARREL), def.ranged().chargedGun() != null);
        entity.getComponent(Appearance.class).set(def.look());
    }

    /**
     * Puts a gun in a unit's hands, replacing whatever it was carrying.
     *
     * <p>The single writer for weapons, for spawning and pickups alike. Changes no component set.
     *
     * <p><strong>The new gun arrives cold</strong> (G0025): the bar is the gun's, and the gun that ran
     * hot is the one he let go of. A mount never comes through here - its gun is never swapped - so
     * stepping off a hot turret still cools nothing.
     */
    public void applyWeapon(Entity entity, WeaponKind kind) {
        Weapon weapon = entity.getComponent(Weapon.class);
        if (weapon != null) {
            weapon.set(kind, config.weapon(kind), config.tickSeconds());
        }
        Heat heat = entity.getComponent(Heat.class);
        if (heat != null) {
            heat.reset();
        }
    }

    /**
     * Spawns something lying on the field for the first thing that touches it.
     *
     * <p>Non-solid and immovable: the solver reports the contact and nothing more. Neutral; what each
     * side gets is in the definition.
     */
    public Entity spawnPickup(PickupKind kind, float x, float y) {
        PickupDef def = config.pickup(kind);
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Pickup.class).set(kind, def.shunned()));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(def.radius()));
        entity.add(engine.createComponent(Collider.class).set(false, false));
        entity.add(engine.createComponent(Appearance.class).set(def.look()));
        if (def.armoury().crate()) {
            // Empty and shut: what it holds is dealt the first time it is opened (G0056).
            entity.add(engine.createComponent(Crate.class));
        }
        admit(entity);
        return entity;
    }

    /**
     * Spawns a rabbit: alien food that walks, wanders, and steps aside from a shot it sees coming.
     *
     * <p>A pickup, but solid and movable, so the solver keeps it out of rock. Avoidance ignores
     * pickups. It can be shot, and a shot body cannot be claimed - killing one is denial.
     */
    public Entity spawnRabbit(float x, float y) {
        PickupDef def = config.pickup(PickupKind.RABBIT);
        SimConfig.RabbitConfig rabbit = config.rabbit();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Pickup.class).set(PickupKind.RABBIT, def.shunned()));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(def.radius()));
        entity.add(engine.createComponent(Speed.class).set(rabbit.speed()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class).set(rabbit.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Dying.class).set(ticks(rabbit.deathThroesSeconds())));
        entity.add(engine.createComponent(Steering.class));
        entity.add(engine.createComponent(Slowed.class));
        entity.add(engine.createComponent(Wander.class)
                .set(
                        rabbit.wander().enabled(),
                        rabbit.wander().radius(),
                        rabbit.wander().speedFactor(),
                        rabbit.wander().arriveRadius(),
                        rabbit.wander().pauseMinSeconds(),
                        rabbit.wander().pauseMaxSeconds(),
                        ticks(rabbit.wander().waypointTimeoutSeconds()),
                        rabbit.wander().driftPerStop())
                .anchor(x, y));
        entity.add(engine.createComponent(Dodge.class)
                .set(
                        rabbit.dodge().enabled(),
                        rabbit.dodge().reactionRange(),
                        rabbit.dodge().speed(),
                        ticks(rabbit.dodge().durationSeconds()),
                        ticks(rabbit.dodge().commitSeconds()),
                        rabbit.dodge().margin()));
        entity.add(engine.createComponent(Appearance.class).set(def.look()));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns an explosive barrel: a pickup nobody wants to pick up.
     *
     * <p>A pickup, not an obstacle, so gunfire and a bite are existing paths meeting at one death. The
     * one <em>shunned</em> pickup (G0022): avoided like rock, and bitten only on
     * {@code AiChance.BITE_A_BARREL} while touching. Solid and immovable; {@code BlastsOnDeath} ends
     * both ways the same.
     */
    public Entity spawnBarrel(float x, float y) {
        PickupDef def = config.pickup(PickupKind.BARREL);
        SimConfig.BarrelConfig barrel = config.barrel();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Pickup.class).set(PickupKind.BARREL, def.shunned()));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(def.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Health.class).set(barrel.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .set(barrel.blastRadius(), barrel.bittenBlastRadius())
                .harming(config.blast().harm()));
        // A velocity only because it can be hauled; nothing drives it.
        entity.add(engine.createComponent(Dragged.class));
        entity.add(engine.createComponent(Appearance.class).set(def.look()));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a mine: something small lying in the open that goes off when the other side comes near.
     *
     * <p><strong>Solid, and deliberately not avoided</strong> - it is meant to be walked into. Solid so
     * a hauled one stays out of rock. On nobody's side, so no alien comes for it (G0026) and anything
     * can damage it. It explodes by dying (G0018), so mines chain.
     */
    public Entity spawnMine(float x, float y) {
        SimConfig.MineConfig mine = config.mine();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Mine.class)
                .set(
                        mine.triggerRadius(),
                        ticks(mine.fuseSeconds()),
                        mine.flashFromSeconds() / config.tickSeconds(),
                        mine.flashToSeconds() / config.tickSeconds()));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(mine.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false).avoided(false));
        entity.add(engine.createComponent(Health.class).set(mine.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .set(mine.blastRadius(), mine.blastRadius())
                .harming(config.blast().harm()));
        // Hauled exactly as a barrel is (G0021).
        entity.add(engine.createComponent(Dragged.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.MINE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a geyser (G0089): a circle of ground this wide that lies dormant, fills and erupts in fire, over and over.
     *
     * <p><strong>No collider and no side</strong>, as a control point: dormant it is ground a man walks across, and
     * nothing steers round it, stops on it or hunts it. Its first dormancy is keyed off it as every later one is, so it
     * is admitted before it is set. Spawn before whatever stands on it - the renderer draws in spawn order.
     */
    public Entity spawnGeyser(float x, float y, float radius) {
        SimConfig.GeyserConfig geyser = config.geyser();
        Entity entity = engine.createEntity();
        Geyser cycle = engine.createComponent(Geyser.class);
        entity.add(cycle);
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Appearance.class).set(Look.GEYSER));
        admit(entity);
        cycle.set(
                geyser.dormantTicks(config.seed(), Handles.of(entity), 0, config.tickSeconds()),
                ticks(geyser.fillingSeconds()),
                ticks(geyser.burnSeconds()),
                geyser.eruption(config.fire(), radius));
        return entity;
    }

    /**
     * Spawns a geyser's eruption: a carrier of its fire, born dead, so the fire arrives the way every fire does - by
     * something dying, through the one reaction (G0018, G0035). Nothing else; the reaper takes it this tick.
     */
    public Entity spawnEruption(float x, float y, SlickDef fire) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(SlicksOnDeath.class).set(fire));
        admit(entity);
        markDead(entity);
        return entity;
    }

    /**
     * Spawns a nest: a hole in the ground that keeps sending more of one strain out of it.
     *
     * <p>Solid, immovable and shootable. <strong>Alien side and {@code Enemy}</strong>: shot by troopers
     * and turrets, and counted by "the map is clear". No death throes. Spawn before whatever stands on
     * it - the renderer draws in spawn order.
     */
    public Entity spawnNest(UnitKind kind, float x, float y) {
        SimConfig.NestConfig nest = config.nest();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Nest.class)
                .set(
                        kind,
                        ticks(nest.periodSeconds()),
                        nest.broodMin(),
                        nest.broodMax(),
                        nest.deathBroodMin(),
                        nest.deathBroodMax(),
                        nest.hatchRadius(),
                        nest.wakeRadius(),
                        nest.rouseRadius()));
        entity.add(engine.createComponent(Enemy.class)
                .standingOut(nest.standsOut())
                .weighed(nest.threat(), nest.kept()));
        entity.add(engine.createComponent(Faction.class).set(Side.ALIEN));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(nest.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Health.class).set(config.nestHealth()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.NEST));
        admit(entity);
        // So the system that runs it can tell it is being hit from the bar alone.
        entity.getComponent(Nest.class).watchedHealth = config.nestHealth();
        return entity;
    }

    /**
     * Spawns a door in a doorway (G0069): shut, its doorway's tiles solid ground until it is opened or broken (ADR
     * 0022). <strong>No body and no collider</strong> - a shut door is those tiles, never a circle in the pairwise
     * path. It stands at the doorway's middle, for a reach to be measured to and a bar to be drawn over; its health
     * is what a leap, a blast or a heavy gun takes off it, and its shake what a leap landing on it publishes.
     */
    public Entity spawnDoor(TileRect doorway, float tileSize) {
        SimConfig.DoorConfig door = config.door();
        float x = (doorway.col() + doorway.width() * 0.5f) * tileSize;
        float y = (doorway.row() + doorway.height() * 0.5f) * tileSize;
        Entity entity = engine.createEntity();
        entity.add(
                engine.createComponent(Door.class).set(doorway, ticks(door.openSeconds()), ticks(door.sinkSeconds())));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Health.class).set(door.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Shake.class));
        entity.add(engine.createComponent(Appearance.class)
                .set(Look.DOOR)
                .squares(doorway.width(), doorway.height(), tileSize));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a control point: ground the squad takes by standing on it.
     *
     * <p><strong>No collider</strong>, so its capture-radius body never sizes the collision grid. The
     * body is what the renderer draws. Spawn before what stands on it.
     */
    public Entity spawnControlPoint(float x, float y) {
        SimConfig.ControlPointConfig point = config.controlPoint();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(ControlPoint.class)
                .set(point.radius(), ticks(point.captureSeconds()), point.aggroRadius()));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(point.radius()));
        entity.add(engine.createComponent(Appearance.class).set(Look.CONTROL_POINT));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a turret: a gun on a mount that a man gets on rather than picks up.
     *
     * <p>Solid, immovable and haulable, like a barrel. It can be shot down and blasts on death, catching
     * whoever is on it. No death throes. Player side.
     */
    public Entity spawnTurret(float x, float y) {
        SimConfig.TurretConfig turret = config.turret();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Turret.class)
                .set(turret.turnRate(), turret.firingTurnFactor(), turret.range(), ticks(turret.quietSeconds())));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(turret.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Health.class).set(turret.health()).resisting(turret.blastResistance()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .set(turret.blastRadius(), turret.blastRadius())
                .harming(config.blast().harm()));
        entity.add(engine.createComponent(Dragged.class));
        entity.add(engine.createComponent(Weapon.class)
                .set(turret.weapon(), config.weapon(turret.weapon()), config.tickSeconds()));
        // The heat belongs to the gun, not to whoever is on it.
        entity.add(engine.createComponent(Heat.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.TURRET));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns the level entity: one entity holding state that belongs to nobody in particular.
     *
     * <p>Spawned with the world and never removed; an entity so level-scope reactions are ordinary
     * systems (G0004).
     */
    public Entity spawnLevel() {
        Entity entity = engine.createEntity();
        Level level = engine.createComponent(Level.class);
        level.reserve = config.reserve().start();
        entity.add(level);
        // What is still to arrive; empty means everything is on the field from the first tick.
        entity.add(engine.createComponent(Waves.class));
        // What is kept off the field until the players are all in one place; empty keeps nothing (G0048).
        entity.add(engine.createComponent(Lair.class));
        // What each side holds, the drafts waiting to be picked, and who is away (ADR 0017, G0049, G0052).
        entity.add(engine.createComponent(Upgrades.class));
        entity.add(engine.createComponent(Drafts.class));
        entity.add(engine.createComponent(Presence.class));
        // Which of the armoury deck's cards crates have been dealt, and the crate-size bag (G0056).
        entity.add(engine.createComponent(Armoury.class));
        admit(entity);
        return entity;
    }

    /**
     * Spawns the post: ground the level says you must hold, and the other side walks at.
     *
     * <p>No collider, health or faction. Spawn before what stands on it.
     */
    public Entity spawnPost(float x, float y) {
        return spawnPost(x, y, 0f);
    }

    /**
     * The same, watching only the ground around it rather than the whole level.
     *
     * <p>Zero is the whole level.
     */
    public Entity spawnPost(float x, float y, float watchRadius) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Post.class).set(config.controlPoint().radius(), watchRadius));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(config.controlPoint().radius()));
        entity.add(engine.createComponent(Appearance.class).set(Look.POST));
        admit(entity);
        return entity;
    }

    /** Spawns a boulder: a static circle that blocks units and projectiles. On nobody's side, and harmless. */
    public Entity spawnObstacle(float x, float y) {
        return spawnObstacle(x, y, config.obstacle().radius(), Look.OBSTACLE);
    }

    /**
     * Spawns a static circle of this radius that blocks units and projectiles, drawn as {@code look} - a boulder, or a
     * tree's trunk (G0072). On nobody's side, and harmless.
     */
    public Entity spawnObstacle(float x, float y, float radius, Look look) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Obstacle.class));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Appearance.class).set(look));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a projectile travelling along the given unit direction, on the firer's side so it cannot
     * hit them or anything else friendly. The direction must already be normalised. Everything about
     * the shot, its look included, comes from the gun's definition, except what it does: {@code damage},
     * which the shooter read for the whole pull. {@code credit} is the slot of the player a kill by it
     * counts for, or {@link Tally#NOBODY}.
     */
    public Entity spawnProjectile(
            float x, float y, float directionX, float directionY, Weapon weapon, int damage, Side side, int credit) {
        return spawnProjectile(x, y, directionX, directionY, weapon, damage, side, credit, weapon.projectileRange);
    }

    /**
     * The same, flying {@code range} rather than the gun's own reach - a stepped pattern's shot, stopping short
     * along the way to its aim (G0073). Zero flies until it hits something.
     */
    public Entity spawnProjectile(
            float x,
            float y,
            float directionX,
            float directionY,
            Weapon weapon,
            int damage,
            Side side,
            int credit,
            float range) {
        return projectile(
                x,
                y,
                directionX,
                directionY,
                weapon.projectileSpeed,
                weapon.projectileRadius,
                range,
                damage,
                weapon.projectileLook,
                weapon.blastRadius,
                weapon.slicks,
                ticks(weapon.pierceIntervalSeconds),
                weapon.slowFactor,
                weapon.slowTicks,
                weapon.bloatPerShot,
                weapon.trailLength,
                weapon.stunTicks,
                weapon.shockPerHit,
                weapon.harm,
                weapon.blastHarm,
                weapon.bounces,
                // A heavy gun's shot is the one a door takes a blow from (G0069).
                config.weapon(weapon.kind).type() == WeaponType.HEAVY,
                side,
                credit);
    }

    private Entity projectile(
            float x,
            float y,
            float directionX,
            float directionY,
            float speed,
            float radius,
            float range,
            int damage,
            Look look,
            float blastRadius,
            SlickDef slicks,
            int pierceIntervalTicks,
            float slowFactor,
            int slowTicks,
            float bloatPerShot,
            float trailLength,
            int stunTicks,
            float shockPerHit,
            Harm harm,
            Harm blastHarm,
            int bounces,
            boolean breaksDoors,
            Side side,
            int credit) {
        Entity entity = engine.createEntity();
        Projectile shot = engine.createComponent(Projectile.class)
                .from(x, y)
                .lasting(range)
                .trailing(trailLength)
                .bouncing(bounces)
                .breakingDoors(breaksDoors);
        if (pierceIntervalTicks > 0) {
            // A shot that goes through things: the contact cooldown makes one crossing one blow.
            shot.piercing();
        }
        entity.add(shot);
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(directionX * speed, directionY * speed));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(false, true).swept(true));
        entity.add(engine.createComponent(ContactDamage.class)
                .set(damage, pierceIntervalTicks, 0)
                // What the gun says it does to a man: gunfire wounds, venom's shot costs nothing (G0053, G0057).
                .harming(harm)
                .slowing(slowFactor, slowTicks)
                .pumping(bloatPerShot)
                .stunning(stunTicks)
                .shocking(shockPerHit)
                .credited(credit));
        if (blastRadius > 0f) {
            // A shot that goes off where it stops, by dying (G0018) - on a hit or at the arena edge.
            entity.add(engine.createComponent(BlastsOnDeath.class)
                    .set(blastRadius, blastRadius)
                    .harming(blastHarm)
                    .credited(credit));
        }
        if (slicks.leavesAnything()) {
            // And one that ruins the ground where it stops; a gun may name both.
            entity.add(engine.createComponent(SlicksOnDeath.class).set(slicks));
        }
        entity.add(engine.createComponent(Appearance.class).set(look));
        admit(entity);
        return entity;
    }

    /**
     * Spawns one circle of ruined ground: an ordinary body carrying an ordinary blow.
     *
     * <p>Non-solid and immovable, like a pickup. <strong>On the side of whatever fired the
     * shot</strong>, so the collision rule keeps it off its own side. Its blow does what its source declares -
     * venom's and fire's wound a man and throw him off it, his grace keeping the next circle of the same spatter
     * off him (G0053); glue's only slows him, and is no blow at all (G0073).
     */
    public Entity spawnSlick(
            float x,
            float y,
            float radius,
            float seconds,
            float growFraction,
            float fadeFraction,
            int damage,
            float victimInvulnerableSeconds,
            Look look,
            float knockbackSpeed,
            Harm harm,
            float slowFactor,
            float slowSeconds,
            float heeded,
            long spatter,
            Side side) {
        Entity entity = engine.createEntity();
        Slick slick = engine.createComponent(Slick.class).set(ticks(seconds), radius, growFraction, fadeFraction);
        entity.add(slick);
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(slick.radiusNow()));
        // Steered around by whoever it could catch - avoidance asks about the pair (G0035). It stops no
        // shot: it lies on the floor.
        entity.add(engine.createComponent(Collider.class)
                .set(false, false)
                .avoided(true)
                .stopsShots(false)
                // Gone round by a man only on a chance, the same for all of one spatter (G0076).
                .heeded(heeded, spatter));
        entity.add(engine.createComponent(ContactDamage.class)
                .set(damage, 0, ticks(victimInvulnerableSeconds))
                // Venom and fire wound a man and throw him off themselves (G0053); glue only slows him (G0073).
                .harming(harm)
                .slowing(slowFactor, ticks(slowSeconds))
                .shoving(knockbackSpeed, ticks(config.wound().shoveSeconds()), config.tickSeconds()));
        entity.add(engine.createComponent(Appearance.class).set(look));
        admit(entity);
        return entity;
    }

    /**
     * Spawns one swing of a melee weapon: a real body in the world that sweeps through an arc and is
     * gone (G0005).
     *
     * <p>Non-solid and immovable. Its attack cooldown is the whole swing, so one swing lands at most one
     * blow. It grants no invulnerability: the opening is meant to be punished.
     */
    public Entity spawnStrike(
            @Nullable Entity owner,
            float originX,
            float originY,
            float startAngle,
            float sweepRadians,
            Melee melee,
            Side side) {
        float x = originX + (float) StrictMath.cos(startAngle) * melee.reach;
        float y = originY + (float) StrictMath.sin(startAngle) * melee.reach;
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Strike.class)
                .set(
                        owner == null ? Handles.NOBODY : Handles.of(owner),
                        originX,
                        originY,
                        melee.reach,
                        startAngle,
                        sweepRadians,
                        ticks(melee.activeSeconds)));
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(melee.radius));
        entity.add(engine.createComponent(Collider.class).set(false, false));
        entity.add(engine.createComponent(ContactDamage.class)
                .set(melee.damage, ticks(melee.activeSeconds), 0)
                .setLock(
                        melee.lockTicks,
                        melee.locksCommittedOnly,
                        melee.clinchCarrySpeed,
                        melee.clinchCarryDecay,
                        melee.knockbackSpeed,
                        melee.knockbackDecay)
                .credited(Players.credit(owner)));
        entity.add(engine.createComponent(Appearance.class).set(Look.STRIKE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a grenade travelling along the given unit direction, thrown by a unit that carries one.
     *
     * <p>Non-solid and movable, so the solver leaves walls to {@code GrenadeSystem}, which bounces it.
     * Harmless in the air: everything it does, it does by dying. {@code credit} is the slot of the
     * player who threw it, or {@link Tally#NOBODY}.
     */
    public Entity spawnGrenade(float x, float y, float directionX, float directionY, Thrower thrower, int credit) {
        return grenade(
                x,
                y,
                directionX,
                directionY,
                thrower.speed,
                thrower.range,
                thrower.radius,
                thrower.blastRadius,
                credit);
    }

    /**
     * The same, from the config rather than from a thrower holding one. For the harness and for tests
     * that want one in the air without a commander behind it.
     */
    public Entity spawnGrenade(float x, float y, float directionX, float directionY) {
        SimConfig.GrenadeConfig grenade = config.grenade();
        return grenade(
                x,
                y,
                directionX,
                directionY,
                grenade.speed(),
                grenade.range(),
                grenade.radius(),
                grenade.blastRadius(),
                Tally.NOBODY);
    }

    private Entity grenade(
            float x,
            float y,
            float directionX,
            float directionY,
            float speed,
            float range,
            float radius,
            float blastRadius,
            int credit) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Thrown.class).set(speed, range));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(directionX * speed, directionY * speed));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(false, true));
        // Nothing bites a grenade out of the air, so both ways of ending it are the same circle.
        entity.add(engine.createComponent(BlastsOnDeath.class)
                .set(blastRadius, blastRadius)
                // A grenade's blast wounds a man where every other puts him down (G0018, G0053).
                .harming(config.grenade().blastHarm())
                .credited(credit));
        entity.add(engine.createComponent(Appearance.class).set(Look.GRENADE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a blast: a circle of ground that is lethal for a moment, and on nobody's side.
     *
     * <p><strong>No collider</strong>, as {@link #spawnControlPoint}: {@code BlastSystem} does the radius
     * test. The body is the drawn circle, and the same number as the lethal one.
     */
    public Entity spawnBlast(float x, float y, float radius) {
        return spawnBlast(
                x, y, radius, true, Look.BLAST, Tally.NOBODY, config.blast().harm());
    }

    /**
     * The same, saying whether it leaves the ground burning and what it is drawn as. Everything that
     * explodes leaves fire and is drawn as an explosion, but one. {@code credit} is the slot what it
     * kills counts for, or {@link Tally#NOBODY}; {@code harm} is what it does to a man (G0053).
     */
    public Entity spawnBlast(float x, float y, float radius, boolean leavesFire, Look look, int credit, Harm harm) {
        SimConfig.BlastConfig blast = config.blast();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Blast.class)
                .set(blast.damage(), harm, ticks(blast.durationSeconds()))
                .credited(credit));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Appearance.class).set(look));
        entity.add(engine.createComponent(Shake.class)
                .set(blast.shakeStrength(), radius * blast.shakeRadiusFactor(), ticks(blast.durationSeconds())));
        SlickDef fire = config.fire().forBlast(radius);
        if (leavesFire && fire.leavesAnything()) {
            // What the explosion leaves burning, through the one reaction (G0018, G0035).
            entity.add(engine.createComponent(SlicksOnDeath.class).set(fire));
        }
        admit(entity);
        return entity;
    }

    /**
     * Spawns a pop (G0078): a pickup just taken by touch, drawn as {@code look} at {@code radius} where it was taken,
     * for {@code sim.pop.seconds}. No collider, no side: feedback, which nothing touches or hunts (G0004).
     */
    public Entity spawnPop(float x, float y, Look look, float radius) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Pop.class).set(ticks(config.pop().seconds())));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Appearance.class).set(look));
        admit(entity);
        return entity;
    }

    /**
     * Spawns the pending-respawn ticket for a unit that just died: a countdown and nothing else.
     */
    public Entity spawnRespawn(float delaySeconds, float shieldSeconds, int slot) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Respawning.class).set(ticks(delaySeconds), ticks(shieldSeconds), slot));
        admit(entity);
        return entity;
    }

    /**
     * Marks an entity for removal at the end of the tick. Nothing removes an entity directly - the
     * gap between being marked and being reaped is where death reactions run (G0004).
     */
    public void markDead(Entity entity) {
        if (entity.getComponent(Dead.class) == null) {
            entity.add(engine.createComponent(Dead.class));
        }
    }
}
