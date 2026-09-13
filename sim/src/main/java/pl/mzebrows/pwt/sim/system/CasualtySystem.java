package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;

/**
 * Runs the clock on a man who is down, and finishes him when it runs out.
 *
 * <p>The mirror of {@code DyingSystem}: that one takes a body away once it has lain there long
 * enough, this one decides that a man nobody reached is a body. Both end at the same call, so a
 * bleed-out leaves exactly the corpse a bullet would have.
 *
 * <p><strong>The clock is counted here rather than in {@code CooldownSystem}</strong>, which is
 * where every other timer in the game lives. The rate it runs at is not a constant - being hauled
 * slows it, and by default stops it outright - and a timer whose speed something else decides is not
 * a cooldown, it is a decision with a number attached. Counting down is not deciding, so the moment
 * counting down required a decision it moved out.
 *
 * <p>That the rate is a number rather than a rule is the point: whether hauling a man buys time,
 * costs it, or does neither is a question for play, and it is answered in {@code game.properties}
 * rather than in here.
 *
 * <p>It runs after {@code DamageSystem}, which is what puts a man down, and before
 * {@code DyingSystem}, which is what takes him away: a casualty created this tick therefore gets its
 * full window, and one whose window closes this tick starts its throes in the same tick it would
 * have if something had shot him.
 */
public final class CasualtySystem extends IteratingSystem {

    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);

    private final Spawner spawner;
    private final float draggedBleedFactor;

    public CasualtySystem(SimConfig config, Spawner spawner) {
        super(Family.all(Casualty.class).get());
        this.spawner = spawner;
        this.draggedBleedFactor = config.casualty().draggedBleedFactor();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Casualty casualty = CASUALTY.get(entity);
        if (!casualty.begun || (DYING.has(entity) && DYING.get(entity).begun)) {
            return;
        }
        float rate = DRAGGED.has(entity) && DRAGGED.get(entity).held() ? draggedBleedFactor : 1f;
        casualty.remainingSeconds -= deltaTime * rate;
        if (casualty.remainingSeconds <= 0f) {
            casualty.remainingSeconds = 0f;
            Deaths.kill(spawner, entity);
        }
    }
}
