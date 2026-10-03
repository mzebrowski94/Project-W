package pl.mzebrows.pwt.ui.hud;

import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * What {@link TopBarSeats} asks of the game it is played in, and hands to it: which slot each local seat
 * plays, the picks and crate choices it sends, and the pause a solo player's draft makes. The client's play,
 * offline or online, is one; ui never sees which.
 */
public interface SeatedPlay {

    /** The slot a local seat plays in, or -1 if it has none. */
    int slotOf(int seat);

    /** Takes a card from the draft bar for the slot this seat plays, from the next tick (G0049). */
    void pick(int seat, int serial, UpgradeKind kind);

    /** Takes a pickup of this kind from the crate this seat's slot has open, or - for null - shuts it (G0056). */
    void crate(int seat, @Nullable PickupKind take);

    boolean paused();

    /** Whether the pause in force is for a solo player's draft, asked for on whichever machine. */
    boolean pausedForDraft();

    /** Asks for the pause a solo player's draft makes to be on or off. */
    void setDraftPaused(boolean on);
}
