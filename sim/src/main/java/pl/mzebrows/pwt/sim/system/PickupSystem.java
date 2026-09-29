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
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Turns the pickups claimed this tick into whatever they grant, and spends the ones that were taken.
 *
 * <p>Contact records the claim; this decides what it means. <strong>The switch is exhaustive over a
 * sealed type on purpose</strong>: a new grant does not compile until somebody decides what it does.
 * Runs before damage: a grunt that reaches a flower first gets up whole. A spent pickup is marked dead.
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
    private final Modifiers modifiers;
    private final Defs<PickupKind, PickupDef> pickups;

    private ImmutableArray<Entity> takers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public PickupSystem(SimConfig config, Spawner spawner, Modifiers modifiers) {
        this.spawner = spawner;
        this.modifiers = modifiers;
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
                // Swallowed whole, no bang; bitten, the smaller blast. Same reaction (G0018).
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
                // Unreachable: a side with nothing on offer never claims one.
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
     * <p>Nothing, unless the strain names a {@code chargedGun} (G0022); the blast is the barrel's own
     * death (G0018).
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
     * <p>The level is an ordinary entity, so no message queue (G0004). What a crate is worth is read
     * here, where it is spent.
     */
    private void reinforce(int troopers) {
        int men = modifiers.menPerCrate(troopers);
        for (int i = 0; i < levels.size(); i++) {
            LEVEL.get(levels.get(i)).pendingReinforcements += men;
        }
    }
}
