package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;

/**
 * Turns the damage owed this tick into lost health, and health at zero into a death.
 *
 * <p>The only system that reduces health, and the only one that decides something has died. Contact
 * resolution adds to a {@link Damage} component and knows nothing about health, invulnerability or
 * what dying involves - which is what keeps a new damage source from having to learn all three.
 *
 * <p>Damage owed is always cleared, invulnerable or not. Carrying it over would mean a unit that
 * survived a hit behind protection dies the moment the protection lapses, from a blow it already
 * shrugged off.
 */
public final class DamageSystem extends IteratingSystem {

    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Damage> DAMAGE = ComponentMapper.getFor(Damage.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);

    private final Spawner spawner;

    public DamageSystem(Spawner spawner) {
        super(Family.all(Health.class, Damage.class).get());
        this.spawner = spawner;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Damage damage = DAMAGE.get(entity);
        if (damage.amount <= 0f) {
            return;
        }
        float owed = damage.amount;
        float recovery = damage.recoverySeconds;
        damage.amount = 0f;
        damage.recoverySeconds = 0f;
        if (INVULNERABLE.has(entity) && INVULNERABLE.get(entity).active()) {
            return;
        }
        Health health = HEALTH.get(entity);
        health.current -= owed;
        if (INVULNERABLE.has(entity)) {
            INVULNERABLE.get(entity).grant(recovery);
        }
        if (health.current <= 0f) {
            spawner.markDead(entity);
        }
    }
}
