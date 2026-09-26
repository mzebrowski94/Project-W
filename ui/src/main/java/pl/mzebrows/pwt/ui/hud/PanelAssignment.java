package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.Players;

/**
 * Whose panel is in which quarter of the bottom bar.
 *
 * <p>While there are no more players in the game than quarters - local and online alike - every one of
 * them has a panel: this machine's seats first, in seat order, then everybody else in slot order, so the
 * keyboard player is on the left. A player keeps his quarter for as long as he is in the game; one who
 * joins takes the lowest free quarter and nobody else moves (G0028: plugging a pad in is joining, and can
 * happen mid-fight). With more players than quarters the bar shows this machine's seats only, each in
 * its own seat's quarter; the compact view of everybody is the roll call.
 *
 * <p>A new world starts the seating again, in the same order, so a restart puts everybody back where he
 * was.
 */
public final class PanelAssignment {

    private final int[] slotInQuarter = new int[PanelLayout.QUARTERS];
    private final boolean[] placed = new boolean[Players.MAX];
    private long worldSerial = Long.MIN_VALUE;
    private boolean everybody;

    public PanelAssignment() {
        Arrays.fill(slotInQuarter, -1);
    }

    /**
     * Follows the readout. Call once a frame.
     *
     * @param slotOfSeat the slot each of this machine's seats plays, or -1; {@link PanelLayout#QUARTERS}
     *     long
     */
    public void update(Readout readout, int[] slotOfSeat) {
        int present = 0;
        for (int slot = 0; slot < Players.MAX; slot++) {
            if (readout.player(slot).posture != Posture.ABSENT) {
                present++;
            }
        }
        boolean fits = present <= PanelLayout.QUARTERS;
        if (fits != everybody || readout.worldSerial != worldSerial) {
            Arrays.fill(slotInQuarter, -1);
            everybody = fits;
            worldSerial = readout.worldSerial;
        }
        if (!everybody) {
            for (int seat = 0; seat < PanelLayout.QUARTERS; seat++) {
                int slot = slotOfSeat[seat];
                slotInQuarter[seat] = inGame(readout, slot) ? slot : -1;
            }
            return;
        }
        Arrays.fill(placed, false);
        for (int quarter = 0; quarter < PanelLayout.QUARTERS; quarter++) {
            int slot = slotInQuarter[quarter];
            if (!inGame(readout, slot)) {
                slotInQuarter[quarter] = -1;
            } else {
                placed[slot] = true;
            }
        }
        for (int seat = 0; seat < PanelLayout.QUARTERS; seat++) {
            seat(readout, slotOfSeat[seat]);
        }
        for (int slot = 0; slot < Players.MAX; slot++) {
            seat(readout, slot);
        }
    }

    /** The slot whose panel is in this quarter, or -1 for a black one. */
    public int slotIn(int quarter) {
        return slotInQuarter[quarter];
    }

    /** Whether every player in the game has a panel, rather than only this machine's. */
    public boolean everybody() {
        return everybody;
    }

    private void seat(Readout readout, int slot) {
        if (!inGame(readout, slot) || placed[slot]) {
            return;
        }
        for (int quarter = 0; quarter < PanelLayout.QUARTERS; quarter++) {
            if (slotInQuarter[quarter] < 0) {
                slotInQuarter[quarter] = slot;
                placed[slot] = true;
                return;
            }
        }
    }

    private static boolean inGame(Readout readout, int slot) {
        return slot >= 0 && slot < Players.MAX && readout.player(slot).posture != Posture.ABSENT;
    }
}
