package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.ConstraintSolver;
import pl.mzebrows.pwt.sim.component.Armour;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Runs the constraint solver, then decides what this tick's contacts mean.
 *
 * <p>The solver knows geometry and nothing else; this class knows the game and no geometry.
 *
 * <p><strong>One rule for every contact, about sides rather than kinds</strong>: nothing happens
 * within a side; across sides, whoever carries a {@link ContactDamage} off cooldown lands a hit. A
 * blow with a lock holds <em>both</em> parties still and throws whatever survives it.
 *
 * <p>It never subtracts health and never removes anything: a hit adds to {@link Damage}, a spent shot
 * is marked dead, and later systems decide what those mean.
 */
public final class CollisionSystem extends EntitySystem implements ConstraintSolver.ContactListener {

    private static final ComponentMapper<Projectile> PROJECTILE = ComponentMapper.getFor(Projectile.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<ContactDamage> CONTACT_DAMAGE = ComponentMapper.getFor(ContactDamage.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Strike> STRIKE = ComponentMapper.getFor(Strike.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Thrown> THROWN = ComponentMapper.getFor(Thrown.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Armour> ARMOUR = ComponentMapper.getFor(Armour.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Grip> GRIP = ComponentMapper.getFor(Grip.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Shield> SHIELD = ComponentMapper.getFor(Shield.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);

    /** Below this two bodies are on top of each other and give no direction to be thrown along. */
    private static final float EPSILON = 1e-4f;

    private final ConstraintSolver solver;
    private final Spawner spawner;
    private final Defs<PickupKind, PickupDef> pickups;
    private final Rng rng;
    private final Modifiers modifiers;
    private final Chances chances;

    /**
     * The length of the tick being resolved, for the one decision here that is a chance per second.
     *
     * <p>A field because contacts arrive through the solver's callback. Written at the top of
     * {@link #update(float)}.
     */
    @NotState("scratch: this tick's step, set at the start of update for the contact callbacks")
    private float deltaTime;

    private ImmutableArray<Entity> bodies = new ImmutableArray<>(new Array<>());

    /** Every entity with a handle, for finding who swung a strike. */
    private ImmutableArray<Entity> handled = new ImmutableArray<>(new Array<>());

    public CollisionSystem(SimConfig config, TileMap map, Spawner spawner, Modifiers modifiers, Rng rng) {
        this.solver = new ConstraintSolver(config, map, this);
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.pickups = config.pickups();
        this.chances = config.chances();
        this.rng = rng;
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Collider.class).get());
        handled = engine.getEntitiesFor(Family.all(Handle.class).get());
    }

    @Override
    public void update(float deltaTime) {
        this.deltaTime = deltaTime;
        solver.solve(bodies);
    }

    @Override
    public void onContact(Entity a, Entity b) {
        if (THROWN.has(a) || THROWN.has(b)) {
            bounce(a, b);
            return;
        }
        if (PICKUP.has(a) && !shotAt(a, b)) {
            claim(a, b);
            return;
        }
        if (PICKUP.has(b) && !shotAt(b, a)) {
            claim(b, a);
            return;
        }
        if (friendly(a, b)) {
            return;
        }
        boolean firstIsProjectile = PROJECTILE.has(a);
        boolean secondIsProjectile = PROJECTILE.has(b);
        if (firstIsProjectile && secondIsProjectile) {
            return;
        }
        if (firstIsProjectile) {
            spend(a, b);
        } else if (secondIsProjectile) {
            spend(b, a);
        } else {
            strike(a, b);
            strike(b, a);
        }
    }

    /**
     * Notes which way a thrown thing came off whatever it ran into, and stops there.
     *
     * <p>Recorded, not answered: whether it bounces is {@code GrenadeSystem}'s question. It comes off
     * <em>scenery</em> alone. First in the method, and it returns: a grenade in the air is harmless.
     */
    private static void bounce(Entity a, Entity b) {
        if (THROWN.has(a) && prop(b)) {
            comeOff(a, POSITION.get(b));
        }
        if (THROWN.has(b) && prop(a)) {
            comeOff(b, POSITION.get(a));
        }
    }

    private static void comeOff(Entity thrown, Position surface) {
        Position at = POSITION.get(thrown);
        float normalX = at.x - surface.x;
        float normalY = at.y - surface.y;
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY);
        if (length <= EPSILON) {
            return;
        }
        THROWN.get(thrown).bounceOff(normalX / length, normalY / length);
    }

    /**
     * Scenery: something standing on the field that never moves and is not on its way out.
     *
     * <p><strong>A grenade passes through anything alive</strong> (G0019): a unit is solid and movable,
     * so it drops out. A corpse is excluded too - it comes and goes unannounced.
     */
    private static boolean prop(Entity entity) {
        if (!COLLIDER.has(entity)) {
            return false;
        }
        Collider collider = COLLIDER.get(entity);
        return collider.solid && !collider.movable && !inert(entity);
    }

    /**
     * A shot at a pickup that can be hurt, rather than something reaching out to take it.
     *
     * <p>Contact with a pickup is a claim, except a weapon touching one that carries health: that
     * reaches the damage path. A unit walking into a rabbit is eating it, not attacking it.
     */
    private static boolean shotAt(Entity pickup, Entity other) {
        return (PROJECTILE.has(other) || STRIKE.has(other)) && DAMAGE.has(pickup);
    }

    /**
     * Marks a pickup as taken by whoever touched it, if that side has anything to gain.
     *
     * <p>Nothing is granted here: the claim is recorded and {@code PickupSystem} decides what it means.
     * A pickup already dying or already claimed this tick is not claimable, and a side it offers
     * nothing passes over it - so it goes to exactly one taker.
     *
     * <p><strong>A shunned pickup is taken only if the dice say so</strong> (G0022), per second of
     * touching it ({@code AiChance.BITE_A_BARREL}). Decided here, where a claim is made, so a claim is
     * never taken back.
     */
    private void claim(Entity pickupEntity, Entity taker) {
        Pickup pickup = PICKUP.get(pickupEntity);
        if (pickup.claimed || inert(pickupEntity) || !TAKING.has(taker) || !FACTION.has(taker) || inert(taker)) {
            return;
        }
        PickupDef def = pickups.of(pickup.kind);
        if (!def.offersSomethingTo(FACTION.get(taker).side) || !worthTo(def.grantFor(FACTION.get(taker).side), taker)) {
            return;
        }
        if (pickup.shunned) {
            Taking taking = TAKING.get(taker);
            taking.biteDue = Odds.spend(
                    modifiers,
                    taker,
                    AiChance.BITE_A_BARREL,
                    rng,
                    taking.biteDue,
                    chances,
                    taking.biteChance,
                    deltaTime);
            if (!Odds.spent(taking.biteDue)) {
                return;
            }
        }
        if (TAKING.get(taker).claim(pickup.kind)) {
            pickup.claimed = true;
            // What reached it decides which blast a barrel makes; read where the claim is spent.
            pickup.swallowed = TAKING.get(taker).swallows;
        }
    }

    /**
     * A body in its throes is scenery. Scenery does not go shopping, and it does not fight either.
     *
     * <p>A unit shot dead is not stunned, so without this its claws stay live through its throes.
     */
    private static boolean inert(@Nullable Entity entity) {
        return entity != null && DYING.has(entity) && DYING.get(entity).begun;
    }

    /**
     * Whether the attacker can reach the target at all, given the arc its blow declares.
     *
     * <p><strong>Which way it is going is which way it is facing</strong> - this tick's velocity, not
     * last tick's {@code Facing}. Something standing still reaches all round: it can turn on the spot.
     */
    private static boolean reaches(Entity attacker, Entity target, float arcCosine) {
        if (arcCosine <= -1f || !VELOCITY.has(attacker) || !POSITION.has(attacker) || !POSITION.has(target)) {
            return true;
        }
        Velocity heading = VELOCITY.get(attacker);
        Position at = POSITION.get(attacker);
        Position where = POSITION.get(target);
        return Sight.withinArc(heading.x, heading.y, at.x, at.y, where.x, where.y, arcCosine);
    }

    /** Same side, so nothing happens. An entity with no side is on nobody's, and is friendly to nobody. */
    private static boolean friendly(Entity a, Entity b) {
        if (!FACTION.has(a) || !FACTION.has(b)) {
            return false;
        }
        Side side = FACTION.get(a).side;
        return side != Side.NEUTRAL && side == FACTION.get(b).side;
    }

    /**
     * A projectile is used up on whatever stops it, whether or not that thing can be hurt.
     *
     * <p><strong>A piercing shot is stopped by scenery alone</strong> ({@link #prop}, G0019); rock and the
     * arena edge are {@code ProjectileBoundsSystem}'s. Ruined ground stops nothing - a collider flag.
     */
    private void spend(Entity projectile, Entity target) {
        if (!glancesOff(projectile, target)) {
            strike(projectile, target);
        }
        if (COLLIDER.has(target) && !COLLIDER.get(target).stopsShots) {
            return;
        }
        if (!PROJECTILE.get(projectile).piercing || prop(target)) {
            spawner.markDead(projectile);
        }
    }

    /**
     * Whether what a pickup grants would do anything for <em>this</em> taker (G0006 as amended by G0053).
     * A medkit is worth something only to a man with a wound; everything else is worth taking by anybody
     * its side is offered it. Asked of the grant and the taker, never of a flag on the pickup.
     */
    private static boolean worthTo(Grant grant, Entity taker) {
        return switch (grant) {
            case Grant.Mend _ -> VITALITY.has(taker) && VITALITY.get(taker).wounded();
            case Grant.None _, Grant.Detonate _, Grant.Arm _, Grant.Feed _, Grant.Mutate _, Grant.Reinforce _ -> true;
        };
    }

    /**
     * Whether a shot arrives at an armoured head and glances off it (G0045): coming at the thing from
     * inside the arc round the way it faces, judged by the way the shot is flying rather than where on
     * the body it touched. The shot is still spent; it lands nothing.
     */
    private static boolean glancesOff(Entity projectile, Entity target) {
        if (!ARMOUR.has(target) || !ARMOUR.get(target).enabled || !FACING.has(target) || !VELOCITY.has(projectile)) {
            return false;
        }
        Facing facing = FACING.get(target);
        Velocity flight = VELOCITY.get(projectile);
        float speed = (float) Math.sqrt(flight.x * flight.x + flight.y * flight.y);
        if (speed <= EPSILON || (facing.x == 0f && facing.y == 0f)) {
            return false;
        }
        // Back along the shot, towards where it came from, against the way the head is pointing.
        return -(flight.x * facing.x + flight.y * facing.y) / speed >= ARMOUR.get(target).arcCosine;
    }

    /**
     * Lands a hit if the attacker has one to give and the target can take it. Damage is owed, not
     * applied - {@code DamageSystem} decides what it costs.
     */
    private void strike(Entity attacker, Entity target) {
        if (!CONTACT_DAMAGE.has(attacker) || !DAMAGE.has(target)) {
            return;
        }
        if (inert(attacker) || inert(owner(attacker))) {
            // Dead, and its body is still lying there. A corpse is scenery: solid, and harmless.
            return;
        }
        if (held(attacker) || held(owner(attacker))) {
            // Held still or off its feet: no blow, and no cooldown spent.
            return;
        }
        if (GRIP.has(attacker) && GRIP.get(attacker).enabled && takesHoldInsteadOfBiting(attacker, target)) {
            return;
        }
        ContactDamage attack = CONTACT_DAMAGE.get(attacker);
        if (!attack.ready()) {
            return;
        }
        if (!reaches(attacker, target, attack.arcCosine)) {
            // Its back is turned. The cooldown is not spent either: it never swung.
            return;
        }
        Damage owed = DAMAGE.get(target);
        boolean staggering = attack.victimInvulnerableTicks > 0;
        if (staggering && owed.staggered()) {
            // Already staggered this tick: no second blow, and no cooldown spent.
            return;
        }
        attack.remainingTicks = attack.cooldownTicks;
        if (INVULNERABLE.has(target) && INVULNERABLE.get(target).active()) {
            return;
        }
        owed.add(attack.amount, attack.victimInvulnerableTicks, attack.harm);
        owed.stun(attack.stunTicks);
        // Recorded, not applied, as the shove is.
        owed.slow(attack.slowFactor, attack.slowTicks);
        owed.pump(attack.bloat);
        owed.creditTo(attack.credit);
        recordSource(attacker, owed);
        lock(attacker, target, attack, owed);
        shove(attacker, target, attack, owed);
    }

    /**
     * Throws a man off ground that wounds him, holding nobody (G0053): away from the circle's middle,
     * recorded rather than applied, as every throw is - only a man left on his feet is thrown.
     */
    private static void shove(Entity attacker, Entity target, ContactDamage attack, Damage owed) {
        if (!attack.shoves() || !VELOCITY.has(target) || !POSITION.has(attacker) || !POSITION.has(target)) {
            return;
        }
        if (COLLIDER.has(target) && !COLLIDER.get(target).movable) {
            return;
        }
        Position from = POSITION.get(attacker);
        Position to = POSITION.get(target);
        float awayX = to.x - from.x;
        float awayY = to.y - from.y;
        float distance = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (distance <= EPSILON) {
            return;
        }
        owed.shove(awayX / distance, awayY / distance, attack.knockbackSpeed, attack.shoveTicks, attack.knockbackDecay);
    }

    /**
     * What a creature whose leap grips does instead of biting (G0045), and whether it did.
     *
     * <p><strong>Jaws full, it bites nobody</strong>, the man in them included. <strong>In the air, it
     * takes hold</strong> of a man on his feet whom nothing else has hold of, and never kills on landing:
     * whatever it lands on that it cannot take - a man already taken, a man who is down, a gun - it
     * simply lands on. On the ground it bites like anything else. The leap ends as it takes hold.
     */
    private boolean takesHoldInsteadOfBiting(Entity attacker, Entity target) {
        Grip grip = GRIP.get(attacker);
        if (grip.holding()) {
            return true;
        }
        if (!LEAP.has(attacker) || !LEAP.get(attacker).airborne()) {
            return false;
        }
        if (GRIPPED.has(target)
                && !GRIPPED.get(target).held()
                && !Drivers.down(target)
                && !Drivers.aboard(target)
                && !(INVULNERABLE.has(target) && INVULNERABLE.get(target).active())) {
            if (shielded(target)) {
                // A shield takes a grab as it takes any hit, and breaks: the creature simply lands (G0053).
                SHIELD.get(target).shatter();
                LEAP.get(attacker).finish();
                return true;
            }
            grip.take(Handles.of(target));
            GRIPPED.get(target).by = Handles.of(attacker);
            GRIPPED.get(target).fraction = 1f;
            LEAP.get(attacker).finish();
        }
        return true;
    }

    /**
     * Holds both parties still, and throws the target away from whoever swung.
     *
     * <p>Both resolve to the swing's owner where there is one: he lives with the opening, and a sweep on
     * the far side of its target must not throw it into his face. <strong>The lock is what a parry
     * earns</strong>: spent only on something committed to attacking.
     *
     * <p>Both are recorded, since they depend on survival: a killing blow locks its author and throws
     * nothing; a survived one throws its target and locks nobody.
     */
    private void lock(Entity attacker, Entity target, ContactDamage attack, Damage owed) {
        if (!attack.locks()) {
            return;
        }
        Entity owner = owner(attacker);
        Entity source = owner == null ? attacker : owner;
        if (!shielded(target)) {
            // A shield takes the hit and whatever holds him with it (G0053); DamageSystem breaks it.
            hold(target, attack);
        }
        if (!attack.locksCommittedOnly || committed(target)) {
            owed.lockOnDeath(Handles.of(source), attack.lockTicks, attack.clinchCarrySpeed, attack.clinchCarryDecay);
        }
        throwBack(source, target, attack, owed);
    }

    /**
     * Already coming at you: wound up to leap, in the air, or between the leaps of a sequence.
     *
     * <p>The wind-up counts: a sweep on it is the same parry as one on the jump.
     */
    private static boolean committed(Entity target) {
        return LEAP.has(target) && LEAP.get(target).committed();
    }

    private static boolean shielded(Entity entity) {
        return SHIELD.has(entity) && SHIELD.get(entity).up;
    }

    private static void hold(@Nullable Entity entity, ContactDamage attack) {
        if (entity != null && STUNNED.has(entity)) {
            STUNNED.get(entity).apply(attack.lockTicks, attack.knockbackDecay);
        }
    }

    private static void throwBack(Entity source, Entity target, ContactDamage attack, Damage owed) {
        if (attack.knockbackSpeed <= 0f || !VELOCITY.has(target) || !POSITION.has(source) || !POSITION.has(target)) {
            return;
        }
        if (COLLIDER.has(target) && !COLLIDER.get(target).movable) {
            // Something rooted is not thrown: the solver would never bring it back out of rock (G0048).
            return;
        }
        Position from = POSITION.get(source);
        Position to = POSITION.get(target);
        float awayX = to.x - from.x;
        float awayY = to.y - from.y;
        float distance = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        if (distance <= EPSILON) {
            return;
        }
        owed.push(awayX / distance, awayY / distance, attack.knockbackSpeed);
    }

    /**
     * Notes where the blow came from, so that whatever decides what it costs can also decide what it
     * provokes (G0016).
     *
     * <p>A shot's <em>muzzle</em>, a sweep's owner, and otherwise the attacker itself.
     */
    private void recordSource(Entity attacker, Damage owed) {
        if (PROJECTILE.has(attacker)) {
            Projectile shot = PROJECTILE.get(attacker);
            owed.cameFrom(shot.originX, shot.originY);
            return;
        }
        Entity swinger = owner(attacker);
        Entity source = swinger != null ? swinger : attacker;
        if (POSITION.has(source)) {
            Position at = POSITION.get(source);
            owed.cameFrom(at.x, at.y);
        }
    }

    /** The entity that swung this, where the attacker is a swing rather than a unit. */
    private @Nullable Entity owner(@Nullable Entity attacker) {
        if (attacker == null || !STRIKE.has(attacker)) {
            return null;
        }
        return Handles.resolve(handled, STRIKE.get(attacker).owner);
    }

    /**
     * Not swinging at anything: held still by a blow, or off its feet. A man in something's jaws is held
     * and still swings - that is how he gets out (G0045).
     *
     * <p>Asked through {@link Drivers}, the one copy of this question.
     */
    private static boolean held(@Nullable Entity entity) {
        return entity != null && Drivers.held(entity) && !Drivers.gripped(entity);
    }
}
