package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Shock;
import pl.mzebrows.pwt.sim.component.Vitality;

/**
 * Drains the shock nothing built this tick (G0059), and empties it on anything already down - there is
 * nothing left there to stun, and its charge ring has a clock of its own to show.
 *
 * <p>Building it, and the stun it fills to, are {@code DamageSystem}'s, where the hits land.
 */
public final class ShockSystem extends IteratingSystem {

    private static final ComponentMapper<Shock> SHOCK = ComponentMapper.getFor(Shock.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);

    private final float drainPerSecond;

    public ShockSystem(SimConfig config) {
        super(Family.all(Shock.class).get());
        this.drainPerSecond = config.shock().drainPerSecond();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Shock shock = SHOCK.get(entity);
        boolean fed = shock.takeFed();
        if (!shock.charged()) {
            return;
        }
        if ((DYING.has(entity) && DYING.get(entity).begun) || (VITALITY.has(entity) && VITALITY.get(entity).begun)) {
            shock.charge = 0f;
            return;
        }
        if (!fed) {
            shock.drain(drainPerSecond, deltaTime);
        }
    }
}
