package pl.mzebrows.pwt.sim.system.combat;

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
import pl.mzebrows.pwt.sim.component.Pop;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Taking;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.PickupDef;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Turns the pickups claimed this tick into whatever they grant, and spends the ones that were taken.
 *
 * <p>Contact records the claim; this decides what it means, through {@link Grants} - the same place a
 * pickup chosen out of a crate is handed over (G0056). Runs before damage: a grunt that reaches a flower
 * first gets up whole. A spent pickup is marked dead.
 *
 * <p><strong>A take by touch on the player's side leaves a pop</strong> where the taker stands, wearing what he took
 * (G0078) - never the aliens' meals, whose tell is the strain they grow into. A pop that has run out is marked dead
 * here too: what spawns it ends it, as a blast's system does its.
 */
public final class PickupSystem extends EntitySystem {

    private static final ComponentMapper<Taking> TAKING = ComponentMapper.getFor(Taking.class);
    private static final ComponentMapper<Faction> FACTION = ComponentMapper.getFor(Faction.class);
    private static final ComponentMapper<Pickup> PICKUP = ComponentMapper.getFor(Pickup.class);
    private static final ComponentMapper<BlastsOnDeath> REACTION = ComponentMapper.getFor(BlastsOnDeath.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Pop> POP = ComponentMapper.getFor(Pop.class);

    private final Spawner spawner;
    private final Grants grants;
    private final Defs<PickupKind, PickupDef> pickups;

    private ImmutableArray<Entity> takers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> lying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> pops = new ImmutableArray<>(new Array<>());

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
        pops = engine.getEntitiesFor(Family.all(Pop.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < pops.size(); i++) {
            Entity pop = pops.get(i);
            if (POP.get(pop).isOver() && !pop.isScheduledForRemoval()) {
                spawner.markDead(pop);
            }
        }
        for (int i = 0; i < takers.size(); i++) {
            Entity taker = takers.get(i);
            Taking taking = TAKING.get(taker);
            PickupKind kind = taking.claimed;
            if (kind == null) {
                continue;
            }
            taking.claimed = null;
            PickupDef taken = pickups.of(kind);
            Side side = FACTION.get(taker).side;
            grants.apply(taker, taken.grantFor(side), levels);
            if (side == Side.PLAYER && POSITION.has(taker)) {
                Position at = POSITION.get(taker);
                spawner.spawnPop(at.x, at.y, taken.look(), taken.radius());
            }
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
