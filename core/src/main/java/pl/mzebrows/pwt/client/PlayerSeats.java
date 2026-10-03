package pl.mzebrows.pwt.client;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.ui.hud.TopBarSeats;

/**
 * Who is sitting where on this machine: the keyboard in seat zero and a gamepad in each of the others.
 *
 * <p>A <strong>seat</strong> is a device's place on one machine, and there are {@link #SEATS} of them.
 * Offline a seat and a slot are the same number, which is why the methods here still speak of slots;
 * online the host maps each machine's seats to slots in the game (G0038).
 *
 * <p><strong>Seat zero is the keyboard's and no pad ever takes it</strong> (1.a), so four players is
 * one keyboard and three pads in the alpha. A pad claims the lowest free slot the first time it is
 * seen, which makes plugging one in the whole of joining the game - there is no lobby, no press-start
 * screen and nothing to choose.
 *
 * <p><strong>A seat is kept when its pad goes away.</strong> A pulled cable, a flat battery and
 * somebody kicking the dongle all look the same from here, and all three are almost always followed
 * by the pad coming back. So the seat stays, the commander stays standing on the field, and the pad
 * that comes back is put in the seat it left rather than in a new one. <strong>Not by its id alone</strong>:
 * the desktop backend gives a pad a new random id every time it is plugged in, so a pad that returns is
 * a stranger by id. A pad the seats do not know therefore takes back a seat whose pad is missing - one
 * whose pad had the same name first, else the lowest - and only claims a new seat when none is missing.
 * The cost, accepted: somebody new plugging in while a player's pad is out takes that player's
 * commander, which on one sofa is a word across the room. What the player loses is the seconds he was unplugged, and his commander spends them standing
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
    public static final int KEYBOARD_SLOT = TopBarSeats.KEYBOARD;

    /**
     * How many players one machine seats: one keyboard and three pads (G0038). Fewer than a game
     * holds, because the rest of a game's players are on other machines.
     */
    public static final int SEATS = 4;

    /** The pad id sitting in each slot, or null for a free seat. Slot zero is never a pad's. */
    private final @Nullable String[] idBySlot = new String[SEATS];

    /** The name of the pad last seen in each slot, so a returning pad can find its own seat first. */
    private final @Nullable String[] nameBySlot = new String[SEATS];

    /** The live pad for each slot, or null while that slot's pad is unplugged. */
    private final @Nullable Gamepad[] padBySlot = new Gamepad[SEATS];

    /** Slots that have taken a pad for the first time and not yet been reported to the caller. */
    private final boolean[] unreported = new boolean[SEATS];

    /** Seats whose pad has just gone and that have not been reported to the caller yet. */
    private final boolean[] unreportedUnplug = new boolean[SEATS];

    /** Which seats were empty last frame, so a departure is logged once rather than sixty times a second. */
    private final boolean[] wasMissing = new boolean[SEATS];

    /**
     * How many pads this refresh had nowhere to put, and how many the last one did.
     *
     * <p>A count rather than a set of ids, and it exists for exactly the reason {@link #wasMissing}
     * does: seating is recomputed from scratch sixty times a second, so anything said about a pad
     * without remembering that it has already been said is said sixty times a second. With four pads
     * plugged into a game whose slot zero is the keyboard's, the fourth one printed a line every
     * frame for as long as it was plugged in.
     *
     * <p>Comparing counts is enough and needs no allocation in the loop: a pad that has just been
     * turned away pushes the count above what it was last frame, and one that was already being
     * turned away does not.
     */
    private int turnedAway;

    private int wasTurnedAway;

    /** Brings the seating in line with what is plugged in, logging every change. */
    public void refresh(GamepadSource pads) {
        for (int slot = 0; slot < SEATS; slot++) {
            padBySlot[slot] = null;
        }
        turnedAway = 0;
        int count = pads.count();
        // Two passes: every pad the seats know first, so a stranger can only take back a seat whose
        // own pad is really gone this frame - not one whose pad simply comes later in the list.
        for (int i = 0; i < count; i++) {
            reseat(pads.get(i));
        }
        for (int i = 0; i < count; i++) {
            if (slotOf(pads.get(i).id()) < 0) {
                seat(pads.get(i));
            }
        }
        wasTurnedAway = turnedAway;
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
        for (int slot = 0; slot < SEATS; slot++) {
            if (unreported[slot]) {
                unreported[slot] = false;
                return slot;
            }
        }
        return -1;
    }

    /** Puts a pad the seats already know back in its seat. Does nothing for a stranger. */
    private void reseat(Gamepad pad) {
        int existing = slotOf(pad.id());
        if (existing >= 0) {
            // Against wasMissing, not against padBySlot - that is cleared at the top of every
            // refresh, so testing it announced a reconnection sixty times a second for a pad that
            // had never been unplugged.
            if (wasMissing[existing]) {
                Log.info("gamepad reconnected in slot " + existing + ": " + pad.name());
            }
            padBySlot[existing] = pad;
            nameBySlot[existing] = pad.name();
        }
    }

    /**
     * The lowest seat whose pad has just been unplugged, or -1. Asking clears it, as {@link
     * #nextNewlySeated()} does - offline, it is what pauses the game for the player who lost his pad.
     */
    public int nextNewlyUnplugged() {
        for (int slot = 0; slot < SEATS; slot++) {
            if (unreportedUnplug[slot]) {
                unreportedUnplug[slot] = false;
                return slot;
            }
        }
        return -1;
    }

    /** Seats a pad the seats do not know: in a seat whose pad is gone, else a free one. */
    private void seat(Gamepad pad) {
        int returning = missingSeatFor(pad.name());
        if (returning >= 0) {
            idBySlot[returning] = pad.id();
            padBySlot[returning] = pad;
            nameBySlot[returning] = pad.name();
            Log.info("gamepad reconnected in slot " + returning + ": " + pad.name());
            return;
        }
        int free = lowestFreeSlot();
        if (free < 0) {
            turnedAway++;
            if (turnedAway > wasTurnedAway) {
                Log.info("gamepad ignored, all " + SEATS + " seats on this machine are taken: " + pad.name());
            }
            return;
        }
        idBySlot[free] = pad.id();
        padBySlot[free] = pad;
        nameBySlot[free] = pad.name();
        unreported[free] = true;
        Log.info("gamepad seated in slot " + free + ": " + pad.name()
                + (PadLabels.isPlayStation(pad.name()) ? " (PlayStation labels)" : " (Xbox labels)"));
    }

    private void reportTheMissing() {
        for (int slot = 0; slot < SEATS; slot++) {
            boolean missing = idBySlot[slot] != null && padBySlot[slot] == null;
            if (missing && !wasMissing[slot]) {
                unreportedUnplug[slot] = true;
                Log.info("gamepad in slot " + slot + " disconnected; his commander stays on the field");
            }
            wasMissing[slot] = missing;
        }
    }

    /**
     * A seat whose pad is not plugged in this refresh: the one whose pad had this name, else the
     * lowest. -1 if no seat is waiting for its pad.
     */
    private int missingSeatFor(String name) {
        int any = -1;
        for (int slot = KEYBOARD_SLOT + 1; slot < SEATS; slot++) {
            if (idBySlot[slot] == null || padBySlot[slot] != null) {
                continue;
            }
            if (name.equals(nameBySlot[slot])) {
                return slot;
            }
            if (any < 0) {
                any = slot;
            }
        }
        return any;
    }

    private int slotOf(String id) {
        for (int slot = 0; slot < SEATS; slot++) {
            if (id.equals(idBySlot[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private int lowestFreeSlot() {
        for (int slot = KEYBOARD_SLOT + 1; slot < SEATS; slot++) {
            if (idBySlot[slot] == null) {
                return slot;
            }
        }
        return -1;
    }
}
