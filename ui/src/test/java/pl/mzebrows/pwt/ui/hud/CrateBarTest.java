package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * The crate in the top bar (G0056): it follows the world - the first local seat with a crate open gets the
 * bar, a second is named for shutting - only its own seat works it, and what it sent is remembered until
 * the world has answered.
 */
class CrateBarTest {

    /** Keyboard in slot 0, a pad in slot 3, nobody else. */
    private static final int[] SEATS = {0, 3, -1, -1};

    private final CrateBar bar = new CrateBar();

    @Test
    void showsTheCrateOfTheFirstLocalSeatThatHasOneOpen() {
        Readout readout = opened(3, PickupKind.ARMOURY_FLAMER, PickupKind.MEDKIT);

        bar.follow(readout, SEATS, false);

        assertThat(bar.open()).isTrue();
        assertThat(bar.seat()).isEqualTo(1);
        assertThat(bar.slot()).isEqualTo(3);
        assertThat(bar.highlighted()).isZero();
    }

    /** One bar per machine: a draft has it, so the crate waits - and is named for shutting. */
    @Test
    void leavesTheBarToADraftAndNamesTheCrateToShut() {
        Readout readout = opened(0, PickupKind.ARMOURY_LASER);

        bar.follow(readout, SEATS, true);

        assertThat(bar.open()).isFalse();
        assertThat(bar.intruder(readout, SEATS, true)).isZero();
    }

    @Test
    void namesASecondLocalSeatWithACrateOpenForShutting() {
        Readout readout = opened(0, PickupKind.ARMOURY_LASER);
        fill(readout, 3, PickupKind.MEDKIT);

        bar.follow(readout, SEATS, false);

        assertThat(bar.seat()).isZero();
        assertThat(bar.intruder(readout, SEATS, false)).isEqualTo(1);
    }

    @Test
    void takesTheHighlightedCardAndCloses() {
        Readout readout = opened(
                0,
                PickupKind.ARMOURY_FLAMER,
                PickupKind.ARMOURY_SHOTGUN,
                PickupKind.ARMOURY_LASER,
                PickupKind.ARMOURY_RAILGUN);
        bar.follow(readout, SEATS, false);

        bar.press(0, CrateBar.Key.CARD_4, readout);
        CrateBar.Outcome outcome = bar.press(0, CrateBar.Key.TAKE, readout);

        assertThat(outcome).isEqualTo(CrateBar.Outcome.TAKE);
        assertThat(bar.taken()).isEqualTo(PickupKind.ARMOURY_RAILGUN);
        assertThat(bar.open()).isFalse();
    }

    /** A card he would be turned down for - a medkit to a man with no wound - takes nothing (G0053). */
    @Test
    void takesNothingHeWouldBeTurnedDownFor() {
        Readout readout = opened(0, PickupKind.MEDKIT, PickupKind.ARMOURY_FLAMER);
        readout.player(0).crateRefused[0] = true;
        bar.follow(readout, SEATS, false);

        CrateBar.Outcome outcome = bar.press(0, CrateBar.Key.TAKE, readout);

        assertThat(outcome).isEqualTo(CrateBar.Outcome.NONE);
        assertThat(bar.open()).isTrue();
    }

    @Test
    void movesTheHighlightRoundTheCardsBothWays() {
        Readout readout = opened(0, PickupKind.MEDKIT, PickupKind.ARMOURY_FLAMER, PickupKind.ARMOURY_LASER);
        bar.follow(readout, SEATS, false);

        bar.press(0, CrateBar.Key.PREVIOUS, readout);
        assertThat(bar.highlighted()).isEqualTo(2);

        bar.press(0, CrateBar.Key.NEXT, readout);
        assertThat(bar.highlighted()).isZero();
    }

    @Test
    void belongsToTheSeatWhoseCrateItIs() {
        Readout readout = opened(0, PickupKind.MEDKIT, PickupKind.ARMOURY_FLAMER);
        bar.follow(readout, SEATS, false);

        CrateBar.Outcome outcome = bar.press(1, CrateBar.Key.CLOSE, readout);

        assertThat(outcome).isEqualTo(CrateBar.Outcome.NONE);
        assertThat(bar.open()).isTrue();
    }

    /** Shut here, still open in the world until the next tick: the bar does not open on it again meanwhile. */
    @Test
    void doesNotShowACrateItHasShutUntilTheWorldHasShutItToo() {
        Readout readout = opened(0, PickupKind.MEDKIT, PickupKind.ARMOURY_FLAMER);
        bar.follow(readout, SEATS, false);

        assertThat(bar.press(0, CrateBar.Key.CLOSE, readout)).isEqualTo(CrateBar.Outcome.CLOSE);
        bar.follow(readout, SEATS, false);
        assertThat(bar.open()).isFalse();

        bar.follow(new Readout(), SEATS, false);
        bar.follow(readout, SEATS, false);
        assertThat(bar.open()).isTrue();
    }

    @Test
    void closesWhenTheWorldShutsTheCrate() {
        bar.follow(opened(0, PickupKind.MEDKIT), SEATS, false);

        bar.follow(new Readout(), SEATS, false);

        assertThat(bar.open()).isFalse();
    }

    private static Readout opened(int slot, PickupKind... kinds) {
        Readout readout = new Readout();
        fill(readout, slot, kinds);
        return readout;
    }

    private static void fill(Readout readout, int slot, PickupKind... kinds) {
        PlayerReadout player = readout.player(slot);
        player.crateCount = kinds.length;
        for (int i = 0; i < kinds.length; i++) {
            player.crate[i] = kinds[i];
        }
    }
}
