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
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.ConstraintSolver;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;

/**
 * Runs the constraint solver, then decides what this tick's contacts mean.
 *
 * <p>The split matters: the solver knows geometry and nothing else, and this class knows the game and
 * no geometry at all.
 *
 * <p>There is one rule for every contact, and it is about sides rather than about kinds. Nothing
 * happens between two entities on the same side, which is what stops a commander shooting itself and
 * enemies eating each other with no special case for either. Across sides, whoever carries a
 * {@link ContactDamage} that is off cooldown lands a hit - claws, bullets and a swung fist alike,
 * differing only in their numbers.
 *
 * <p>A blow that carries a lock holds <em>both</em> parties still and throws whatever survives it.
 * That one rule covers both halves of the melee exchange: an enemy that reaches the commander is
 * committed to the kill for three seconds, and a sweep that answers a leap costs the commander a
 * shorter opening of his own. The asymmetry is entirely in the numbers.
 *
 * <p>It never subtracts health and never removes anything. A hit adds to the target's {@link Damage},
 * and a spent projectile is marked dead; what those mean is decided later in the tick, by systems
 * that know nothing about collision.
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

    /** Below this two bodies are on top of each other and give no direction to be thrown along. */
    private static final float EPSILON = 1e-4f;

    private final ConstraintSolver solver;
    private final Spawner spawner;
    private final Defs<PickupKind, PickupDef> pickups;

    private ImmutableArray<Entity> bodies = new ImmutableArray<>(new Array<>());

    public CollisionSystem(SimConfig config, TileMap map, Spawner spawner) {
        this.solver = new ConstraintSolver(config, map, this);
        this.spawner = spawner;
        this.pickups = config.pickups();
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
                Family.all(Position.class, Body.class, Collider.class).get());
    }

    @Override
    public void update(float deltaTime) {
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
     * <p>Recorded rather than answered, for the reason everything in this class is: geometry knows two
     * circles overlapped, and whether that is a bounce depends on which way the thing was already
     * travelling - which is {@code GrenadeSystem}'s question. It is the same split as damage owed and
     * a pickup claimed.
     *
     * <p>It comes off <em>scenery</em> and nothing else, so one grenade passes through another, through
     * a bullet, and through every unit on the field. That is the whole test, and it is a question about
     * collider flags rather than about what any of it is.
     *
     * <p>First in the method, and it returns, because a grenade in the air is harmless: nothing should
     * spend an enemy's attack cooldown on it or offer it a mushroom.
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
     * <p><strong>A grenade passes through anything alive.</strong> It came off units when this was
     * first built, and one playtest in a crowd showed why that is wrong: with a dozen bodies between
     * the commander and where he was aiming, where a throw ends up stops being anything a player can
     * predict, and the bank off a wall - the whole reason the throw is worth having - is drowned in
     * noise. Nothing had to be added to fix it: a unit is solid <em>and movable</em>, so it drops out
     * of this test on its own.
     *
     * <p>The corpse clause is the one deliberate exclusion. A dying body is scenery everywhere else in
     * the game - solid, immovable, and it stops bullets - but it appears and vanishes on a one-second
     * clock, and a surface that comes and goes unannounced is exactly the unpredictability the rest of
     * this rule exists to remove.
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
     * <p>Almost every pickup is scenery a weapon passes through, and being taken is the only thing
     * that can happen to one - which is why contact with a pickup is a claim and nothing else. A
     * pickup that carries health is the exception: it can be denied, and a shot or a sweep at one has
     * to reach the damage path rather than being absorbed by the claim that fails.
     *
     * <p>Only a weapon asks. A unit walking into a rabbit is having dinner, not attacking it, however
     * much contact damage it happens to carry - which is exactly why this is a question about the
     * thing that touched it rather than about whether that thing can hurt things.
     */
    private static boolean shotAt(Entity pickup, Entity other) {
        return (PROJECTILE.has(other) || STRIKE.has(other)) && DAMAGE.has(pickup);
    }

    /**
     * Marks a pickup as taken by whoever touched it, if that side has anything to gain.
     *
     * <p>Nothing is granted here. The claim is recorded on both parties and {@code PickupSystem}
     * decides what it means, for the same reason contact never subtracts health: geometry knows two
     * circles overlapped and nothing else. It does not know whether the taker is dying, what a grant
     * does, or where a level-scope one has to land.
     *
     * <p>A pickup already dying is not claimable either. It matters for exactly one of them: shooting
     * something that runs away is only denial if the corpse cannot then be eaten.
     *
     * <p>The two guards are what make a pickup go to exactly one taker and never vanish for nothing.
     * A pickup already claimed this tick is spent, so the second unit to reach it walks on; a side the
     * definition offers nothing simply passes over it and leaves it standing, which is what lets one
     * item mean different things to the commander and to what is chasing him.
     */
    private void claim(Entity pickupEntity, Entity taker) {
        Pickup pickup = PICKUP.get(pickupEntity);
        if (pickup.claimed || inert(pickupEntity) || !TAKING.has(taker) || !FACTION.has(taker) || inert(taker)) {
            return;
        }
        if (!pickups.of(pickup.kind).offersSomethingTo(FACTION.get(taker).side)) {
            return;
        }
        if (TAKING.get(taker).claim(pickup.kind)) {
            pickup.claimed = true;
        }
    }

    /**
     * A body in its throes is scenery. Scenery does not go shopping, and it does not fight either.
     *
     * <p>Both halves of that matter. A unit killed by a <em>blow</em> is clinched, so the stun already
     * stopped it swinging; a unit killed by a <em>shot</em> is not stunned at all, because shooting
     * something commits the shooter to nothing. Its claws stayed live for the whole of its throes, and
     * walking over something you had just shot killed you.
     */
    private static boolean inert(@Nullable Entity entity) {
        return entity != null && DYING.has(entity) && DYING.get(entity).begun;
    }

    /** Same side, so nothing happens. An entity with no side is on nobody's, and is friendly to nobody. */
    private static boolean friendly(Entity a, Entity b) {
        if (!FACTION.has(a) || !FACTION.has(b)) {
            return false;
        }
        Side side = FACTION.get(a).side;
        return side != Side.NEUTRAL && side == FACTION.get(b).side;
    }

    /** A projectile is used up on whatever stops it, whether or not that thing can be hurt. */
    private void spend(Entity projectile, Entity target) {
        strike(projectile, target);
        spawner.markDead(projectile);
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
            // Something already landed on this attacker; it is not swinging at anything until it
            // comes round. Its cooldown is not spent either - it never got the blow off.
            return;
        }
        ContactDamage attack = CONTACT_DAMAGE.get(attacker);
        if (!attack.ready()) {
            return;
        }
        Damage owed = DAMAGE.get(target);
        boolean staggering = attack.victimInvulnerableSeconds > 0f;
        if (staggering && owed.staggered()) {
            // Something already staggered this target this tick; a second blow does not connect, so it
            // does not cost this attacker its cooldown either.
            return;
        }
        attack.remainingSeconds = attack.cooldownSeconds;
        if (INVULNERABLE.has(target) && INVULNERABLE.get(target).active()) {
            return;
        }
        owed.add(attack.amount, attack.victimInvulnerableSeconds, attack.fatal);
        recordSource(attacker, owed);
        lock(attacker, target, attack, owed);
    }

    /**
     * Holds both parties still, and throws the target away from whoever swung.
     *
     * <p>Both halves resolve to the swing's owner where there is one. For the lock that is because a
     * sweep is gone in a tenth of a second and it is the commander who has to live with the opening it
     * costs him. For the throw it is because a sweep can be on the <em>far</em> side of what it hits -
     * it travels an arc - and pushing away from the sweep would then shove the target into the
     * commander's face, which is the exact opposite of what a parry is for.
     *
     * <p><strong>The lock is what a parry earns, not what a kill costs.</strong> A melee weapon spends
     * it only on something that had already committed to attacking; a sweep that kills something
     * merely walking towards him is a swat, and he keeps his feet. Without that, the safest thing on
     * the field would be the most expensive to kill, which is exactly backwards.
     *
     * <p>Both of those are recorded rather than applied, because both depend on whether the target
     * survives and nothing here has touched its health yet - {@code DamageSystem} decides. A blow that
     * kills throws nothing and locks its author: the body stops where it was struck and the two of them
     * are tangled together for a beat. A blow that is survived throws its target and locks nobody: the
     * commander has shoved something away, which is not the same as being caught up with it, and he
     * keeps fighting.
     */
    private static void lock(Entity attacker, Entity target, ContactDamage attack, Damage owed) {
        if (!attack.locks()) {
            return;
        }
        Entity owner = owner(attacker);
        Entity source = owner == null ? attacker : owner;
        hold(target, attack);
        if (!attack.locksCommittedOnly || committed(target)) {
            owed.lockOnDeath(source, attack.lockSeconds, attack.clinchCarrySpeed, attack.clinchCarryDecay);
        }
        throwBack(source, target, attack, owed);
    }

    /**
     * Already coming at you: wound up to leap, in the air, or between the leaps of a sequence.
     *
     * <p>The hesitation counts. It is the moment the unit has committed to the jump and the moment the
     * player is answering, so a sweep that lands on the wind-up is the same parry as one that lands on
     * the jump - and treating only the airborne half as committed would make the tell a trap.
     */
    private static boolean committed(Entity target) {
        return LEAP.has(target) && LEAP.get(target).committed();
    }

    private static void hold(@Nullable Entity entity, ContactDamage attack) {
        if (entity != null && STUNNED.has(entity)) {
            STUNNED.get(entity).apply(attack.lockSeconds, attack.knockbackDecay);
        }
    }

    private static void throwBack(Entity source, Entity target, ContactDamage attack, Damage owed) {
        if (attack.knockbackSpeed <= 0f || !VELOCITY.has(target) || !POSITION.has(source) || !POSITION.has(target)) {
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
     * <p>For a shot that is the <em>muzzle</em> rather than the impact: a projectile lands a stride
     * from its victim, and walking at that is walking on the spot. For a sweep it is whoever swung,
     * because a sweep travels an arc and is often on the far side of what it hits. For anything
     * else - claws, a body - it is the attacker itself.
     */
    private static void recordSource(Entity attacker, Damage owed) {
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
    private static @Nullable Entity owner(@Nullable Entity attacker) {
        if (attacker == null || !STRIKE.has(attacker)) {
            return null;
        }
        return STRIKE.get(attacker).owner;
    }

    private static boolean held(@Nullable Entity entity) {
        return entity != null && STUNNED.has(entity) && STUNNED.get(entity).active();
    }
}
