package pl.mzebrows.pwt.sim.system.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Odds;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.ConstraintSolver;
import pl.mzebrows.pwt.sim.collision.Solid;
import pl.mzebrows.pwt.sim.component.Armour;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Grip;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Limpet;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Slither;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.component.Thrown;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.query.Deflect;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Sight;

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
    private static final ComponentMapper<Slither> SLITHER = ComponentMapper.getFor(Slither.class);
    private static final ComponentMapper<Handle> HANDLE = ComponentMapper.getFor(Handle.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Stunned> STUNNED = ComponentMapper.getFor(Stunned.class);
    private static final ComponentMapper<Strike> STRIKE = ComponentMapper.getFor(Strike.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Velocity> VELOCITY = ComponentMapper.getFor(Velocity.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Leap> LEAP = ComponentMapper.getFor(Leap.class);
    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Thrown> THROWN = ComponentMapper.getFor(Thrown.class);
    private static final ComponentMapper<Limpet> LIMPET = ComponentMapper.getFor(Limpet.class);
    private static final ComponentMapper<Collider> COLLIDER = ComponentMapper.getFor(Collider.class);
    private static final ComponentMapper<Armour> ARMOUR = ComponentMapper.getFor(Armour.class);
    private static final ComponentMapper<Facing> FACING = ComponentMapper.getFor(Facing.class);
    private static final ComponentMapper<Grip> GRIP = ComponentMapper.getFor(Grip.class);
    private static final ComponentMapper<Shield> SHIELD = ComponentMapper.getFor(Shield.class);
    private static final ComponentMapper<Gripped> GRIPPED = ComponentMapper.getFor(Gripped.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<PreviousPosition> PREVIOUS = ComponentMapper.getFor(PreviousPosition.class);

    /** Below this two bodies are on top of each other and give no direction to be thrown along. */
    private static final float EPSILON = 1e-4f;

    /**
     * How far clear of a body a shot that came off it is put, so next tick it is not touching what it just
     * came off and lands no second blow on it.
     */
    private static final float CLEAR_OF_THE_BODY = 1e-3f;

    private final ConstraintSolver solver;
    private final Spawner spawner;
    private final Defs<PickupKind, PickupDef> pickups;
    private final Odds odds;

    /** The level's seed, which a trooper's deflect of a leap is keyed from (G0086). */
    private final long levelSeed;

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

    public CollisionSystem(SimConfig config, Solid solid, Spawner spawner, Odds odds) {
        this.solver = new ConstraintSolver(config, solid, this);
        this.spawner = spawner;
        this.pickups = config.pickups();
        this.odds = odds;
        this.levelSeed = config.seed();
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
        if (LIMPET.has(a) || LIMPET.has(b)) {
            stick(a, b);
            return;
        }
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

    /**
     * Notes what a limpet in flight has touched, for {@code LimpetSystem} to stick it to (G0100), and stops there - a
     * limpet lands no blow and takes none, so shots and claws pass through it.
     */
    private static void stick(Entity a, Entity b) {
        if (LIMPET.has(a)) {
            touch(a, b);
        }
        if (LIMPET.has(b)) {
            touch(b, a);
        }
    }

    /**
     * It sticks to <strong>scenery</strong>, as a grenade comes off it, and to <strong>anything alive of another
     * side</strong>; it passes through its own side - his men and his own turret - a body in its throes, another limpet, and anything not solid - a
     * shot, ruined ground, a pickup lying on the floor, a rider in the air.
     */
    private static void touch(Entity limpet, Entity other) {
        Limpet thrown = LIMPET.get(limpet);
        if (thrown.stuck || thrown.touched || LIMPET.has(other) || ownSide(thrown.side, other)) {
            return;
        }
        if (!prop(other) && !foe(thrown.side, other)) {
            return;
        }
        Position at = POSITION.get(other);
        thrown.touch(HANDLE.has(other) ? HANDLE.get(other).id : Handle.NOBODY, at.x, at.y);
    }

    /** On this side, which is somebody's: his men, and scenery that is his too - his own turret. */
    private static boolean ownSide(Side side, Entity other) {
        return side != Side.NEUTRAL && FACTION.has(other) && FACTION.get(other).side == side;
    }

    /** Something alive, solid and on its feet, of a side other than {@code side} and other than nobody's. */
    private static boolean foe(Side side, Entity other) {
        if (!FACTION.has(other) || !COLLIDER.has(other) || inert(other)) {
            return false;
        }
        Collider collider = COLLIDER.get(other);
        if (!collider.solid || collider.throughBodies) {
            return false;
        }
        Side theirs = FACTION.get(other).side;
        return theirs != Side.NEUTRAL && theirs != side;
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
        if (!def.offersSomethingTo(FACTION.get(taker).side)
                || !Grants.worthTo(def.grantFor(FACTION.get(taker).side), taker)) {
            return;
        }
        if (pickup.shunned) {
            Taking taking = TAKING.get(taker);
            taking.biteDue = odds.roll(taker, AiChance.BITE_A_BARREL, taking.biteDue, taking.biteChance, deltaTime);
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
        return entity != null && Life.dying(entity);
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
     *
     * <p><strong>A shot with a bounce left comes off whatever would have stopped it</strong>, scenery and
     * the living alike, having landed its blow - so one disc can cut through a crowd one body at a time.
     * Its own side it never touches: {@link #friendly} let it pass before it got here.
     */
    private void spend(Entity projectile, Entity target) {
        if (!glancesOff(projectile, target)) {
            strike(projectile, target);
        }
        if (COLLIDER.has(target) && !COLLIDER.get(target).stopsShots) {
            return;
        }
        Projectile shot = PROJECTILE.get(projectile);
        if (shot.bounces()) {
            comeOff(projectile, shot, target);
        } else if (!shot.piercing || prop(target)) {
            spawner.markDead(projectile);
        }
    }

    /**
     * Turns a shot off a body at the point its path first touched it, spending a bounce: put back on that
     * point, just clear of the body, and mirrored about the line from the body's centre - so the way it
     * leaves is decided by where it struck, never by how far through the body the tick carried it.
     */
    private static void comeOff(Entity projectile, Projectile shot, Entity target) {
        Position at = POSITION.get(projectile);
        Position centre = POSITION.get(target);
        float fromX = at.x;
        float fromY = at.y;
        if (PREVIOUS.has(projectile)) {
            fromX = PREVIOUS.get(projectile).x;
            fromY = PREVIOUS.get(projectile).y;
        }
        float touching = BODY.get(projectile).radius + BODY.get(target).radius;
        float pathX = at.x - fromX;
        float pathY = at.y - fromY;
        float offX = fromX - centre.x;
        float offY = fromY - centre.y;
        float along = 0f;
        float squared = pathX * pathX + pathY * pathY;
        float outside = offX * offX + offY * offY - touching * touching;
        if (outside > 0f && squared > EPSILON) {
            // The first point of the path at touching distance: the smaller root of |off + t*path| = touching.
            float half = offX * pathX + offY * pathY;
            float discriminant = half * half - squared * outside;
            if (discriminant >= 0f) {
                along = clampUnit((-half - (float) Math.sqrt(discriminant)) / squared);
            }
        }
        float normalX = fromX + pathX * along - centre.x;
        float normalY = fromY + pathY * along - centre.y;
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY);
        Velocity velocity = VELOCITY.get(projectile);
        if (length <= EPSILON) {
            // Dead centre: back the way it came.
            float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
            if (speed <= EPSILON) {
                return;
            }
            normalX = -velocity.x;
            normalY = -velocity.y;
            length = speed;
        }
        normalX /= length;
        normalY /= length;
        float clear = touching + CLEAR_OF_THE_BODY;
        at.set(centre.x + normalX * clear, centre.y + normalY * clear);
        Bounce.reflect(velocity, normalX, normalY);
        shot.bounceAt(at.x, at.y);
    }

    private static float clampUnit(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /**
     * Whether a shot arrives at an armoured end and glances off it (G0045, G0073): coming at the thing from
     * inside the arc round its facing, turned to the armoured end, judged by the way the shot is flying rather than where on
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
        // Back along the shot, towards where it came from, against the way the armoured end is pointing.
        Armour armour = ARMOUR.get(target);
        float endX = facing.x * armour.centreCosine - facing.y * armour.centreSine;
        float endY = facing.x * armour.centreSine + facing.y * armour.centreCosine;
        return -(flight.x * endX + flight.y * endY) / speed >= armour.arcCosine;
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
        if (!attack.enabled) {
            // No claws. A grip is its own ability, and was asked about above.
            return;
        }
        if (!attack.ready()) {
            return;
        }
        if (!reaches(attacker, target, attack.arcCosine)) {
            // Its back is turned. The cooldown is not spent either: it never swung.
            return;
        }
        Damage owed = DAMAGE.get(target);
        if (attack.onlySlows()) {
            // Ground that only slows is no blow (G0073): no harm owed, no shield broken, nobody provoked or thrown.
            attack.remainingTicks = attack.cooldownTicks;
            owed.slowBy(attack);
            return;
        }
        boolean staggering = attack.victimInvulnerableTicks > 0;
        if (staggering && owed.staggered()) {
            // Already staggered this tick: no second blow, and no cooldown spent.
            return;
        }
        if (SLITHER.has(attacker)
                && HANDLE.has(target)
                && !SLITHER.get(attacker).bite(Handles.of(target))) {
            // Its head is still passing through him from the last bite: once a pass (G0095).
            return;
        }
        attack.remainingTicks = attack.cooldownTicks;
        // Recorded, not applied, protected target or not: whether it costs anything is DamageSystem's.
        owed.land(attack);
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
     *
     * <p><strong>One that takes hold by touch</strong> (G0083) does so on the ground as well, and so never bites at
     * all: what it touches that it cannot take, it merely touches - and on the ground it waits on its claws.
     *
     * <p><strong>A shield takes the grab</strong> as it takes a bite, and spends the claws the same way - and the
     * leap - so the creature it turned neither bites nor leaps on him again before its waits are out. <strong>So does a
     * deflect</strong> (G0086): a trooper it comes at inside his arc, on its leap's keyed chance, is not taken.
     */
    private boolean takesHoldInsteadOfBiting(Entity attacker, Entity target) {
        Grip grip = GRIP.get(attacker);
        if (grip.holding()) {
            return true;
        }
        boolean airborne = LEAP.has(attacker) && LEAP.get(attacker).airborne();
        if (!airborne && !grip.byTouch) {
            return false;
        }
        if (!airborne
                && CONTACT_DAMAGE.has(attacker)
                && !CONTACT_DAMAGE.get(attacker).ready()) {
            // A touch waits on its claws as a bite does: one a shield has just turned merely touches (G0053).
            return true;
        }
        if (GRIPPED.has(target)
                && !GRIPPED.get(target).held()
                && !Life.down(target)
                && !Drivers.aboard(target)
                && !protectedNow(target)) {
            if (shielded(target)) {
                // A shield takes a grab as it takes any hit, and breaks: the grab lands as a harmless blow,
                // and the creature simply lands (G0053, G0057). Its claws are spent, as on a bite the shield
                // took, and its leap with them, in the air or winding up - or it bites or leaps on him a few
                // ticks later and the shield bought him nothing.
                if (DAMAGE.has(target)) {
                    Damage owed = DAMAGE.get(target);
                    owed.land(0, Harm.NONE);
                    recordSource(attacker, owed);
                }
                spendClaws(attacker);
                if (LEAP.has(attacker)) {
                    LEAP.get(attacker).finish();
                }
                return true;
            }
            if (Deflect.deflects(levelSeed, attacker, target)) {
                // Deflected (G0086): he meets it with his hand strike, swung as it came into his reach, so it never
                // takes hold - it lands, its claws spent as on a grab a shield turned, and the strike lands on it.
                spendClaws(attacker);
                LEAP.get(attacker).finish();
                return true;
            }
            grip.take(Handles.of(target));
            GRIPPED.get(target).by = Handles.of(attacker);
            GRIPPED.get(target).pinned = grip.pins;
            GRIPPED.get(target).fraction = 1f;
            if (airborne) {
                LEAP.get(attacker).finish();
            }
        }
        return true;
    }

    /** Puts a creature's claws on their cooldown, as a bite would have: what it was turned off does not bite it next. */
    private static void spendClaws(Entity attacker) {
        if (CONTACT_DAMAGE.has(attacker)) {
            ContactDamage claws = CONTACT_DAMAGE.get(attacker);
            claws.remainingTicks = claws.cooldownTicks;
        }
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
        if (!shielded(target) && !protectedNow(target)) {
            // A shield takes the hit and whatever holds him with it (G0053); DamageSystem breaks it. A
            // protected target is not held either: the hold is the one thing a blow does on the spot.
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

    private static boolean protectedNow(Entity entity) {
        return INVULNERABLE.has(entity) && INVULNERABLE.get(entity).active();
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
