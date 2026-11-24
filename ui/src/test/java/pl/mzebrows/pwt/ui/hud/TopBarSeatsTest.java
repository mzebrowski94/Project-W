package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * One machine's top bar and the seats that share it (G0049, G0056): which seat drives it, what its keys
 * mean while it does, whose inputs it takes, and the pause a solo player's draft makes.
 */
class TopBarSeatsTest {

    private static final int SEATS = 4;

    private static final int PAD = 1;

    private static final int SERIAL = 7;

    private final TopBarSeats seats = new TopBarSeats(SEATS);

    private final FakePlay play = new FakePlay();

    private final Readout readout = new Readout();

    @Test
    void opensADraftOnItsSeatsKeyAndTakesTheCardItsOrdersChoose() {
        drafts(0, UpgradeKind.MARKSMEN, UpgradeKind.FIELD_MEDICINE, UpgradeKind.LARGER_ARMY);

        frame(draftPressed(0), inputs());
        frame(nothingPressed(), inputs().order(0, Order.FORWARD));
        frame(draftPressed(0), inputs());

        assertThat(play.picks).containsExactly("0:" + SERIAL + ":" + UpgradeKind.FIELD_MEDICINE);
        assertThat(seats.draft().open()).isFalse();
    }

    /**
     * With player one on a pad seat zero's orders are a d-pad's: the cancel order moves the highlight rather than
     * closing the draft as the keyboard's fourth key does.
     */
    @Test
    void readsSeatZerosOrdersAsAPadsWhenPlayerOneIsOnOne() {
        seats.keyboardIn(-1);
        drafts(0, UpgradeKind.MARKSMEN, UpgradeKind.FIELD_MEDICINE, UpgradeKind.LARGER_ARMY);

        frame(draftPressed(0), inputs());
        frame(nothingPressed(), inputs().order(0, Order.CANCEL));

        assertThat(seats.draft().open()).isTrue();
    }

    @Test
    void givesNoOrderFromTheSeatDrivingTheDraftAndLeavesEveryOtherSeatAlone() {
        drafts(0, UpgradeKind.MARKSMEN);
        frame(draftPressed(0), inputs());

        PlayerInputs players = inputs().order(0, Order.DEFENCE).order(PAD, Order.DEFENCE);
        frame(nothingPressed(), players);

        assertThat(players.orderOf(0)).isNull();
        assertThat(players.orderOf(PAD)).isEqualTo(Order.DEFENCE);
    }

    @Test
    void opensNoDraftWhileACrateHasTheBar() {
        crate(PAD, PickupKind.MEDKIT);
        drafts(0, UpgradeKind.MARKSMEN);
        follow();

        frame(draftPressed(0), inputs());

        assertThat(seats.crate().seat()).isEqualTo(PAD);
        assertThat(seats.draft().open()).isFalse();
    }

    @Test
    void takesTheHighlightedCrateCardOnceForOnePressOfTheActionKey() {
        crate(0, PickupKind.MEDKIT, PickupKind.ARMOURY_FLAMER);
        follow();

        frame(nothingPressed(), inputs().set(0, acting(true)));
        follow();
        frame(nothingPressed(), inputs().set(0, acting(true)));

        assertThat(play.crates).containsExactly("0:" + PickupKind.MEDKIT);
    }

    @Test
    void shutsTheCrateOnTheKeyboardsKeyForIt() {
        crate(0, PickupKind.MEDKIT);
        follow();

        seats.route(inputs(), nothingPressed(), true, false);
        seats.work(inputs(), readout, false, play);

        assertThat(play.crates).containsExactly("0:null");
    }

    @Test
    void keepsAnotherSeatFromOpeningACrateWhileTheBarIsTaken() {
        crate(0, PickupKind.MEDKIT);
        follow();
        readout.player(PAD).action = ActionKind.OPEN_CRATE;

        PlayerInputs players = inputs().set(PAD, acting(true));
        frame(nothingPressed(), players);

        assertThat(players.get(PAD).acting()).isFalse();
    }

    @Test
    void shutsASecondLocalSeatsCrateSoAMachineHoldsOne() {
        crate(0, PickupKind.MEDKIT);
        crate(PAD, PickupKind.ARMOURY_LASER);

        follow();

        assertThat(seats.crate().seat()).isZero();
        assertThat(play.crates).containsExactly(PAD + ":null");
    }

    @Test
    void pausesASoloGameForADraftAndResumesWhenItCloses() {
        readout.player(0).posture = Posture.STANDING;
        drafts(0, UpgradeKind.MARKSMEN);

        frame(draftPressed(0), inputs());
        boolean pausedWhileOpen = play.paused;
        frame(nothingPressed(), inputs().order(0, Order.CANCEL));

        assertThat(pausedWhileOpen).isTrue();
        assertThat(play.paused).isFalse();
        assertThat(seats.quietPause(play)).isFalse();
    }

