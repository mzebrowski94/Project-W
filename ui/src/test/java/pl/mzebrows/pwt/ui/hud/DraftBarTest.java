package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The draft in the top bar (D18): one per machine, driven by the seat that opened it, and true to the
 * world - a draft that goes gives way to the next, and taking a card closes it.
 */
class DraftBarTest {

    private static final int SERIAL = 7;

    private final DraftBar bar = new DraftBar();

    @Test
    void opensTheOldestDraftOfTheSeatsSlot() {
        Readout readout = drafted(2, SERIAL, 2, UpgradeKind.MARKSMEN, UpgradeKind.GRENADIER, UpgradeKind.FIRST_AID);

        bar.press(1, 2, DraftBar.Key.DRAFT, readout);

        assertThat(bar.open()).isTrue();
        assertThat(bar.seat()).isEqualTo(1);
        assertThat(bar.slot()).isEqualTo(2);
        assertThat(bar.serial()).isEqualTo(SERIAL);
        assertThat(bar.highlighted()).isZero();
    }

    @Test
    void opensNothingForASlotWithoutADraft() {
        Readout readout = new Readout();

        bar.press(0, 0, DraftBar.Key.DRAFT, readout);
        bar.press(0, 0, DraftBar.Key.CARD_2, readout);

        assertThat(bar.open()).isFalse();
    }

    @Test
    void takesTheHighlightedCardAndCloses() {
        Readout readout = drafted(0, SERIAL, 1, UpgradeKind.MARKSMEN, UpgradeKind.GRENADIER, UpgradeKind.FIRST_AID);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        bar.press(0, 0, DraftBar.Key.CARD_3, readout);
        boolean took = bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        assertThat(took).isTrue();
        assertThat(bar.takenSerial()).isEqualTo(SERIAL);
        assertThat(bar.takenKind()).isEqualTo(UpgradeKind.FIRST_AID);
        assertThat(bar.open()).isFalse();
    }

    @Test
    void movesTheHighlightRoundTheCardsBothWays() {
        Readout readout = drafted(0, SERIAL, 1, UpgradeKind.MARKSMEN, UpgradeKind.GRENADIER, UpgradeKind.FIRST_AID);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        bar.press(0, 0, DraftBar.Key.PREVIOUS, readout);
        int wrappedBack = bar.highlighted();
        bar.press(0, 0, DraftBar.Key.NEXT, readout);

        assertThat(wrappedBack).isEqualTo(2);
        assertThat(bar.highlighted()).isZero();
    }

    @Test
    void ignoresAPlaceWithNoCardOnIt() {
        Readout readout = drafted(0, SERIAL, 1, UpgradeKind.MARKSMEN, UpgradeKind.GRENADIER);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        bar.press(0, 0, DraftBar.Key.CARD_3, readout);

        assertThat(bar.highlighted()).isZero();
    }

    @Test
    void closesAndKeepsTheDraft() {
        Readout readout = drafted(0, SERIAL, 1, UpgradeKind.MARKSMEN);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        boolean took = bar.press(0, 0, DraftBar.Key.CLOSE, readout);

        assertThat(took).isFalse();
        assertThat(bar.open()).isFalse();
    }

    /** A machine's top bar shows one draft at a time; another seat's keys change nothing while it is taken. */
    @Test
    void belongsToTheSeatThatOpenedIt() {
        Readout readout = drafted(0, SERIAL, 1, UpgradeKind.MARKSMEN, UpgradeKind.GRENADIER);
        fill(readout, 1, SERIAL + 1, 1, UpgradeKind.FIRST_AID);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        boolean took = bar.press(1, 1, DraftBar.Key.DRAFT, readout);
        bar.press(1, 1, DraftBar.Key.CARD_2, readout);
        bar.press(1, 1, DraftBar.Key.CLOSE, readout);

        assertThat(took).isFalse();
        assertThat(bar.slot()).isZero();
        assertThat(bar.highlighted()).isZero();
        assertThat(bar.open()).isTrue();
    }

    @Test
    void followsTheNextDraftWhenTheOneOnShowIsGone() {
        Readout readout = drafted(0, SERIAL, 2, UpgradeKind.MARKSMEN, UpgradeKind.GRENADIER);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);
        bar.press(0, 0, DraftBar.Key.CARD_2, readout);

        fill(readout, 0, SERIAL + 3, 1, UpgradeKind.FIRST_AID);
        bar.follow(readout);

        assertThat(bar.serial()).isEqualTo(SERIAL + 3);
        assertThat(bar.highlighted()).isZero();
    }

    @Test
    void closesWhenHisLastDraftIsGone() {
        Readout readout = drafted(0, SERIAL, 1, UpgradeKind.MARKSMEN);
        bar.press(0, 0, DraftBar.Key.DRAFT, readout);

        readout.player(0).drafts = 0;
        readout.player(0).cardCount = 0;
        bar.follow(readout);

        assertThat(bar.open()).isFalse();
    }

    private static Readout drafted(int slot, int serial, int drafts, UpgradeKind... cards) {
        Readout readout = new Readout();
        fill(readout, slot, serial, drafts, cards);
        return readout;
    }

    private static void fill(Readout readout, int slot, int serial, int drafts, UpgradeKind... cards) {
        PlayerReadout player = readout.player(slot);
        player.drafts = drafts;
        player.draftSerial = serial;
        player.cardCount = cards.length;
        for (int i = 0; i < cards.length; i++) {
            player.cards[i] = cards[i];
            player.cardRanks[i] = 1;
        }
    }
}
