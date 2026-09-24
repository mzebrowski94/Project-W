package pl.mzebrows.pwt.sim;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * One tick's worth of what every player is asking for, by slot: what each is holding, and the
 * <em>input events</em> that tick carries.
 *
 * <p><strong>Held state and events are kept apart on purpose</strong> (ADR 0012). Held state - one
 * {@link CommanderInput} per slot - is safe to repeat, which is what happens to a late player's slot
 * online. An event is a one-off and must be applied exactly once: an order given, a player joining,
 * the harness switching invulnerability on or off. A held flag standing in for one would be repeated
 * by that same rule - an order given twice - or lost inside a late window.
 *
 * <p>A mutable holder rather than a record, and deliberately so: it is filled and handed to {@link
 * Simulation#step(PlayerInputs)} sixty times a second, and a fresh one per tick would be an allocation
 * per tick for a thing that is read once and thrown away. The client keeps one and overwrites it;
 * nothing in the simulation holds on to it past {@code step}.
 *
 * <p>A slot nobody is sitting in reads as {@link CommanderInput#IDLE}, which is also what a slot
 * whose gamepad has just been unplugged reads as - a commander left on the field with nobody driving
 * him stands still rather than keeping whatever he was last told to do.
 */
public final class PlayerInputs {

    private final CommanderInput[] bySlot;

    private final @Nullable Order[] orders;

    private final boolean[] joins;

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
        requireSlot(slot);
        joins[slot] = true;
        return this;
    }

    /** Whether a player takes this slot on this tick. */
    public boolean joins(int slot) {
        requireSlot(slot);
        return joins[slot];
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
