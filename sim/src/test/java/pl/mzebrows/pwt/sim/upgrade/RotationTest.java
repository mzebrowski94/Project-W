package pl.mzebrows.pwt.sim.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Whose turn a draft is: the next present slot, in slot order, wrapping round (G0049). */
class RotationTest {

    private static final boolean[] NOBODY_AWAY = new boolean[4];

    @Test
    void startsWithTheLowestSlotBeforeAnybodyHasChosen() {
        assertThat(Rotation.next(-1, seated(0, 1, 2), NOBODY_AWAY)).isZero();
    }

    @Test
    void goesToTheNextSlotWithACommanderAndWrapsRound() {
        boolean[] seated = seated(0, 2, 3);

        assertThat(Rotation.next(0, seated, NOBODY_AWAY)).isEqualTo(2);
        assertThat(Rotation.next(2, seated, NOBODY_AWAY)).isEqualTo(3);
        assertThat(Rotation.next(3, seated, NOBODY_AWAY)).isZero();
    }

    @Test
    void skipsASlotThatIsAway() {
        assertThat(Rotation.next(0, seated(0, 1, 2), away(1))).isEqualTo(2);
    }

    @Test
    void comesBackToTheSameManWhenHeIsTheOnlyOnePresent() {
        assertThat(Rotation.next(0, seated(0, 1), away(1))).isZero();
    }

    /** Its drafts pass on the moment somebody returns. */
    @Test
    void goesToTheLowestSeatedSlotWhenNobodyIsPresent() {
        assertThat(Rotation.next(0, seated(1, 3), away(1, 3))).isEqualTo(1);
    }

    private static boolean[] seated(int... slots) {
        boolean[] seated = new boolean[4];
        for (int slot : slots) {
            seated[slot] = true;
        }
        return seated;
    }

    private static boolean[] away(int... slots) {
        return seated(slots);
    }
}
