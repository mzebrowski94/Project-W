package pl.mzebrows.pwt.sim.system.death;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Ticks;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

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
 * costs it, or does neither is a question for play, and it is answered in {@code assets/sim/*.properties}
 * rather than in here.
 *
 * <p>It runs after {@code DamageSystem}, which is what puts a man down, and before
 * {@code DyingSystem}, which is what takes him away: a casualty created this tick therefore gets its
 * full window, and one whose window closes this tick starts its throes in the same tick it would
 * have if something had shot him.
 */
public final class CasualtySystem extends IteratingSystem {

    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);

    private final Spawner spawner;
    private final Modifiers modifiers;
    /** How fast his clock runs while he is hauled, in hundredths of a tick a tick. */
    private final int draggedBleedRate;

    private final int mendSeconds;
    private final float tickSeconds;

    public CasualtySystem(SimConfig config, Spawner spawner, Modifiers modifiers) {
        super(Family.all(Vitality.class).get());
        this.spawner = spawner;
        this.modifiers = modifiers;
        this.draggedBleedRate = Math.round(config.casualty().draggedBleedFactor() * Vitality.TICK);
        this.mendSeconds = config.wound().mendSeconds();
        this.tickSeconds = config.tickSeconds();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Vitality casualty = VITALITY.get(entity);
        if (Life.dying(entity)) {
            return;
        }
        if (!Life.casualty(entity)) {
            mend(entity, casualty);
            return;
        }
        int rate = DRAGGED.has(entity) && DRAGGED.get(entity).held() ? draggedBleedRate : Vitality.TICK;
        if (casualty.bleed(rate)) {
            Deaths.kill(spawner, entity);
        }
    }

    /**
     * A standing man's wounds clear on their own once long enough has passed since the last one - the
     * one place {@code MEND_SECONDS} is read (G0053). Zero, which is every man without the upgrade, never.
     * A new wound starts the clock again ({@code Vitality}).
     */
    private void mend(Entity entity, Vitality vitality) {
        if (vitality.wounds <= 0) {
            return;
        }
        int seconds = modifiers.mendSeconds(entity, mendSeconds);
        if (seconds <= 0) {
            vitality.mendTicks = 0;
            return;
        }
        vitality.mendTicks++;
        if (vitality.mendTicks >= Ticks.of(seconds, tickSeconds)) {
            vitality.recover();
        }
    }
}
