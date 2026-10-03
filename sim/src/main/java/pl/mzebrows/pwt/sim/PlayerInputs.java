package pl.mzebrows.pwt.sim;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * One tick's worth of what every player is asking for, by slot: what each is holding, and the
 * <em>input events</em> that tick carries.
 *
 * <p><strong>Held state and events are kept apart</strong> (ADR 0012): held state is safe to repeat
 * for a late slot; an event must apply exactly once. Mutable, to allocate nothing per tick; nothing
 * holds it past {@code step}. An empty or unplugged slot reads as {@link CommanderInput#IDLE}.
 */
public final class PlayerInputs {

    private final CommanderInput[] bySlot;

    private final @Nullable Order[] orders;

    private final boolean[] joins;

    private final int[] joinMachines;

    private final @Nullable UpgradeKind[] picks;

    private final int[] pickSerials;

    private final boolean[] leaving;

    private final @Nullable PickupKind[] crateTakes;

    private final boolean[] crateCloses;

    private final boolean[] returning;

    private boolean invulnerabilitySwitched;

    private boolean invulnerable;

    /** One tick for as many players as the game can hold. */
    public PlayerInputs() {
        this(Players.MAX);
    }

    /** One tick for a game of this many slots - the capacity every slot number here is checked against. */
    public PlayerInputs(int capacity) {
        this.bySlot = new CommanderInput[capacity];
        this.orders = new Order[capacity];
        this.joins = new boolean[capacity];
        this.joinMachines = new int[capacity];
        this.picks = new UpgradeKind[capacity];
        this.pickSerials = new int[capacity];
        this.leaving = new boolean[capacity];
        this.crateTakes = new PickupKind[capacity];
        this.crateCloses = new boolean[capacity];
        this.returning = new boolean[capacity];
        clear();
    }

    /** How many slots this holds. */
    public int capacity() {
        return bySlot.length;
    }

    /** What the player in this slot is holding. {@link CommanderInput#IDLE} if nobody is. */
    public CommanderInput get(int slot) {
        requireSlot(slot);
        return bySlot[slot];
    }

    /** Sets what one slot is holding and returns this, so a frame's worth reads as one statement. */
    public PlayerInputs set(int slot, CommanderInput input) {
        requireSlot(slot);
        bySlot[slot] = input;
        return this;
    }

    /** An order this slot's player gives on this tick, or none. An event: given once, not held. */
    public PlayerInputs order(int slot, @Nullable Order order) {
        requireSlot(slot);
        orders[slot] = order;
        return this;
    }

    /** The order this slot's player gives on this tick, or null if he gives none. */
    public @Nullable Order orderOf(int slot) {
        requireSlot(slot);
        return orders[slot];
    }

    /**
     * A player takes this slot on this tick. An event: the simulation puts a commander on the field for
     * him if the slot has none, and does nothing if it already has one (G0028, G0038).
     */
    public PlayerInputs join(int slot) {
        return join(slot, 0);
    }

    /**
     * A player takes this slot on this tick, sitting at this machine. The machine is what the leash is
     * reckoned by (G0056): commanders sharing one screen are held together, commanders at different machines
     * are not. Offline every slot sits at machine 0; online it is the peer that holds the slot.
     */
    public PlayerInputs join(int slot, int machine) {
        requireSlot(slot);
        joins[slot] = true;
        joinMachines[slot] = machine;
        return this;
    }

    /** The machine a player joining this slot on this tick sits at. Meaningless unless he {@link #joins}. */
    public int machineOf(int slot) {
        requireSlot(slot);
        return joinMachines[slot];
    }

    /** Whether a player takes this slot on this tick. */
    public boolean joins(int slot) {
        requireSlot(slot);
        return joins[slot];
    }

    /**
     * This slot's player takes the card of this kind from the draft with this serial (G0049). An event:
     * the kind, never the card's place, so a card re-dealt between the press and the tick cannot hand
     * him something he never saw. Whether it counts is the simulation's to decide.
     */
    public PlayerInputs pick(int slot, int serial, UpgradeKind kind) {
        requireSlot(slot);
        picks[slot] = kind;
        pickSerials[slot] = serial;
        return this;
    }

    /** The kind this slot picks on this tick, or null if it picks nothing. */
    public @Nullable UpgradeKind pickOf(int slot) {
        requireSlot(slot);
        return picks[slot];
    }

    /** The serial of the draft this slot picks from on this tick. */
    public int pickSerialOf(int slot) {
        requireSlot(slot);
        return pickSerials[slot];
    }

    /**
     * This slot's player takes a pickup of this kind from the crate he has open (G0056). An event, by kind
     * rather than by place. Whether it counts - his crate, still holding one, worth something to him - is
     * the simulation's to decide.
     */
    public PlayerInputs takeFromCrate(int slot, PickupKind kind) {
        requireSlot(slot);
        crateTakes[slot] = kind;
        return this;
    }

    /** The kind this slot takes from his crate on this tick, or null. */
    public @Nullable PickupKind crateTakeOf(int slot) {
        requireSlot(slot);
        return crateTakes[slot];
    }

    /** This slot's player closes the crate he has open (G0056). An event. */
    public PlayerInputs closeCrate(int slot) {
        requireSlot(slot);
        crateCloses[slot] = true;
        return this;
    }

    /** Whether this slot closes his crate on this tick. */
    public boolean closesCrate(int slot) {
        requireSlot(slot);
        return crateCloses[slot];
    }

    /**
     * This slot's link has dropped: it is away from this tick (G0052). An event the host's sequencer
     * seals; offline nothing sends it.
     */
    public PlayerInputs away(int slot) {
        requireSlot(slot);
        leaving[slot] = true;
        return this;
    }

    /** Whether this slot goes away on this tick. */
    public boolean goesAway(int slot) {
        requireSlot(slot);
        return leaving[slot];
    }

    /** This slot's machine is back and re-seated: it is present again from this tick (G0052). */
    public PlayerInputs back(int slot) {
        requireSlot(slot);
        returning[slot] = true;
        return this;
    }

    /** Whether this slot comes back on this tick. */
    public boolean comesBack(int slot) {
        requireSlot(slot);
        return returning[slot];
    }

    /**
     * Switches the harness's invulnerability on or off from this tick. An event: it stays as switched,
     * on the level, until another switch arrives. Harness only - pillar 2 says the commander can be
     * killed, and {@code ArchitectureTest} keeps scenarios from calling this.
     */
    public PlayerInputs switchInvulnerability(boolean on) {
        invulnerabilitySwitched = true;
        invulnerable = on;
        return this;
    }

    /** Whether this tick switches invulnerability at all. */
    public boolean switchesInvulnerability() {
        return invulnerabilitySwitched;
    }

    /** Which way it switches it, when {@link #switchesInvulnerability()} says it does. */
    public boolean invulnerable() {
        return invulnerable;
    }

    /**
     * Puts every slot back to idle and drops every event. Called before each frame is filled in, so a
     * slot that has gone quiet does not repeat itself and an event is not carried into a second tick.
     */
    public PlayerInputs clear() {
        for (int slot = 0; slot < bySlot.length; slot++) {
            bySlot[slot] = CommanderInput.IDLE;
            orders[slot] = null;
            joins[slot] = false;
            joinMachines[slot] = 0;
            picks[slot] = null;
            pickSerials[slot] = 0;
            leaving[slot] = false;
            crateTakes[slot] = null;
            crateCloses[slot] = false;
            returning[slot] = false;
        }
        invulnerabilitySwitched = false;
        invulnerable = false;
        return this;
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= bySlot.length) {
            throw new IllegalArgumentException("player slot must be in [0, " + bySlot.length + "), was " + slot);
        }
    }
}
