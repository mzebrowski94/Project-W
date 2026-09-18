package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Who is sitting where. Plugging a pad in is the whole of joining the game, and unplugging one is
 * not leaving it - which is the rule most of these defend.
 */
class PlayerSeatsTest {

    @Test
    void leavesTheFirstSlotToTheKeyboard() {
        PlayerSeats seats = new PlayerSeats();
        FakeGamepads pads = new FakeGamepads();
        pads.pluggedIn(FakeGamepad.xbox());

        seats.refresh(pads);

        assertThat(seats.padIn(PlayerSeats.KEYBOARD_SLOT)).isNull();
        assertThat(seats.padIn(1)).isNotNull();
    }

    @Test
    void seatsEachNewPadInTheLowestFreeSlot() {
        PlayerSeats seats = new PlayerSeats();
        FakeGamepads pads = new FakeGamepads();

        pads.pluggedIn(new FakeGamepad("a", "Pad A"), new FakeGamepad("b", "Pad B"));
        seats.refresh(pads);

        assertThat(seats.padIn(1).id()).isEqualTo("a");
        assertThat(seats.padIn(2).id()).isEqualTo("b");
    }

    @Test
    void reportsEachNewPlayerOnceSoHeIsGivenOneCommander() {
        PlayerSeats seats = new PlayerSeats();
        FakeGamepads pads = new FakeGamepads();
        pads.pluggedIn(new FakeGamepad("a", "Pad A"));

        seats.refresh(pads);

        assertThat(seats.nextNewlySeated()).isEqualTo(1);
        assertThat(seats.nextNewlySeated()).isEqualTo(-1);
    }

    /** The commander stays on the field, so the seat has to stay his - the cable is usually back. */
    @Test
    void keepsASeatForAPadThatHasBeenUnplugged() {
        PlayerSeats seats = new PlayerSeats();
        FakeGamepads pads = new FakeGamepads();
        pads.pluggedIn(new FakeGamepad("a", "Pad A"));
        seats.refresh(pads);

        pads.pluggedIn();
        seats.refresh(pads);

        assertThat(seats.taken(1)).isTrue();
        assertThat(seats.padIn(1)).isNull();
    }

    @Test
    void putsAReconnectedPadBackInTheSeatItLeft() {
        PlayerSeats seats = new PlayerSeats();
        FakeGamepads pads = new FakeGamepads();
        FakeGamepad first = new FakeGamepad("a", "Pad A");
        pads.pluggedIn(first, new FakeGamepad("b", "Pad B"));
        seats.refresh(pads);
        seats.nextNewlySeated();
        seats.nextNewlySeated();

        pads.pluggedIn(new FakeGamepad("b", "Pad B"));
        seats.refresh(pads);
        pads.pluggedIn(new FakeGamepad("b", "Pad B"), first);
        seats.refresh(pads);

        assertThat(seats.padIn(1).id()).isEqualTo("a");
        assertThat(seats.nextNewlySeated()).isEqualTo(-1);
    }

    /** Four players, one of whom is the keyboard: a fourth pad has nowhere to sit. */
    @Test
    void turnsAwayAPadWhenEverySlotIsTaken() {
        PlayerSeats seats = new PlayerSeats();
        FakeGamepads pads = new FakeGamepads();

        pads.pluggedIn(
                new FakeGamepad("a", "Pad A"),
                new FakeGamepad("b", "Pad B"),
                new FakeGamepad("c", "Pad C"),
                new FakeGamepad("d", "Pad D"));
        seats.refresh(pads);

        assertThat(seats.padIn(3)).isNotNull();
        assertThat(seats.padIn(3).id()).isEqualTo("c");
    }
}
