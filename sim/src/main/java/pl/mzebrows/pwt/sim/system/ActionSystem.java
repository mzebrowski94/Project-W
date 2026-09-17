package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * Decides what the commander could do with his hands right now, and does it while he asks.
 *
 * <p><strong>One key, and the game chooses what it means.</strong> Whatever is within arm's length
 * is what is on offer, nearest first; there is no menu and nothing to select. That is pillar 1 -
 * anything that makes the player stop and pick belongs in a different game - and it is why the
 * available action is published on {@link Action} for the interface to show rather than assembled by
 * the interface from what it can see.
 *
 * <p><strong>This is the player's half only.</strong> A trooper does the same thing off its own
 * judgement in {@code AidSystem}, and both go through {@link Aid} so that a man picked up by a
 * trooper comes up exactly as a man picked up by the commander does. What is here rather than there
 * is the key: an action is offered, held, and lost the moment it is let go.
 *
 * <p>The work resets the moment he lets go. A stabilise is a commitment to stand still in the open,
 * and a version that accumulated across interruptions would quietly turn that into no commitment at
 * all - you would tap the key on every pass.
 *
 * <p>It runs <strong>after</strong> {@code DamageSystem} and <strong>before</strong>
 * {@code CasualtySystem}, and both halves of that matter. After, because a man who goes down this
 * tick must be on offer this tick - decided any earlier and the prompt is always one tick behind the
 * body hitting the ground. Before, because a man picked up on the tick his clock would have run out
 * should be picked up rather than buried.
 *
 * <p>Being that late also means it reads positions the solver has already settled, so arm's length
 * means where things actually ended up rather than where they were going.
 */
public final class ActionSystem extends EntitySystem {

    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);

    private final float reviveHealthFraction;
    private final float reviveInvulnerableSeconds;

    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());

    public ActionSystem(SimConfig config) {
        this.reviveHealthFraction = config.casualty().reviveHealthFraction();
        this.reviveInvulnerableSeconds = config.casualty().reviveInvulnerableSeconds();
    }

    @Override
    public void addedToEngine(Engine engine) {
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Intent.class, Action.class, Position.class, Body.class)
                        .get());
        casualties = engine.getEntitiesFor(
                Family.all(Casualty.class, Position.class, Body.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            Action action = ACTION.get(commander);
            Entity subject = Drivers.held(commander) ? null : Aid.nearest(casualties, commander, action.reach);
            if (subject == null) {
                action.idle();
                continue;
            }
            action.available = ActionKind.STABILISE;
            if (INTENT.get(commander).acting) {
                Aid.work(subject, deltaTime, reviveHealthFraction, reviveInvulnerableSeconds);
            } else {
                Aid.letGo(subject);
            }
            action.progress = Aid.progress(subject);
        }
    }
}
