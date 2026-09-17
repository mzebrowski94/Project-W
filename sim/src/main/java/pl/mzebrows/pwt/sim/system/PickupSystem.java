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
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Ranged;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Turns the pickups claimed this tick into whatever they grant, and spends the ones that were taken.
 *
 * <p>The second half of the pattern {@code ContactDamage} and {@code DamageSystem} already share:
 * contact records what is owed and this decides what it means. Collision resolution can say that a
 * unit touched a mushroom; only here is it known what the mushroom does, whether the taker is on a
 * side that cares, or where a grant that belongs to the level rather than to anybody has to land.
 *
 * <p><strong>The switch is exhaustive over a sealed type on purpose.</strong> Adding a kind of grant
 * fails this compile until somebody decides what it does, which is the cheapest possible guarantee
 * that a new effect cannot be defined and then silently ignored. That property is the whole reason
 * grants are records rather than callbacks.
 *
 * <p>It runs before damage is applied, and that ordering is a design decision rather than an
 * accident: a grunt shot down to a sliver that reaches a flower first gets up whole. The pickup was
 * on screen, the player could see it, and losing a kill to it is a mistake the player made rather
 * than one the game made.
 *
 * <p>It removes nothing. A spent pickup is marked {@code Dead} and the reaper takes it at the end of
 * the tick like everything else.
 */
public final class PickupSystem extends EntitySystem {

    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<Feed> FEED = ComponentMapper.getFor(Feed.class);
    private static final ComponentMapper<Ranged> RANGED = ComponentMapper.getFor(Ranged.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);

    private final Spawner spawner;
    private final Defs<PickupKind, PickupDef> pickups;

    private ImmutableArray<Entity> takers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public PickupSystem(SimConfig config, Spawner spawner) {
        this.spawner = spawner;
        this.pickups = config.pickups();
    }

    @Override
    public void addedToEngine(Engine engine) {
        takers = engine.getEntitiesFor(Family.all(Taking.class, Faction.class).get());
        lying = engine.getEntitiesFor(Family.all(Pickup.class).get());
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < takers.size(); i++) {
            Entity taker = takers.get(i);
            Taking taking = TAKING.get(taker);
            PickupKind kind = taking.claimed;
            if (kind == null) {
                continue;
            }
            taking.claimed = null;
            apply(taker, pickups.of(kind), FACTION.get(taker).side);
        }
        for (int i = 0; i < lying.size(); i++) {
            Entity pickup = lying.get(i);
            if (!PICKUP.get(pickup).claimed) {
                continue;
            }
            if (REACTION.has(pickup)) {
                // Being taken is one of two things, and which one is a fact about whatever reached
                // it. Something that swallowed it took it whole and there is no bang at all - it is
                // carrying it now. Something that merely bit into it had its mouth on the thing, so
                // most of the blast goes into it and the circle is far smaller than the one a shot
                // from across the field leaves. The reaction is the same reaction either way
                // (G0018); only the radius it reads is decided here, before the death.
                if (PICKUP.get(pickup).swallowed) {
                    REACTION.get(pickup).swallowed();
                } else {
                    REACTION.get(pickup).bitten();
                }
            }
            spawner.markDead(pickup);
        }
    }

    /** What one side gets out of one pickup. Nothing here allocates - a grant is a value, not a call. */
    private void apply(Entity taker, PickupDef def, Side side) {
        switch (def.grantFor(side)) {
            case Grant.None _ -> {
                // A side with nothing on offer never claims one, so this is unreachable in practice.
                // It exists because the switch is exhaustive, which is the property worth having.
            }
            case Grant.Detonate _ -> charge(taker);
            case Grant.Arm(WeaponKind gun) -> spawner.applyWeapon(taker, gun);
            case Grant.Feed(int amount) -> feed(taker, amount);
            case Grant.Mutate(UnitKind into) -> mutate(taker, into);
            case Grant.Reinforce(int troopers) -> reinforce(troopers);
        }
    }

    /**
     * What is left of a barrel once it has gone off in something's face.
     *
     * <p><strong>Nothing, for almost everything.</strong> Taking a barrel is what kills the barrel,
     * and a thing that blasts on death is already on its way to going off; setting it off here as
     * well would be a second path to the same explosion (G0018). How big that explosion is was
     * settled when the claim was spent, on the barrel itself.
     *
     * <p>A strain that shoots is the exception, and the exception is data: it names on its own
     * definition the gun it carries afterwards, and picking that up goes through the same call a
     * weapons cache goes through. Anything that names none - every strain that closes and claws -
     * still gets nothing at all, which is why a barrel is a gift when a grunt walks into one and a
     * problem when a spitter does (G0022).
     */
    private void charge(Entity taker) {
        if (!RANGED.has(taker)) {
            return;
        }
        Ranged ranged = RANGED.get(taker);
        if (!ranged.enabled || ranged.chargedGun == null) {
            return;
        }
        spawner.applyWeapon(taker, ranged.chargedGun);
    }

    /** Counts towards whatever the taker's strain evolves into. {@code EvolutionSystem} decides when. */
    private static void feed(Entity taker, int amount) {
        if (FEED.has(taker)) {
            FEED.get(taker).add(amount);
        }
    }

    /** Names the strain the taker is about to become. {@code EvolutionSystem} is what performs it. */
    private static void mutate(Entity taker, UnitKind into) {
        if (FEED.has(taker)) {
            FEED.get(taker).mutateInto(into);
        }
    }

    /**
     * A grant that belongs to the level rather than to whoever picked it up.
     *
     * <p>This is the case G0004 said would one day need a message queue, answered without one: the
     * level is an ordinary entity, so reaching it is an ordinary family lookup.
     */
    private void reinforce(int troopers) {
        for (int i = 0; i < levels.size(); i++) {
            LEVEL.get(levels.get(i)).pendingReinforcements += troopers;
        }
    }
}
