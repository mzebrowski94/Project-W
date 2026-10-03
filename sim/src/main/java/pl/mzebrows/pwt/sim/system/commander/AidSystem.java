package pl.mzebrows.pwt.sim.system.commander;

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
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * A trooper standing over a man on the ground, doing the work.
 *
 * <p>The squad's half of what {@code ActionSystem} does for the commander, and deliberately the same
 * shape: find who is within arm's length, spend a tick on him, publish how far along it is. Both go
 * through {@link Aid}, so a man picked up by a trooper stands up with exactly what a man picked up by
 * the commander stands up with - the rules are about the man on the ground, not about who reached him.
 *
 * <p><strong>Deciding to go is not here.</strong> Whether a trooper leaves what it was doing is
 * {@code TrooperMindSystem}'s, along with every other choice about what a trooper is doing; this
 * system only asks whether one that has already decided is close enough to get on with it. That is
 * the same split as {@code TrooperMindSystem} and {@code TrooperMoveSystem} - deciding in one place,
 * doing in another - and it is what keeps the dice out of a system that runs every tick for everyone.
 *
 * <p>A trooper on its way, still walking, finds nobody within reach and publishes nothing. It is not
 * a separate state: being close enough is the whole condition, and the mode alone is what says it is
 * coming.
 *
 * <p>Unlike the commander's version there is nothing to let go of - a trooper that stops aiding stops
 * being in the mode - but the work is still lost rather than banked, because the moment it is out of
 * reach nothing adds to it and the next helper starts from where the counter stands. The thing that
 * <em>clears</em> it is the commander's release; a trooper simply stops.
 *
 * <p>It runs between {@code ActionSystem} and {@code CasualtySystem}, for the reasons that put
 * {@code ActionSystem} there: after the damage that puts a man down, so a man who fell this tick can
 * be worked on this tick, and before the clock that finishes him, so a man reached on the tick his
 * time runs out is picked up rather than buried.
 */
public final class AidSystem extends EntitySystem {

    private static final ComponentMapper<Trooper> TROOPER = ComponentMapper.getFor(Trooper.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);

    private final float reviveInvulnerableSeconds;
    private final Modifiers modifiers;

    private ImmutableArray<Entity> helpers = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());

    public AidSystem(SimConfig config, Modifiers modifiers) {
        this.modifiers = modifiers;
        this.reviveInvulnerableSeconds = config.casualty().reviveInvulnerableSeconds();
    }

    @Override
    public void addedToEngine(Engine engine) {
        helpers = engine.getEntitiesFor(Family.all(Trooper.class, Action.class, Position.class, Body.class)
                .get());
        casualties = engine.getEntitiesFor(
                Family.all(Vitality.class, Position.class, Body.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < helpers.size(); i++) {
            Entity helper = helpers.get(i);
            Action action = ACTION.get(helper);
            if (TROOPER.get(helper).mode != Trooper.Mode.AID || Drivers.held(helper)) {
                action.idle();
                continue;
            }
            Entity subject = Reach.nearest(casualties, helper, action.reach, ActionKind.STABILISE);
            if (subject == null) {
                action.idle();
                continue;
            }
            action.available = ActionKind.STABILISE;
            Aid.work(modifiers, helper, subject, deltaTime, reviveInvulnerableSeconds);
            action.progress = Aid.progress(subject);
        }
    }
}
