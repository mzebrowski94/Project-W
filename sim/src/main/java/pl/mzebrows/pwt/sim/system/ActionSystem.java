package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Dragged;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Invulnerable;
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
 * <p><strong>Only the commander stabilises.</strong> A trooper walks past a man on the ground.
 * That is a decision rather than a gap: making the squad self-healing needs a mode of its own and
 * rules for how it ranks against orders and objectives, and both are somebody's next session. It
 * also leaves open who picks <em>him</em> up, which is why he cannot be a casualty yet.
 *
 * <p>The work resets the moment he lets go. A stabilise is a commitment to stand still in the open
 * for a second and a half, and a version that accumulated across interruptions would quietly turn
 * that into no commitment at all - you would tap the key on every pass.
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
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<Casualty> CASUALTY = ComponentMapper.getFor(Casualty.class);
    private static final ComponentMapper<Dragged> DRAGGED = ComponentMapper.getFor(Dragged.class);
    private static final ComponentMapper<Health> HEALTH = ComponentMapper.getFor(Health.class);
    private static final ComponentMapper<Invulnerable> INVULNERABLE = ComponentMapper.getFor(Invulnerable.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);

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
            Entity subject = Drivers.held(commander) ? null : nearestCasualty(commander, action.reach);
            if (subject == null) {
                action.available = ActionKind.NONE;
                action.progress = 0f;
                continue;
            }
            action.available = ActionKind.STABILISE;
            boolean hauled = DRAGGED.has(subject) && DRAGGED.get(subject).hauled;
            stabilise(subject, hauled, INTENT.get(commander).acting, deltaTime);
            action.progress = CASUALTY.get(subject).stabiliseProgress(hauled);
        }
    }

    /**
     * The man on the ground he would reach first, or null if there is nobody.
     *
     * <p>A scan of the casualties rather than a neighbour query: there are almost never any, and on
     * the worst day there are a handful. The collision grid exists for pairs of everything against
     * everything, which this is not.
     *
     * <p>Nearest by distance between surfaces, so a wide man and a narrow one standing equally close
     * are equally close. Nothing already dying is offered, or the key would do nothing over a corpse
     * that had just run out of time.
     */
    private @Nullable Entity nearestCasualty(Entity commander, float reach) {
        Position at = POSITION.get(commander);
        float radius = BODY.get(commander).radius;
        Entity nearest = null;
        float nearestGap = Float.MAX_VALUE;
        for (int i = 0; i < casualties.size(); i++) {
            Entity candidate = casualties.get(i);
            if (!CASUALTY.get(candidate).begun) {
                continue;
            }
            if (DYING.has(candidate) && DYING.get(candidate).begun) {
                continue;
            }
            Position where = POSITION.get(candidate);
            float toX = where.x - at.x;
            float toY = where.y - at.y;
            float gap = (float) Math.sqrt(toX * toX + toY * toY) - radius - BODY.get(candidate).radius;
            if (gap <= reach && gap < nearestGap) {
                nearest = candidate;
                nearestGap = gap;
            }
        }
        return nearest;
    }

    /** A tick's worth of work on him, or a tick's worth of undoing it if nobody is asking. */
    private void stabilise(Entity subject, boolean hauled, boolean asking, float deltaTime) {
        Casualty casualty = CASUALTY.get(subject);
        if (!asking) {
            casualty.stabilised = 0f;
            return;
        }
        casualty.stabilised += deltaTime;
        if (!casualty.stabilisedEnough(hauled)) {
            return;
        }
        casualty.recover();
        HEALTH.get(subject).current = HEALTH.get(subject).max * reviveHealthFraction;
        if (INVULNERABLE.has(subject)) {
            INVULNERABLE.get(subject).grant(reviveInvulnerableSeconds);
        }
    }
}
