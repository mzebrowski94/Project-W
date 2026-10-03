package pl.mzebrows.pwt.client.play;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.input.PlayerSeats;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * This machine's players' one-off input events - a card taken from a draft (G0049), a pickup taken from a
 * crate or the crate shut (G0056) - held by seat until something takes them.
 *
 * <p>An event is delivered once (ADR 0012), and must not be lost on the way: a frame may run no tick at all,
 * and an online session takes no event until it plays. So an event is offered to every tick or frame until
 * one confirms it took it, and only then forgotten. Both sessions hold their events here, under one set of
 * rules: the latest pick per seat stands, and so does the latest crate choice - a take and a shut replace
 * each other.
 */
final class HeldEvents {

    private final @Nullable UpgradeKind[] picks = new UpgradeKind[PlayerSeats.SEATS];

    private final int[] pickSerials = new int[PlayerSeats.SEATS];

    private final @Nullable PickupKind[] crateTakes = new PickupKind[PlayerSeats.SEATS];

    private final boolean[] crateCloses = new boolean[PlayerSeats.SEATS];

    /** Which seats' events the last {@link #offer} handed over, for {@link #taken} to forget. */
    private final boolean[] offered = new boolean[PlayerSeats.SEATS];

    /** Holds a card taken from this draft, by its serial, as the card's kind was shown. */
    void pick(int seat, int serial, UpgradeKind kind) {
        if (seat < 0 || seat >= picks.length) {
            return;
        }
        picks[seat] = kind;
        pickSerials[seat] = serial;
    }

    /** Holds a pickup taken from this seat's open crate, or - for null - the crate shut. */
    void crate(int seat, @Nullable PickupKind take) {
        if (seat < 0 || seat >= crateTakes.length) {
            return;
        }
        crateTakes[seat] = take;
        crateCloses[seat] = take == null;
    }

    /**
     * Writes every held event into {@code into}, at the slot {@code slots} says its seat plays. A seat with no
     * slot, or one past what {@code into} holds, keeps its events for a later offer.
     */
    void offer(PlayerInputs into, IntUnaryOperator slots) {
        for (int seat = 0; seat < offered.length; seat++) {
            offered[seat] = false;
            int slot = slots.applyAsInt(seat);
            if (slot < 0 || slot >= into.capacity()) {
                continue;
            }
            UpgradeKind picked = picks[seat];
            if (picked != null) {
                into.pick(slot, pickSerials[seat], picked);
                offered[seat] = true;
            }
            PickupKind taken = crateTakes[seat];
            if (taken != null) {
                into.takeFromCrate(slot, taken);
                offered[seat] = true;
            } else if (crateCloses[seat]) {
                into.closeCrate(slot);
                offered[seat] = true;
            }
        }
    }

    /** Forgets what the last {@link #offer} handed over: the tick or the session it went to took it. */
    void taken() {
        for (int seat = 0; seat < offered.length; seat++) {
            if (offered[seat]) {
                picks[seat] = null;
                crateTakes[seat] = null;
                crateCloses[seat] = false;
                offered[seat] = false;
            }
        }
    }

    /** Forgets everything held: the world it was meant for is gone. */
    void clear() {
        Arrays.fill(picks, null);
        Arrays.fill(crateTakes, null);
        Arrays.fill(crateCloses, false);
        Arrays.fill(offered, false);
    }
}
