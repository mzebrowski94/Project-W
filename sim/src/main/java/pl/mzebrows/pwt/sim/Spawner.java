package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Approach;
import pl.mzebrows.pwt.sim.component.Avoidance;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Dodge;
import pl.mzebrows.pwt.sim.component.Drag;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Focus;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Hop;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Mine;
import pl.mzebrows.pwt.sim.component.Nest;
import pl.mzebrows.pwt.sim.component.Obstacle;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Post;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;
import pl.mzebrows.pwt.sim.component.Shake;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.SlicksOnDeath;
import pl.mzebrows.pwt.sim.component.Slowed;
import pl.mzebrows.pwt.sim.component.Speed;
import pl.mzebrows.pwt.sim.component.Steering;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Thrower;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Wander;
import pl.mzebrows.pwt.sim.component.Waves;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.component.Weave;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.SlickDef;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.def.UnitDef;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * The one place that builds entity state, so no system has to remember which components a commander
 * or a projectile is made of - or reach for the engine to create one.
 *
 * <p>Systems that spawn take one of these rather than a reference back to {@link Simulation}, which
 * keeps the dependency pointing one way and lets a system be tested without a whole world. Marking an
 * entity dead lives here for the same reason: it needs a pooled component, and component creation is
 * this class's job.
 *
 * <p>Every spawn seeds {@code PreviousPosition} at the spawn point. Leaving it at the origin would
 * make a new entity streak in from the corner of the arena on its first drawn frame, and that is the
 * kind of bug no test catches.
 */
public final class Spawner {

    /**
     * What a commander is armed with when it spawns and when it comes back. A better gun is something
     * found on the field and lost on death, never something owned (pillar 3).
     */
    private static final WeaponKind STARTING_WEAPON = WeaponKind.SIDEARM;

    private final PooledEngine engine;
    private final SimConfig config;
    private final TileMap map;

    private final ImmutableArray<Entity> levels;

