package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.RespawnsOnDeath;

/**
 * A death reaction, and the countdown it starts.
 *
 * <p>Runs inside the window between a unit being marked dead and being reaped, which is where every
 * reaction to a death belongs (G0004). It reads the reaction off the dying unit rather than knowing
 * anything about commanders, so a trooper that respawns will need no change here at all.
 *
 * <p>The countdown lives on a ticket entity of its own because the unit holding it is about to stop
 * existing. Keeping the dead unit around in a half-alive state instead would mean every family in
 * the game having to exclude it.
 */
public final class RespawnSystem extends EntitySystem {

    private static final ComponentMapper<RespawnsOnDeath> REACTION = ComponentMapper.getFor(RespawnsOnDeath.class);
    private static final ComponentMapper<Respawning> PENDING = ComponentMapper.getFor(Respawning.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private final Spawner spawner;

    private ImmutableArray<Entity> dying = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> pending = new ImmutableArray<>(new Array<>());

    public RespawnSystem(Spawner spawner) {
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        dying = engine.getEntitiesFor(
                Family.all(Dead.class, RespawnsOnDeath.class).get());
        pending = engine.getEntitiesFor(Family.all(Respawning.class).get());
    }

    @Override
    public void update(float deltaTime) {
        openTickets();
        closeExpiredTickets(deltaTime);
    }

    /** Runs once per death: the unit is marked dead now and reaped at the end of this same tick. */
    private void openTickets() {
        for (int i = 0; i < dying.size(); i++) {
            RespawnsOnDeath reaction = REACTION.get(dying.get(i));
            spawner.spawnRespawn(reaction.delaySeconds, reaction.x, reaction.y, reaction.invulnerableSeconds);
        }
    }

    private void closeExpiredTickets(float deltaTime) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Entity ticket = pending.get(i);
            Respawning respawning = PENDING.get(ticket);
            respawning.remainingSeconds -= deltaTime;
            if (respawning.remainingSeconds > 0f) {
                continue;
            }
            Entity commander = spawner.spawnCommander(respawning.x, respawning.y);
            INVULNERABLE.get(commander).grant(respawning.invulnerableSeconds);
            spawner.markDead(ticket);
        }
    }
}
