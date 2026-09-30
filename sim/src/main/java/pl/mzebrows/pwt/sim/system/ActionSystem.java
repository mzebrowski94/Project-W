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
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.upgrade.Modifiers;

/**
 * Decides what the commander could do with his hands right now, and does it while he asks.
 *
 * <p><strong>One key, and the game chooses what it means.</strong> Whatever is within arm's length
 * is what is on offer, nearest first; there is nothing to select. A tie goes to the gun, then the crate,
 * then the casualty. A crate is the one thing the key opens a choice on - what to take from it, in the
 * top bar - and that choice is {@code CrateSystem}'s (G0056). That is pillar 1 -
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

    private static final ComponentMapper<Tally> TALLY = ComponentMapper.getFor(Tally.class);
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);
    private static final ComponentMapper<Intent> INTENT = ComponentMapper.getFor(Intent.class);
    private static final ComponentMapper<Commander> COMMANDER = ComponentMapper.getFor(Commander.class);
    private static final ComponentMapper<Level> LEVEL = ComponentMapper.getFor(Level.class);

    private final float reviveInvulnerableSeconds;
    private final Modifiers modifiers;

    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> stations = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> crates = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());

    public ActionSystem(SimConfig config, Modifiers modifiers) {
        this.modifiers = modifiers;
        this.reviveInvulnerableSeconds = config.casualty().reviveInvulnerableSeconds();
    }

    @Override
    public void addedToEngine(Engine engine) {
        commanders = engine.getEntitiesFor(
                Family.all(Commander.class, Intent.class, Action.class, Position.class, Body.class)
                        .get());
        casualties = engine.getEntitiesFor(
                Family.all(Vitality.class, Position.class, Body.class).get());
        stations = engine.getEntitiesFor(
                Family.all(Turret.class, Position.class, Body.class).get());
        crates = engine.getEntitiesFor(
                Family.all(Crate.class, Position.class, Body.class).get());
        levels = engine.getEntitiesFor(Family.all(Level.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            Action action = ACTION.get(commander);
            boolean acting = INTENT.get(commander).acting;
            boolean pressed = acting && !action.wasHeld;
            action.wasHeld = acting;
            if (Drivers.aboard(commander)) {
                offer(action, ActionKind.LEAVE_TURRET);
                if (pressed) {
                    Crews.leave(stations, commander);
                }
                continue;
            }
            int slot = COMMANDER.get(commander).slot;
            if (Drivers.held(commander) || Crates.openBy(crates, slot) != null) {
                // A crate he has open has the key's next press (G0056): the cards in his top bar do.
                action.idle();
                continue;
            }
            @Nullable Entity station = Crews.nearestFree(stations, commander, action.reach);
            @Nullable Entity subject = Aid.nearest(casualties, commander, action.reach);
            long tick = levels.size() == 0 ? 0L : LEVEL.get(levels.first()).tick;
            @Nullable Entity crate = Crates.nearestOpenable(crates, commander, action.reach, slot, tick);
            if (station != null
                    && nearer(commander, station, subject)
                    && (crate == null || Crews.gapTo(commander, station) <= Crates.gapTo(commander, crate))) {
                offer(action, ActionKind.MAN_TURRET);
                if (pressed) {
                    Crews.board(commander, station);
                }
                continue;
            }
            if (crate != null
                    && (subject == null || Crates.gapTo(commander, crate) <= Crates.gapTo(commander, subject))) {
                offer(action, ActionKind.OPEN_CRATE);
                if (pressed) {
                    Crates.open(crate, slot, tick);
                }
                continue;
            }
            if (subject == null) {
                action.idle();
                continue;
            }
            action.available = ActionKind.STABILISE;
            if (acting) {
                if (Aid.work(modifiers, commander, subject, deltaTime, reviveInvulnerableSeconds)
                        && TALLY.has(commander)) {
                    // A stabilise is a stabilise, whoever he put back on his feet (G0044).
                    TALLY.get(commander).stabilised++;
                }
            } else {
                Aid.letGo(subject);
            }
            action.progress = Aid.progress(subject);
        }
    }

    /**
     * Whether the gun is the nearer of the two things on offer (G0021).
     *
     * <p>Ties go to the gun, which is the one case where it matters which way a tie falls: a man
     * lying against a turret is the moment the player most wants the key to mean one specific thing,
     * and getting on the gun is the reversible half of the pair.
     */
    private static boolean nearer(Entity commander, Entity station, @Nullable Entity subject) {
        return subject == null || Crews.gapTo(commander, station) <= Crews.gapTo(commander, subject);
    }

    /** Puts an offer on the table with no work behind it - getting on and off a gun is instant. */
    private static void offer(Action action, ActionKind kind) {
        action.available = kind;
        action.progress = 0f;
    }
}