    @Test
    void holdsEverySeatNeutralAndClosesTheBarsWhileAMenuIsOpen() {
        drafts(0, UpgradeKind.MARKSMEN);
        frame(draftPressed(0), inputs());
        PlayerInputs players = inputs().set(PAD, new CommanderInput(1f, -1f, 3f, 4f, true, true).acting(true))
                .order(PAD, Order.FORWARD);

        seats.route(players, draftPressed(PAD), false, true);
        seats.work(players, readout, true, play);

        CommanderInput held = players.get(PAD);
        assertThat(held.moveX()).isZero();
        assertThat(held.firing()).isFalse();
        assertThat(held.acting()).isFalse();
        assertThat(held.aimX()).isEqualTo(3f);
        assertThat(players.orderOf(PAD)).isNull();
        assertThat(seats.draft().open()).isFalse();
    }

    @Test
    void leavesAPadsCrateAloneWhenTheKeyboardsCrateKeyGoesDown() {
        crate(PAD, PickupKind.MEDKIT);
        follow();

        seats.route(inputs(), nothingPressed(), true, false);
        seats.work(inputs(), readout, false, play);

        assertThat(play.crates).isEmpty();
        assertThat(seats.crate().seat()).isEqualTo(PAD);
    }

    /** The draft key outranks an order given on the same frame: it takes the card on show. */
    @Test
    void takesTheCardWhenTheDraftKeyAndAnOrderComeTogether() {
        drafts(0, UpgradeKind.MARKSMEN, UpgradeKind.FIELD_MEDICINE);
        frame(draftPressed(0), inputs());

        frame(draftPressed(0), inputs().order(0, Order.FORWARD));

        assertThat(play.picks).containsExactly("0:" + SERIAL + ":" + UpgradeKind.MARKSMEN);
    }

    /** A menu takes the bar without shutting the crate in the world, and the bar comes back when it goes. */
    @Test
    void bringsACrateBackWhenTheMenuOverItCloses() {
        crate(0, PickupKind.MEDKIT);
        follow();

        seats.work(inputs(), readout, true, play);
        boolean shownUnderMenu = seats.crate().open();
        follow();

        assertThat(shownUnderMenu).isFalse();
        assertThat(play.crates).isEmpty();
        assertThat(seats.crate().seat()).isZero();
    }

    /** One frame as the screen runs it: route the input, then work the bars with it. */
    private void frame(boolean[] draftPressed, PlayerInputs players) {
        seats.route(players, draftPressed, false, false);
        seats.work(players, readout, false, play);
    }

    private void follow() {
        seats.follow(readout, false, play);
    }

    private static PlayerInputs inputs() {
        return new PlayerInputs(SEATS);
    }

    private static CommanderInput acting(boolean held) {
        return new CommanderInput(0f, 0f, 0f, 0f, false, false).acting(held);
    }

    private static boolean[] nothingPressed() {
        return new boolean[SEATS];
    }

    private static boolean[] draftPressed(int seat) {
        boolean[] pressed = new boolean[SEATS];
        pressed[seat] = true;
        return pressed;
    }

    private void drafts(int slot, UpgradeKind... cards) {
        PlayerReadout player = readout.player(slot);
        player.drafts = 1;
        player.draftSerial = SERIAL;
        player.cardCount = cards.length;
        for (int i = 0; i < cards.length; i++) {
            player.cards[i] = cards[i];
            player.cardRanks[i] = 1;
        }
    }

    private void crate(int slot, PickupKind... kinds) {
        PlayerReadout player = readout.player(slot);
        player.crateCount = kinds.length;
        for (int i = 0; i < kinds.length; i++) {
            player.crate[i] = kinds[i];
        }
    }

    /** Offline in miniature: every seat plays the slot of its own number, and a pause lands at once. */
    private static final class FakePlay implements SeatedPlay {

        final List<String> picks = new ArrayList<>();

        final List<String> crates = new ArrayList<>();

        boolean paused;

        @Override
        public int slotOf(int seat) {
            return seat;
        }

        @Override
        public void pick(int seat, int serial, UpgradeKind kind) {
            picks.add(seat + ":" + serial + ":" + kind);
        }

        @Override
        public void crate(int seat, @Nullable PickupKind take) {
            crates.add(seat + ":" + take);
        }

        @Override
        public boolean paused() {
            return paused;
        }

        @Override
        public boolean pausedForDraft() {
            return false;
        }

        @Override
        public void setDraftPaused(boolean on) {
            paused = on;
        }
    }
}
