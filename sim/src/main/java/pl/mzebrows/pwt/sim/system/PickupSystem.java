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
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Pickup;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Turns the pickups claimed this tick into whatever they grant, and spends the ones that were taken.
 *
 * <p>Contact records the claim; this decides what it means, through {@link Grants} - the same place a
 * pickup chosen out of a crate is handed over (G0056). Runs before damage: a grunt that reaches a flower
 * first gets up whole. A spent pickup is marked dead.
 */
public final class PickupSystem extends EntitySystem {

    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);

    private final Spawner spawner;
    private final Grants grants;
    private final Defs<PickupKind, PickupDef> pickups;

    private ImmutableArray<Entity> takers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public PickupSystem(SimConfig config, Spawner spawner, Modifiers modifiers) {
        this.spawner = spawner;
        this.grants = new Grants(spawner, modifiers);
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
            grants.apply(taker, pickups.of(kind).grantFor(FACTION.get(taker).side), levels);
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
}
