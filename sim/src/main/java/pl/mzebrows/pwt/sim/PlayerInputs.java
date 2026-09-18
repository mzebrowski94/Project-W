package pl.mzebrows.pwt.sim;

/**
 * One tick's worth of what every player is asking for, by slot.
 *
 * <p>A mutable holder rather than a record, and deliberately so: it is filled and handed to {@link
 * Simulation#step(PlayerInputs)} sixty times a second, and a fresh one per tick would be an
 * allocation per tick for a thing that is read once and thrown away. The client keeps one and
 * overwrites it; nothing in the simulation holds on to it past {@code step}.
 *
 * <p>A slot nobody is sitting in reads as {@link CommanderInput#IDLE}, which is also what a slot
 * whose gamepad has just been unplugged reads as - a commander left on the field with nobody driving
 * him stands still rather than keeping whatever he was last told to do.
 */
public final class PlayerInputs {

    private final CommanderInput[] bySlot = new CommanderInput[Players.MAX];

    public PlayerInputs() {
        clear();
    }

    /** What the player in this slot is asking for. {@link CommanderInput#IDLE} if nobody is. */
    public CommanderInput get(int slot) {
        requireSlot(slot);
        return bySlot[slot];
    }

    /** Sets one slot's input and returns this, so a frame's worth reads as one statement. */
    public PlayerInputs set(int slot, CommanderInput input) {
        requireSlot(slot);
        bySlot[slot] = input;
        return this;
    }

    /** Puts every slot back to idle. Called before each frame is filled in, so a slot that has gone quiet does not repeat itself. */
    public PlayerInputs clear() {
        for (int slot = 0; slot < bySlot.length; slot++) {
            bySlot[slot] = CommanderInput.IDLE;
        }
        return this;
    }

    private static void requireSlot(int slot) {
        if (!Players.isSlot(slot)) {
            throw new IllegalArgumentException("player slot must be in [0, " + Players.MAX + "), was " + slot);
        }
    }
}
