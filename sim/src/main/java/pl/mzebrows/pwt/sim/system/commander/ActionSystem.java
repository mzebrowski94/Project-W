package pl.mzebrows.pwt.sim.system.commander;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.NotState;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Dash;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Intent;
import pl.mzebrows.pwt.sim.component.Level;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.system.query.Drivers;
import pl.mzebrows.pwt.sim.system.query.Reach;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;

/**
 * Decides what the commander could do with his hands right now, and does it while he asks.
 *
 * <p><strong>One key, and the game chooses what it means.</strong> Whatever is within arm's length
 * is what is on offer, nearest first; there is nothing to select. A tie goes to the gun, then the crate,
 * then the casualty, and only then to a shut door (G0069). A crate is the one thing the key opens a choice on - what to take from it, in the
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
 * <p><strong>A door is opened on stabilise's rules</strong> (G0069): held at arm's length, the work lost the
 * moment he lets go, steps away, goes down or takes a wound. Here he only puts his hands to it; how much work a
 * tick is worth, and when the door gives, is {@code DoorSystem}'s - so a commander and a trooper at one door
 * open it no faster than one.
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
    private static final ComponentMapper<Door> DOOR = ComponentMapper.getFor(Door.class);
    private static final ComponentMapper<Vitality> VITALITY = ComponentMapper.getFor(Vitality.class);
    private static final ComponentMapper<Crew> CREW = ComponentMapper.getFor(Crew.class);
    private static final ComponentMapper<Dash> DASH = ComponentMapper.getFor(Dash.class);

    private final float reviveInvulnerableSeconds;
    private final Modifiers modifiers;

    /** The map's tile, which a doorway is counted in. */
    private final float tileSize;

    /**
     * Scratch, by slot: pressed to open a crate this tick. Opened after every commander has been heard, in
     * slot order, so who gets which crate never depends on the order the commanders are held in (G0056).
     */
    @NotState("scratch: every press is spent before the update ends")
    private final boolean[] opening;

    private ImmutableArray<Entity> commanders = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> casualties = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> stations = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> crates = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> levels = new ImmutableArray<>(new Array<>());
    private ImmutableArray<Entity> doors = new ImmutableArray<>(new Array<>());

    /**
     * @param tileSize the map's tile, which a doorway is counted in
     * @param capacity how many slots the game holds
     */
    public ActionSystem(SimConfig config, Modifiers modifiers, float tileSize, int capacity) {
        this.modifiers = modifiers;
        this.tileSize = tileSize;
        this.reviveInvulnerableSeconds = config.casualty().reviveInvulnerableSeconds();
        this.opening = new boolean[capacity];
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
        doors = engine.getEntitiesFor(Family.all(Door.class).get());
    }

    @Override
    public void update(float deltaTime) {
        long tick = levels.size() == 0 ? 0L : LEVEL.get(levels.first()).tick;
        for (int i = 0; i < commanders.size(); i++) {
            Entity commander = commanders.get(i);
            Action action = ACTION.get(commander);
            boolean acting = INTENT.get(commander).acting;
            boolean pressed = acting && !action.wasHeld;
            action.wasHeld = acting;
            // A dash takes his hands off any work in hand, and the work is lost as letting go loses it (G0107).
            boolean working = acting && !dashing(commander);
            if (Drivers.aboard(commander)) {
                offer(action, ActionKind.LEAVE_TURRET, CREW.get(commander).station);
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
            @Nullable Entity station = Reach.nearest(stations, commander, action.reach, ActionKind.MAN_TURRET);
            @Nullable Entity casualty = Reach.nearest(casualties, commander, action.reach, ActionKind.STABILISE);
            @Nullable Entity crate = Reach.nearest(crates, commander, action.reach, ActionKind.OPEN_CRATE);
            @Nullable Entity door = Reach.nearestDoor(doors, commander, action.reach, tileSize);
            if (door != null
                    && Reach.doorGap(commander, door, tileSize) < nearestGap(commander, station, crate, casualty)) {
                // The bar is the door's, drawn over it; nothing fills on him.
                offer(action, ActionKind.OPEN_DOOR, Handles.of(door));
                if (working) {
                    putHandsTo(commander, DOOR.get(door));
                }
                continue;
            }
            if (station != null
                    && nearer(commander, station, casualty)
                    && (crate == null || Reach.gap(commander, station) <= Reach.gap(commander, crate))) {
                offer(action, ActionKind.MAN_TURRET, Handles.of(station));
                if (pressed) {
                    Crews.board(commander, station);
                }
                continue;
            }
            if (crate != null && (casualty == null || Reach.gap(commander, crate) <= Reach.gap(commander, casualty))) {
                offer(action, ActionKind.OPEN_CRATE, Handles.of(crate));
                opening[slot] |= pressed;
                continue;
            }
            if (casualty == null) {
                action.idle();
                continue;
            }
            action.available = ActionKind.STABILISE;
            action.subject = Handles.of(casualty);
            if (working) {
                if (Aid.work(modifiers, commander, casualty, deltaTime, reviveInvulnerableSeconds)
                        && TALLY.has(commander)) {
                    // A stabilise is a stabilise, whoever he put back on his feet (G0044).
                    TALLY.get(commander).stabilised++;
                }
            } else {
                Aid.letGo(casualty);
            }
            action.progress = Aid.progress(casualty);
        }
        openCrates(tick);
    }

    /** Whether he is in a dash this tick (G0107). */
    private static boolean dashing(Entity commander) {
        return DASH.has(commander) && DASH.get(commander).running();
    }

    /** Each slot that pressed opens the shut crate he would reach first, lowest slot first. */
    private void openCrates(long tick) {
        for (int slot = 0; slot < opening.length; slot++) {
            if (!opening[slot]) {
                continue;
            }
            opening[slot] = false;
            @Nullable Entity commander = Players.inSlot(commanders, slot);
            @Nullable
            Entity crate = commander == null
                    ? null
                    : Reach.nearest(crates, commander, ACTION.get(commander).reach, ActionKind.OPEN_CRATE);
            if (crate != null) {
                Crates.open(crate, slot, tick);
            }
        }
    }

    /**
     * A tick of his hands on a door: work for {@code DoorSystem} to count, or - on the tick a wound lands on him
     * - every tick of it lost (G0069).
     */
    private static void putHandsTo(Entity commander, Door door) {
        if (VITALITY.has(commander) && VITALITY.get(commander).woundedThisTick()) {
            door.workTicks = 0;
            return;
        }
        door.worked = true;
    }

    /** How near the nearest of the other offers is, surface to surface; none at all is as far as can be. */
    private static float nearestGap(
            Entity commander, @Nullable Entity station, @Nullable Entity crate, @Nullable Entity casualty) {
        float nearest = Float.MAX_VALUE;
        if (station != null) {
            nearest = Math.min(nearest, Reach.gap(commander, station));
        }
        if (crate != null) {
            nearest = Math.min(nearest, Reach.gap(commander, crate));
        }
        if (casualty != null) {
            nearest = Math.min(nearest, Reach.gap(commander, casualty));
        }
        return nearest;
    }

    /**
     * Whether the gun is the nearer of the two things on offer (G0021).
     *
     * <p>Ties go to the gun, which is the one case where it matters which way a tie falls: a man
     * lying against a turret is the moment the player most wants the key to mean one specific thing,
     * and getting on the gun is the reversible half of the pair.
     */
    private static boolean nearer(Entity commander, Entity station, @Nullable Entity casualty) {
        return casualty == null || Reach.gap(commander, station) <= Reach.gap(commander, casualty);
    }

    /**
     * Puts an offer on the table, naming what it is made on for the interface to bracket (G0098), with no work
     * behind it on him - getting on and off a gun is instant, and a door's bar is the door's.
     */
    private static void offer(Action action, ActionKind kind, long subject) {
        action.available = kind;
        action.subject = subject;
        action.progress = 0f;
    }
}
