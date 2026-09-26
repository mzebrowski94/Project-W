package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PanelAssignmentTest {

    private static final int[] KEYBOARD_ONLY = {0, -1, -1, -1};

    @Test
    void givesEveryPlayerAPanelWhileThereAreNoMoreThanFour() {
        Readout readout = playing(0, 3, 5);
        PanelAssignment panels = new PanelAssignment();

        panels.update(readout, KEYBOARD_ONLY);

        assertThat(quarters(panels)).containsExactly(0, 3, 5, -1);
        assertThat(panels.everybody()).isTrue();
    }

    @Test
    void putsThisMachinesPlayersFirst() {
        Readout readout = playing(1, 4, 6);
        PanelAssignment panels = new PanelAssignment();

        panels.update(readout, new int[] {6, -1, -1, -1});

        assertThat(panels.slotIn(0)).isEqualTo(6);
    }

    @Test
    void keepsEveryPanelWhereItWasWhenAPlayerJoins() {
        Readout readout = playing(0, 5);
        PanelAssignment panels = new PanelAssignment();
        panels.update(readout, KEYBOARD_ONLY);

        readout.player(2).posture = Posture.STANDING;
        panels.update(readout, KEYBOARD_ONLY);

        assertThat(quarters(panels)).containsExactly(0, 5, 2, -1);
    }

    @Test
    void freesAQuarterWhenAPlayerIsGoneAndLetsTheNextOneHaveIt() {
        Readout readout = playing(0, 3, 5);
        PanelAssignment panels = new PanelAssignment();
        panels.update(readout, KEYBOARD_ONLY);

        readout.player(3).posture = Posture.ABSENT;
        panels.update(readout, KEYBOARD_ONLY);
        readout.player(7).posture = Posture.STANDING;
        panels.update(readout, KEYBOARD_ONLY);

        assertThat(quarters(panels)).containsExactly(0, 7, 5, -1);
    }

    @Test
    void keepsAPanelForAPlayerWaitingToComeBack() {
        Readout readout = playing(0, 3);
        PanelAssignment panels = new PanelAssignment();
        panels.update(readout, KEYBOARD_ONLY);

        readout.player(3).posture = Posture.RESPAWNING;
        panels.update(readout, KEYBOARD_ONLY);

        assertThat(panels.slotIn(1)).isEqualTo(3);
    }

    @Test
    void showsOnlyThisMachinesSeatsWithMoreThanFourPlayers() {
        Readout readout = playing(0, 1, 2, 3, 4);
        PanelAssignment panels = new PanelAssignment();

        panels.update(readout, new int[] {2, -1, 4, -1});

        assertThat(quarters(panels)).containsExactly(2, -1, 4, -1);
        assertThat(panels.everybody()).isFalse();
    }

    @Test
    void seatsEverybodyAgainInTheSameOrderOnANewWorld() {
        Readout readout = playing(0, 5);
        PanelAssignment panels = new PanelAssignment();
        panels.update(readout, KEYBOARD_ONLY);
        readout.player(2).posture = Posture.STANDING;
        panels.update(readout, KEYBOARD_ONLY);

        readout.worldSerial++;
        panels.update(readout, KEYBOARD_ONLY);

        assertThat(quarters(panels)).containsExactly(0, 2, 5, -1);
    }

    private static int[] quarters(PanelAssignment panels) {
        int[] slots = new int[PanelLayout.QUARTERS];
        for (int quarter = 0; quarter < slots.length; quarter++) {
            slots[quarter] = panels.slotIn(quarter);
        }
        return slots;
    }

    private static Readout playing(int... slots) {
        Readout readout = new Readout();
        for (int slot : slots) {
            readout.player(slot).posture = Posture.STANDING;
        }
        return readout;
    }
}
