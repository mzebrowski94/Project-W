package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.collision.ConstraintSolver;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Collider;
import pl.mzebrows.pwt.sim.component.ContactDamage;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;

/**
 * Runs the constraint solver, then decides what this tick's contacts mean.
 *
 * <p>The split matters: the solver knows geometry and nothing else, and this class knows the game and
 * no geometry at all.
 *
 * <p>There is one rule for every contact, and it is about sides rather than about kinds. Nothing
 * happens between two entities on the same side, which is what stops a commander shooting itself and
 * enemies eating each other with no special case for either. Across sides, whoever carries a
 * {@link ContactDamage} that is off cooldown lands a hit - claws and bullets alike, differing only in
 * their numbers.
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

    private final ConstraintSolver solver;
    private final Spawner spawner;

    private ImmutableArray<Entity> bodies = new ImmutableArray<>(new Array<>());

    public CollisionSystem(SimConfig config, Spawner spawner) {
        this.solver = new ConstraintSolver(config, this);
        this.spawner = spawner;
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

    /** Same side, so nothing happens. An entity with no side is on nobody's, and is friendly to nobody. */
    private static boolean friendly(Entity a, Entity b) {
        if (!FACTION.has(a) || !FACTION.has(b)) {
            return false;
        }
        Faction.Side side = FACTION.get(a).side;
        return side != Faction.Side.NEUTRAL && side == FACTION.get(b).side;
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
        owed.add(attack.amount, attack.victimInvulnerableSeconds);
    }
}