    Spawner(PooledEngine engine, SimConfig config, TileMap map) {
        this.engine = engine;
        this.config = config;
        this.map = map;
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
        entity.add(engine.createComponent(Focus.class));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(commander.radius()));
        entity.add(engine.createComponent(Speed.class).set(commander.speed()));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Intent.class));
        entity.add(engine.createComponent(Health.class).set(commander.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(ticks(commander.deathThroesSeconds())));
        entity.add(engine.createComponent(RespawnsOnDeath.class)
                .set(
                        commander.respawnDelaySeconds(),
                        commander.respawnMargin(),
                        map.worldHeight() * 0.5f,
                        commander.respawnInvulnerableSeconds()));
        entity.add(engine.createComponent(Weapon.class)
                .set(STARTING_WEAPON, config.weapon(STARTING_WEAPON), config.tickSeconds()));
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
                        grenade.blastRadius()));
        entity.add(engine.createComponent(Taking.class));
        // He can be shot off his feet like his men, now that there is somebody who would come and
        // get him (G0023). Claws, a sweep and a leap still kill him outright, as they kill everyone.
        // His bleed-out is his own; everything else about being picked up is the same for him as for
        // his men. The single place that reads it, which is what keeps the key from being one that
        // lies (G0023).
        entity.add(engine.createComponent(Casualty.class)
                .set(
                        commander.bleedOutSeconds(),
                        config.casualty().stabiliseSeconds(),
                        config.casualty().hauledStabiliseFactor()));
        entity.add(engine.createComponent(Action.class).set(config.action().reach()));
        entity.add(engine.createComponent(Drag.class)
                .set(
                        config.action().dragSpeedFactor(),
                        config.action().dragLateralFactor(),
                        config.action().dragGap()));
        entity.add(engine.createComponent(Crew.class));
        // Carried always, because what he is holding changes: a gun that runs hot needs somewhere to
        // put it, and one that does not leaves this at zero for ever.
        entity.add(engine.createComponent(Heat.class));
        entity.add(engine.createComponent(Slowed.class));
        // He carries one too, and nothing on the alien side has a gun that fills it. It is here so
        // that what a body is made of does not depend on whose side it was on - the day something
        // over there carries a beam, the mechanic already works on him.
        entity.add(engine.createComponent(Bloat.class));
        entity.add(engine.createComponent(BlastsOnDeath.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.COMMANDER, slot));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns one trooper: a member of the squad, on the commander's side.
     *
     * <p>It has a body, a gun and a life, and nothing drives it yet. That is the stage boundary
     * rather than an oversight - a trooper that can be seen, shoved and killed is worth putting on
     * the field before anything decides where it walks, because everything after this is judged by
     * watching it move.
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
                        trooper.perception().requiresClearLine())
                .look(1f, 0f));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(trooper.radius()));
        entity.add(engine.createComponent(Speed.class).set(trooper.speed()));
        entity.add(engine.createComponent(Steering.class));
        // Carried always, by everything that moves under its own power, because what is slowing it
        // changes: a beam held on it, and nothing else in the game yet. A unit nothing has touched
        // keeps this at zero for ever, and adding it at the moment of the first hit would mutate a
        // family mid-iteration.
        entity.add(engine.createComponent(Slowed.class));
        // And a body that can be pumped up, and a blast that is worth nothing until something has
        // pumped it. Both carried by everything that can be killed, and both inert until a beam is
        // pointed at it: a reaction with no radius leaves nothing behind, which is the same "zero
        // means not for me" every other kind table uses (G0018).
        entity.add(engine.createComponent(Bloat.class));
        entity.add(engine.createComponent(BlastsOnDeath.class));
        entity.add(engine.createComponent(Collider.class).set(true, true));
        entity.add(engine.createComponent(Health.class).set(trooper.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Invulnerable.class));
        entity.add(engine.createComponent(Stunned.class));
        entity.add(engine.createComponent(Dying.class).set(ticks(trooper.deathThroesSeconds())));
        entity.add(engine.createComponent(Casualty.class)
                .set(
                        config.casualty().bleedOutSeconds(),
                        config.casualty().stabiliseSeconds(),
                        config.casualty().hauledStabiliseFactor()));
        entity.add(engine.createComponent(Dragged.class));
        // The pack's own spread (G0033), so a squad walking to a point or after him crosses the ground
        // abreast. Seeded from where he was put down, like every alien's, so nothing is drawn.
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
        entity.add(engine.createComponent(Taking.class));
        // A trooper has hands too. The same component the commander carries, so the work a man does
        // on a casualty is published and drawn the same way whoever is doing it.
        entity.add(engine.createComponent(Action.class).set(config.action().reach()));
        // A trooper has hands for a gun on a mount too, though nothing decides to use one yet.
        entity.add(engine.createComponent(Crew.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.TROOPER));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a chasing unit of the given strain, built entirely from that strain's definition.
     *
     * <p>There is one method rather than one per strain because a strain <em>is</em> its numbers. A
     * grunt and a brute differ in a {@code UnitDef} and in nothing else, so adding a strain is a
     * definition and no code at all.
     */
    public Entity spawnUnit(UnitKind kind, float x, float y) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Enemy.class));
        entity.add(engine.createComponent(Strain.class));
        entity.add(engine.createComponent(Faction.class).set(Side.ALIEN));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class));
        entity.add(engine.createComponent(Speed.class));
        entity.add(engine.createComponent(Steering.class));
        entity.add(engine.createComponent(Slowed.class));
        // And a body that can be pumped up, and a blast that is worth nothing until something has
        // pumped it. Both carried by everything that can be killed, and both inert until a beam is
        // pointed at it: a reaction with no radius leaves nothing behind, which is the same "zero
        // means not for me" every other kind table uses (G0018).
        entity.add(engine.createComponent(Bloat.class));
        entity.add(engine.createComponent(BlastsOnDeath.class));
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
        // Both seeded from where it was put down, so a pack fans out and weaves out of step without a
        // number being drawn for either - the same reason its patch is anchored here rather than in
        // applyStrain. Which line a creature takes in is a fact about that individual, not about
        // what it has turned into, so evolving must not rewrite it.
        entity.add(engine.createComponent(Approach.class).bearingFrom(x, y));
        entity.add(engine.createComponent(Weave.class).phaseFrom(x, y));
        // And its gait, for the third time and the same reason: two frogs put down apart hop out of
        // step with each other, and the same level hops the same way on every run.
        entity.add(engine.createComponent(Hop.class).phaseFrom(x, y));
        entity.add(engine.createComponent(Dodge.class));
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
     * <p><strong>The single writer.</strong> Spawning calls it and so does evolving, which is what
     * guarantees an evolved unit is indistinguishable from one spawned in that strain - no field can
     * be updated in one path and forgotten in the other, because there is only one path.
     *
     * <p>It changes no component <em>set</em>, only field values, so an entity may be run through it
     * mid-tick without disturbing any family being iterated. That is the whole reason a strain is an
     * enum on a component rather than a marker.
     *
     * <p>Where a unit lives is the one thing it does not rewrite - and which line it takes in is the
     * other. {@code Wander.anchor}, {@code Weave.travelled} and {@code Approach.bearing} are set at
     * the spawn and never again, so a grunt that becomes a brute keeps the patch the grunt was put in
     * and the side it was already closing on: both are facts about that individual rather than about
     * what it has turned into.
     *
     * <p>Health is set to the new strain's maximum rather than carried over as a fraction. A unit
     * that has just become something else arrives whole; a grunt shot down to a sliver and then fed
     * gets up as a full brute, and that reversal is the drama the mechanic is for.
     */
    public void applyStrain(Entity entity, UnitKind kind) {
        UnitDef def = config.unit(kind);
        entity.getComponent(Strain.class).set(kind);
        entity.getComponent(Body.class).set(def.radius());
        entity.getComponent(Speed.class).set(def.speed());
        entity.getComponent(Health.class).set(def.health());
        entity.getComponent(Dying.class).set(ticks(def.deathThroesSeconds()));
        entity.getComponent(ContactDamage.class)
                .set(def.contactDamage(), ticks(def.attackCooldownSeconds()), ticks(def.victimInvulnerableSeconds()))
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
                        def.perception().requiresClearLine());
        entity.getComponent(Wander.class)
                .set(
                        def.wander().enabled(),
                        def.wander().radius(),
                        def.wander().speedFactor(),
                        def.wander().arriveRadius(),
                        def.wander().pauseMinSeconds(),
                        def.wander().pauseMaxSeconds(),
                        ticks(def.wander().waypointTimeoutSeconds()));
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
                        def.leap().hesitationMaxSeconds());
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
            // A shooter starts out looking where everything else starts out looking, rather than
            // already at you: a field of view is only a field of view if it starts somewhere. A
            // strain that does not shoot publishes no aim at all, so its facing stays its heading.
            entity.getComponent(Ranged.class).aim(1f, 0f);
        }
        // Through the same table and the same component the commander's own guns come through, so a
        // strain that shoots needs no firing code of its own - only a definition saying which gun.
        entity.getComponent(Weapon.class)
                .set(def.ranged().gun(), config.weapon(def.ranged().gun()), config.tickSeconds());
        // How readily this strain bites into something it should be wary of. Copied onto the
        // component like every other per-strain number, so the place that asks needs no table.
        entity.getComponent(Taking.class)
                .set(def.chances().of(AiChance.BITE_A_BARREL), def.ranged().chargedGun() != null);
        entity.getComponent(Appearance.class).set(def.look());
    }

    /**
     * Puts a gun in a unit's hands, replacing whatever it was carrying.
     *
     * <p>The single writer for weapons, as {@code applyStrain} is for strains: spawning and picking
     * one up both come through here, so a gun found on the field behaves exactly like one spawned
     * with. It changes no component set - only the fields of the {@code Weapon} the unit already has.
     */
    public void applyWeapon(Entity entity, WeaponKind kind) {
        Weapon weapon = entity.getComponent(Weapon.class);
        if (weapon != null) {
            weapon.set(kind, config.weapon(kind), config.tickSeconds());
        }
    }

    /**
     * Spawns something lying on the field for the first thing that touches it.
     *
     * <p>Non-solid, so units walk over it rather than round it, and immovable, so a crowd cannot
     * shove it out of the way. Those two flags are the whole of what makes it a pickup as far as the
     * constraint solver is concerned - it reports the contact and asks nothing further, exactly as it
     * does for a projectile or a melee sweep.
     *
     * <p>Neutral, because a pickup is on nobody's side. What each side gets out of it is in the
     * definition, not in a faction.
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
        admit(entity);
        return entity;
    }

    /**
     * Spawns a rabbit: alien food that walks, wanders, and steps aside from a shot it sees coming.
     *
     * <p>A pickup in every way that matters - it is claimed on contact and its grants come from the
     * same table - but solid and movable rather than the usual non-solid and immovable, because those
     * two flags are what keep a body out of rock and inside the arena and this one moves under its own
     * power. Avoidance is told to ignore pickups outright, so nothing steers around it.
     *
     * <p>It carries {@code Health} and {@code Damage} because it can be shot, and a shot body cannot
     * be claimed - which is what makes killing one denial rather than a waste of a bullet.
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
                        ticks(rabbit.wander().waypointTimeoutSeconds()))
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
     * <p><strong>A pickup rather than an obstacle, and that is the design rather than a
     * convenience.</strong> Being one is what makes "set off by gunfire" and "set off by something
     * hungry" two paths that already existed meeting at one death, rather than two new rules.
     *
     * <p>It is the one pickup that is <em>shunned</em> (G0022): avoidance gives it as wide a berth as
     * a rock and an idle alien no longer walks over to eat it. What gets a creature onto one is the
     * dice - {@code AiChance.BITE_A_BARREL}, rolled while it is actually touching one - so a barrel
     * reads as a hazard the aliens mostly respect and occasionally set off in their own faces.
     *
     * <p>Solid and immovable, unlike the growths: a barrel is a thing standing on the field rather
     * than a dot to walk over. That changes nothing about being taken - the solver reports a contact
     * before it separates anything.
     *
     * <p>It carries {@code Health} so a shot or a sweep reaches the damage path through it, exactly as
     * a rabbit's does, and {@code BlastsOnDeath} so that both ways of ending it end the same way.
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
        entity.add(engine.createComponent(BlastsOnDeath.class).set(barrel.blastRadius(), barrel.bittenBlastRadius()));
        // It can be hauled, which is why it has a velocity at all. Immovable and therefore still
        // scenery until somebody picks it up; nothing drives it, so the velocity stays zero.
        entity.add(engine.createComponent(Dragged.class));
        entity.add(engine.createComponent(Appearance.class).set(def.look()));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a mine: something small lying in the open that goes off when the other side comes near.
     *
     * <p><strong>Solid, and deliberately not avoided.</strong> Everything else solid is given a wide
     * berth, because walking into it is a failure; the whole of what a mine is is something the other
     * side walks into, so it is the one body in the game that says so on its collider. Solid rather
     * than not because the commander can haul one, and the solver only keeps movable <em>solids</em>
     * out of rock - a mine that could be dragged through a wall would be a mine lost inside one.
     *
     * <p>On nobody's side, exactly as a barrel is. That is what keeps it off the list of things an
     * alien comes for (G0026) - a level that laid a dozen of them would otherwise have laid a dozen
     * decoys - and it is what lets anything at all damage it, because damage from a neutral source
     * crosses every faction line.
     *
     * <p>It carries {@code Health} so a shot or a blast reaches it, and {@code BlastsOnDeath} so
     * every one of those ends the same way it does: by dying, through the one reaction (G0018). A
     * mine caught in another mine's blast therefore chains, with no rule anywhere saying it should.
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
        entity.add(engine.createComponent(BlastsOnDeath.class).set(mine.blastRadius(), mine.blastRadius()));
        // Hauled exactly as a barrel is, by the same hands and the same component. Carrying one to
        // where you want it is most of what a mine is worth (G0021).
        entity.add(engine.createComponent(Dragged.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.MINE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a nest: a hole in the ground that keeps sending more of one strain out of it.
     *
     * <p>Solid and immovable, exactly as a barrel and a turret are - it is a thing standing on the
     * field, and it is meant to be walked to and shot. It carries {@code Health} and {@code Damage}
     * so that a shot, a sweep and a blast all reach it through the paths that already exist.
     *
     * <p><strong>On the alien side, and carrying {@code Enemy}.</strong> The side is what makes the
     * player's fire reach it and the aliens' pass through; the marker is what puts it on the list a
     * trooper and a turret shoot at, and on the list "the map is clear" counts. Without the marker a
     * level could read as cleared with something still producing on it, which would make the
     * objective a lie every thirty seconds.
     *
     * <p>No death throes: what a destroyed nest leaves behind is the last brood out of it, not a body
     * to walk round - the same answer a barrel and a turret give.
     *
     * <p>Spawn one before whatever stands on it. The renderer draws in spawn order, and a nest is
     * ground with a hole in it rather than a thing standing on the ground.
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
        entity.add(engine.createComponent(Enemy.class));
        entity.add(engine.createComponent(Faction.class).set(Side.ALIEN));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(nest.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Health.class).set(nest.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.NEST));
        admit(entity);
        // What it has to lose, so the system that runs it can tell it is being hit from the bar alone.
        entity.getComponent(Nest.class).watchedHealth = nest.health();
        return entity;
    }

    /**
     * Spawns a control point: ground the squad takes by standing on it.
     *
     * <p><strong>No collider.</strong> A point is somewhere you walk, not something you walk into, so
     * it never enters the pairwise path at all - which also means its body can be as wide as the
     * capture radius without inflating the collision grid's cell size.
     *
     * <p>It carries a body anyway, because that is what the renderer measures a circle from, and a
     * point drawn at its capture radius is a point whose edge the player can see. Spawn one before
     * the units that will stand on it: the renderer draws in spawn order, so it wants to be the thing
     * underneath.
     */
    public Entity spawnControlPoint(float x, float y) {
        SimConfig.ControlPointConfig point = config.controlPoint();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(ControlPoint.class)
                .set(point.radius(), point.captureSeconds(), point.aggroRadius()));
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
     * <p>Solid and immovable, exactly as a barrel is: it is a thing standing on the field, and it can
     * be hauled to somewhere more useful by the same hands and the same component that haul one. It
     * carries {@code Health} and {@code Damage} so it can be shot down, and {@code BlastsOnDeath} so
     * that losing one is an event rather than a disappearance - and so that whoever was on it when it
     * went is caught by the circle he was standing in the middle of.
     *
     * <p>It has no death throes. A barrel does not either, and for the same reason: what a destroyed
     * explosive thing leaves behind is the blast, not a body to walk round.
     *
     * <p>On the player's side, so his own fire passes through it and the aliens' does not.
     */
    public Entity spawnTurret(float x, float y) {
        SimConfig.TurretConfig turret = config.turret();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Turret.class)
                .set(turret.turnRate(), turret.firingTurnFactor(), turret.range(), turret.quietSeconds()));
        entity.add(engine.createComponent(Faction.class).set(Side.PLAYER));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(0f, 0f));
        entity.add(engine.createComponent(Body.class).set(turret.radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Health.class).set(turret.health()));
        entity.add(engine.createComponent(Damage.class));
        entity.add(engine.createComponent(BlastsOnDeath.class).set(turret.blastRadius(), turret.blastRadius()));
        entity.add(engine.createComponent(Dragged.class));
        entity.add(engine.createComponent(Weapon.class)
                .set(turret.weapon(), config.weapon(turret.weapon()), config.tickSeconds()));
        // The heat belongs to the gun, not to whoever is on it. That is the whole of why stepping off
        // a hot turret and back on again is not a way of putting the bar back.
        entity.add(engine.createComponent(Heat.class));
        entity.add(engine.createComponent(Appearance.class).set(Look.TURRET));
        entity.add(engine.createComponent(Facing.class).face(1f, 0f));
        admit(entity);
        return entity;
    }

    /**
     * Spawns the level entity: one entity holding state that belongs to nobody in particular.
     *
     * <p>Spawned with the world and never removed. It has no position, no body and nothing to draw,
     * which is exactly why it is an entity - it means a level-scope reaction is an ordinary system
     * reading an ordinary component, and no part of the simulation needs a message queue to reach it
     * (G0004).
     */
    public Entity spawnLevel() {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Level.class));
        // What is still to arrive. Empty on every level built so far, which is what "everything is on
        // the field from the first tick" looks like as data.
        entity.add(engine.createComponent(Waves.class));
        admit(entity);
        return entity;
    }

    /**
     * Spawns the post: ground the level says you must hold, and the other side walks at.
     *
     * <p>No collider, no health and no faction - it is a goal rather than a thing to fight. Spawn one
     * before whatever stands on it: the renderer draws in spawn order, and a post wants to be the
     * thing underneath.
     */
    public Entity spawnPost(float x, float y) {
        return spawnPost(x, y, 0f);
    }

    /**
     * The same, watching only the ground around it rather than the whole level.
     *
     * <p>Zero is the whole level, which is what a level the size of one fight means. A march names a
     * distance instead, because "nothing hostile standing" cannot mean seven screens with nests
     * still working four biomes back.
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

    /** Spawns a static circle that blocks units and projectiles. On nobody's side, and harmless. */
    public Entity spawnObstacle(float x, float y) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Obstacle.class));
        entity.add(engine.createComponent(Faction.class).set(Side.NEUTRAL));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(config.obstacle().radius()));
        entity.add(engine.createComponent(Collider.class).set(true, false));
        entity.add(engine.createComponent(Appearance.class).set(Look.OBSTACLE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a projectile travelling along the given unit direction, on the firer's side so it cannot
     * hit them or anything else friendly. The caller supplies the direction already normalised - it
     * has usually just computed it to place the spawn point.
     *
     * <p>Everything about the shot comes from the gun's definition, its look included, so which gun
     * fired it is visible without anything asking.
     */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, Weapon weapon, Side side) {
        return projectile(
                x,
                y,
                directionX,
                directionY,
                weapon.projectileSpeed,
                weapon.projectileRadius,
                weapon.projectileRange,
                weapon.damage,
                weapon.projectileLook,
                weapon.blastRadius,
                weapon.slicks,
                ticks(weapon.pierceIntervalSeconds),
                weapon.slowFactor,
                weapon.slowTicks,
                weapon.bloatPerShot,
                weapon.trailLength,
                side);
    }

    /**
     * The same, from a gun's definition rather than from a gun somebody is holding. For the harness
     * and for tests that want a shot in the world without a shooter behind it.
     */
    public Entity spawnProjectile(float x, float y, float directionX, float directionY, WeaponDef gun, Side side) {
        return projectile(
                x,
                y,
                directionX,
                directionY,
                gun.projectileSpeed(),
                gun.projectileRadius(),
                gun.projectileRange(),
                gun.damage(),
                gun.projectileLook(),
                gun.blastRadius(),
                gun.slicks(),
                ticks(gun.pierceIntervalSeconds()),
                gun.slowFactor(),
                ticks(gun.slowSeconds()),
                gun.bloatPerShot(),
                gun.trailLength(),
                side);
    }

    private Entity projectile(
            float x,
            float y,
            float directionX,
            float directionY,
            float speed,
            float radius,
            float range,
            float damage,
            Look look,
            float blastRadius,
            SlickDef slicks,
            int pierceIntervalTicks,
            float slowFactor,
            int slowTicks,
            float bloatPerShot,
            float trailLength,
            Side side) {
        Entity entity = engine.createEntity();
        Projectile shot = engine.createComponent(Projectile.class)
                .from(x, y)
                .lasting(range)
                .trailing(trailLength);
        if (pierceIntervalTicks > 0) {
            // A shot that goes through things. The interval is its contact cooldown, which is the
            // field that already means "how long before this may land another blow" - so one slug
            // crossing one body owes one blow rather than one per tick it overlaps.
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
                .wounding()
                .slowing(slowFactor, slowTicks)
                .pumping(bloatPerShot));
        if (blastRadius > 0f) {
            // A shot that goes off where it stops, and it does so by dying, through the one reaction
            // everything that explodes uses (G0018). Hitting something and running out of arena are
            // the same event to it, because both end in the reaper's window.
            entity.add(engine.createComponent(BlastsOnDeath.class).set(blastRadius, blastRadius));
        }
        if (slicks.leavesAnything()) {
            // And a shot that ruins the ground where it stops, through the same reaction. A gun may
            // name both: the blast is the moment, the spatter is what the moment leaves behind.
            entity.add(engine.createComponent(SlicksOnDeath.class).set(slicks));
        }
        entity.add(engine.createComponent(Appearance.class).set(look));
        admit(entity);
        return entity;
    }

    /**
     * Spawns one circle of ruined ground: an ordinary body carrying an ordinary blow.
     *
     * <p>Non-solid, so anything walks into it rather than round it, and immovable, so a crowd cannot
     * shove it off the ground it has ruined - the same two flags a pickup lying on the field
     * declares, and the whole of what it takes to be in the pairwise path without being an obstacle.
     *
     * <p><strong>It is on the side of whatever fired the shot</strong>, which is the one way a slick
     * differs from a blast and is the whole of how it is enforced: the collision rule already refuses
     * to let damage stay inside a side, so venom reaches the player's men and not the frogs that
     * spat it, with no system asking anything.
     *
     * <p>The blow it lands is a wounding one - a man goes down rather than dying, as he does to
     * gunfire and to a blast - and it grants its victim a window afterwards. Without that window the
     * next circle of the same spatter would finish him on the same tick, and a blow that leaves a
     * casualty would be an outright kill everywhere it mattered.
     */
    public Entity spawnSlick(
            float x,
            float y,
            float radius,
            float seconds,
            float growFraction,
            float fadeFraction,
            float damage,
            float victimInvulnerableSeconds,
            Look look,
            Side side) {
        Entity entity = engine.createEntity();
        Slick slick = engine.createComponent(Slick.class).set(ticks(seconds), radius, growFraction, fadeFraction);
        entity.add(slick);
        entity.add(engine.createComponent(Faction.class).set(side));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(slick.radiusNow()));
        // Steered around when it is on nobody's side, and not when it has taken one. Derived here
        // rather than declared beside the numbers, exactly as a pickup's shunned is: ground that
        // catches everybody is ground everybody gives room to, and ground that is somebody's problem
        // is walked through by the things that made it (G0034, G0035).
        // And it stops no shot. It is a circle lying on the floor, not a thing standing on it: a
        // slick that ate bullets meant the frog fencing off the ground fenced off the gun too.
        entity.add(engine.createComponent(Collider.class)
                .set(false, false)
                .avoided(side == Side.NEUTRAL)
                .stopsShots(false));
        entity.add(engine.createComponent(ContactDamage.class)
                .set(damage, 0, ticks(victimInvulnerableSeconds))
                .wounding());
        entity.add(engine.createComponent(Appearance.class).set(look));
        admit(entity);
        return entity;
    }

    /**
     * Spawns one swing of a melee weapon: a real body in the world that sweeps through an arc and is
     * gone (G0005).
     *
     * <p>It is non-solid, so it reports a contact without shoving what it touches, and immovable, so
     * the solver leaves its position to the system driving the sweep. Its attack cooldown is the
     * whole duration of the swing, which is what makes one swing land at most one blow.
     *
     * <p>It grants its victim no invulnerability, deliberately: a target held still by a connecting
     * sweep is meant to be punished during the opening, not protected by it.
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
                        melee.activeSeconds));
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
                        melee.knockbackDecay));
        entity.add(engine.createComponent(Appearance.class).set(Look.STRIKE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a grenade travelling along the given unit direction, thrown by a unit that carries one.
     *
     * <p>Non-solid, so it slips past bodies rather than shoving a fight around, and movable, so it
     * travels. Those two flags also mean the solver never keeps it out of rock or inside the arena -
     * {@code GrenadeSystem} does both, because what a wall <em>means</em> to a grenade is a bounce
     * rather than the end of it.
     *
     * <p>No faction, and no contact damage. A grenade in the air is harmless: everything it does, it
     * does by dying.
     */
    public Entity spawnGrenade(float x, float y, float directionX, float directionY, Thrower thrower) {
        return grenade(x, y, directionX, directionY, thrower.speed, thrower.range, thrower.radius, thrower.blastRadius);
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
                grenade.blastRadius());
    }

    private Entity grenade(
            float x,
            float y,
            float directionX,
            float directionY,
            float speed,
            float range,
            float radius,
            float blastRadius) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Thrown.class).set(speed, range));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(directionX * speed, directionY * speed));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Collider.class).set(false, true));
        // Nothing bites a grenade out of the air, so both ways of ending it are the same circle.
        entity.add(engine.createComponent(BlastsOnDeath.class).set(blastRadius, blastRadius));
        entity.add(engine.createComponent(Appearance.class).set(Look.GRENADE));
        admit(entity);
        return entity;
    }

    /**
     * Spawns a blast: a circle of ground that is lethal for a moment, and on nobody's side.
     *
     * <p><strong>No collider.</strong> A body this wide in the pairwise path would set the collision
     * grid's cell size for the whole level in order to serve a quarter of a second of effect - the
     * same trade {@link #spawnControlPoint} makes, for the same reason. {@code BlastSystem} does the
     * radius test itself.
     *
     * <p>It carries a body anyway, because that is what the renderer measures a circle from - and here
     * the drawn circle and the lethal circle are the same number, so the ring the player sees is the
     * ring that killed him.
     *
     * <p>No faction, because a blast is on nobody's side. It is not a question anything asks it: the
     * damage it owes is written straight onto whatever it caught, and never crosses the contact path.
     */
    public Entity spawnBlast(float x, float y, float radius) {
        return spawnBlast(x, y, radius, true, Look.BLAST);
    }

    /**
     * The same, saying whether it leaves the ground burning and what it is drawn as. Everything that
     * explodes leaves fire and is drawn as an explosion, but one.
     */
    public Entity spawnBlast(float x, float y, float radius, boolean leavesFire, Look look) {
        SimConfig.BlastConfig blast = config.blast();
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Blast.class).set(blast.damage(), ticks(blast.durationSeconds())));
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(PreviousPosition.class).set(x, y));
        entity.add(engine.createComponent(Body.class).set(radius));
        entity.add(engine.createComponent(Appearance.class).set(look));
        entity.add(engine.createComponent(Shake.class)
                .set(blast.shakeStrength(), radius * blast.shakeRadiusFactor(), ticks(blast.durationSeconds())));
        SlickDef fire = config.fire().forBlast(radius);
        if (leavesFire && fire.leavesAnything()) {
            // What the explosion leaves burning, through the one reaction everything that leaves
            // something behind uses (G0018, G0035). A blast is a thing that dies like any other, so
            // nothing that explodes had to learn that fire exists - and a gun that leaves no blast,
            // which is what the flamethrower is, leaves no fire either.
            entity.add(engine.createComponent(SlicksOnDeath.class).set(fire));
        }
        admit(entity);
        return entity;
    }

    /**
     * Spawns the pending-respawn ticket for a unit that just died. It has nothing to draw and nothing
     * to collide with; it exists only to hold a countdown that the dead unit can no longer hold.
     */
    public Entity spawnRespawn(float delaySeconds, float x, float y, float invulnerableSeconds, int slot) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Respawning.class)
                .set(ticks(delaySeconds), x, y, ticks(invulnerableSeconds), slot));
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
