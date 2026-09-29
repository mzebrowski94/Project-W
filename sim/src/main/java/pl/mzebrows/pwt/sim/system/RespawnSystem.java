package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;
import pl.mzebrows.pwt.sim.component.Shield;

/**
 * A death reaction, and the countdown it starts.
 *
 * <p>Runs inside the window between a unit being marked dead and being reaped, which is where every
 * reaction to a death belongs (G0004). It reads <em>when</em> and <em>whether</em> off the reaction
 * on the dying unit, so a trooper that respawns will need no change to either.
 *
 * <p><strong>Where</strong> is the one thing it does know about commanders. A man comes back beside
 * whoever of the players is still on his feet, and only falls back to the place on his reaction - the
 * left of the arena - when nobody is. Coming back at the edge is a long walk in single player and, in
 * co-op, a dead player watching his friends fight from across the map; it is also outside the leash,
 * which would make his first thirty seconds a walk he is not allowed to turn away from. A commander
 * who is standing is on ground that is reachable and is not rock, which is why the rally point needs
 * no map query of its own.
 *
 * <p>The countdown lives on a ticket entity of its own because the unit holding it is about to stop
 * existing. Keeping the dead unit around in a half-alive state instead would mean every family in
 * the game having to exclude it.
 */
public final class RespawnSystem extends EntitySystem {

    private static final ComponentMapper<RespawnsOnDeath> REACTION = ComponentMapper.getFor(RespawnsOnDeath.class);
    private static final ComponentMapper<Respawning> PENDING = ComponentMapper.getFor(Respawning.class);
    private static final ComponentMapper<Shield> SHIELD = ComponentMapper.getFor(Shield.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);

    private final Spawner spawner;

    private ImmutableArray<Entity> dying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> pending = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());

    public RespawnSystem(Spawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        dying = engine.getEntitiesFor(
                Family.all(Dead.class, RespawnsOnDeath.class).get());
        pending = engine.getEntitiesFor(Family.all(Respawning.class).get());
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        openTickets();
        closeExpiredTickets();
    }

    /** Runs once per death: the unit is marked dead now and reaped at the end of this same tick. */
    private void openTickets() {
        if (dying.size() == 0) {
            return;
        }
        // Asked once for the whole tick, and asked before anybody is brought back, so two men killed
        // by the same blast come back in the same place rather than the second one onto the first.
        Position rally = Drivers.livingTarget(commanders);
        for (int i = 0; i < dying.size(); i++) {
            Entity dead = dying.get(i);
            RespawnsOnDeath reaction = REACTION.get(dead);
            float x = rally == null ? reaction.x : rally.x;
            float y = rally == null ? reaction.y : rally.y;
            int slot = COMMANDER.has(dead) ? COMMANDER.get(dead).slot : 0;
            spawner.spawnRespawn(reaction.delaySeconds, x, y, reaction.shieldSeconds, slot);
        }
    }

    private void closeExpiredTickets() {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Entity ticket = pending.get(i);
            Respawning respawning = PENDING.get(ticket);
            respawning.remainingTicks--;
            if (respawning.remainingTicks > 0) {
                continue;
            }
            Entity commander = spawner.spawnCommander(respawning.x, respawning.y, respawning.slot);
            // Behind a shield that breaks at the first hit or runs out, whichever is first (G0053).
            SHIELD.get(commander).raiseFor(respawning.shieldTicks);
            spawner.markDead(ticket);
        }
    }
}
