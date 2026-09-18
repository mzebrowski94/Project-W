package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Players;

/**
 * Who is sitting where: the keyboard in slot zero and a gamepad in each of the others.
 *
 * <p><strong>Slot zero is the keyboard's and no pad ever takes it</strong> (1.a), so four players is
 * one keyboard and three pads in the alpha. A pad claims the lowest free slot the first time it is
 * seen, which makes plugging one in the whole of joining the game - there is no lobby, no press-start
 * screen and nothing to choose.
 *
 * <p><strong>A seat is kept when its pad goes away.</strong> A pulled cable, a flat battery and
 * somebody kicking the dongle all look the same from here, and all three are almost always followed
 * by the pad coming back. So the seat stays, the commander stays standing on the field, and the pad
 * that comes back is put in the seat it left rather than in a new one - which is what the pad's id is
 * for. What the player loses is the seconds he was unplugged, and his commander spends them standing
 * still, because an empty seat reads as an idle input rather than as the last thing he was holding.
 *
 * <p>Detections are logged, both ways. A pad that does not work is the single most likely thing to go
 * wrong with any of this, and the difference between "the game never saw it" and "the game saw it and
 * the mapping is wrong" is the difference between an evening of guessing and a line in the log.
 *
 * <p>No libGDX application state is touched - only a {@link GamepadSource}, which is the port - so
 * all of this is unit-tested against pads made of plain values.
 */
public final class PlayerSeats {

    /** Player one, always the keyboard and mouse in the alpha phase. */
    public static final int KEYBOARD_SLOT = 0;

    /** The pad id sitting in each slot, or null for a free seat. Slot zero is never a pad's. */
    private final @Nullable String[] idBySlot = new String[Players.MAX];

    /** The live pad for each slot, or null while that slot's pad is unplugged. */
    private final @Nullable Gamepad[] padBySlot = new Gamepad[Players.MAX];

    /** Slots that have taken a pad for the first time and not yet been reported to the caller. */
    private final boolean[] unreported = new boolean[Players.MAX];

    /** Which seats were empty last frame, so a departure is logged once rather than sixty times a second. */
    private final boolean[] wasMissing = new boolean[Players.MAX];

    /** Brings the seating in line with what is plugged in, logging every change. */
    public void refresh(GamepadSource pads) {
        for (int slot = 0; slot < Players.MAX; slot++) {
            padBySlot[slot] = null;
        }
        int count = pads.count();
        for (int i = 0; i < count; i++) {
            seat(pads.get(i));
        }
        reportTheMissing();
    }

    /** The pad driving this slot, or null if the slot is the keyboard's, free, or unplugged. */
    public @Nullable Gamepad padIn(int slot) {
        return padBySlot[slot];
    }

    /** Whether anybody has ever taken this seat. A seat stays taken while its pad is unplugged. */
    public boolean taken(int slot) {
        return slot == KEYBOARD_SLOT || idBySlot[slot] != null;
    }

    /**
     * The lowest slot that has just been taken for the first time, or -1 if none has.
     *
     * <p>Asking is what clears it, so a caller that asks in a loop is told about each new player once
     * and then told there are none. That is the shape a join wants: somebody has to be spawned, and
     * spawning him twice would be worse than not spawning him at all.
     */
    public int nextNewlySeated() {
        for (int slot = 0; slot < Players.MAX; slot++) {
            if (unreported[slot]) {
                unreported[slot] = false;
                return slot;
            }
        }
        return -1;
    }

    private void seat(Gamepad pad) {
        int existing = slotOf(pad.id());
        if (existing >= 0) {
            // Against wasMissing, not against padBySlot - that is cleared at the top of every
            // refresh, so testing it announced a reconnection sixty times a second for a pad that
            // had never been unplugged.
            if (wasMissing[existing]) {
                Log.info("gamepad reconnected in slot " + existing + ": " + pad.name());
            }
            padBySlot[existing] = pad;
            return;
        }
        int free = lowestFreeSlot();
        if (free < 0) {
            Log.info("gamepad ignored, all " + Players.MAX + " slots are taken: " + pad.name());
            return;
        }
        idBySlot[free] = pad.id();
        padBySlot[free] = pad;
        unreported[free] = true;
        Log.info("gamepad seated in slot " + free + ": " + pad.name()
                + (PadLabels.isPlayStation(pad.name()) ? " (PlayStation labels)" : " (Xbox labels)"));
    }

    private void reportTheMissing() {
        for (int slot = 0; slot < Players.MAX; slot++) {
            boolean missing = idBySlot[slot] != null && padBySlot[slot] == null;
            if (missing && !wasMissing[slot]) {
                Log.info("gamepad in slot " + slot + " disconnected; his commander stays on the field");
            }
            wasMissing[slot] = missing;
        }
    }

    private int slotOf(String id) {
        for (int slot = 0; slot < Players.MAX; slot++) {
            if (id.equals(idBySlot[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private int lowestFreeSlot() {
        for (int slot = KEYBOARD_SLOT + 1; slot < Players.MAX; slot++) {
            if (idBySlot[slot] == null) {
                return slot;
            }
        }
        return -1;
    }
}
