package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * A local player's one-off input events wait here until something takes them, and are offered again until
 * then - a frame may run no tick, and an online session takes nothing until it plays.
 */
class HeldEventsTest {

    private static final int SEAT = 1;

    private static final int SERIAL = 3;

    private static final IntUnaryOperator SAME = IntUnaryOperator.identity();

    private final HeldEvents held = new HeldEvents();

    private final PlayerInputs into = new PlayerInputs(Players.MAX);

    @Test
    void offersAPickUntilSomethingTakesIt() {
        held.pick(SEAT, SERIAL, UpgradeKind.MARKSMEN);

        held.offer(into, SAME);
        PlayerInputs again = new PlayerInputs(Players.MAX);
        held.offer(again, SAME);
        held.taken();
        PlayerInputs after = new PlayerInputs(Players.MAX);
        held.offer(after, SAME);

        assertThat(into.pickOf(SEAT)).isEqualTo(UpgradeKind.MARKSMEN);
        assertThat(into.pickSerialOf(SEAT)).isEqualTo(SERIAL);
        assertThat(again.pickOf(SEAT)).as("not taken yet, so offered again").isEqualTo(UpgradeKind.MARKSMEN);
        assertThat(after.pickOf(SEAT)).as("taken once, so gone").isNull();
    }

    @Test
    void offersAnEventAtTheSlotItsSeatPlays() {
        held.crate(SEAT, PickupKind.MUSHROOM);

        held.offer(into, seat -> seat + 4);

        assertThat(into.crateTakeOf(SEAT + 4)).isEqualTo(PickupKind.MUSHROOM);
        assertThat(into.crateTakeOf(SEAT)).isNull();
    }

    @Test
    void keepsTheEventsOfASeatWithNoSlotUntilItHasOne() {
        held.pick(SEAT, SERIAL, UpgradeKind.MARKSMEN);

        held.offer(into, seat -> -1);
        held.taken();
        held.offer(into, SAME);

        assertThat(into.pickOf(SEAT)).isEqualTo(UpgradeKind.MARKSMEN);
    }

    @Test
    void letsTheLatestCrateChoiceStandWhetherATakeOrAShut() {
        held.crate(SEAT, PickupKind.MUSHROOM);
        held.crate(SEAT, null);
        held.crate(0, null);
        held.crate(0, PickupKind.MUSHROOM);

        held.offer(into, SAME);

        assertThat(into.closesCrate(SEAT)).isTrue();
        assertThat(into.crateTakeOf(SEAT)).isNull();
        assertThat(into.closesCrate(0)).isFalse();
        assertThat(into.crateTakeOf(0)).isEqualTo(PickupKind.MUSHROOM);
    }

    @Test
    void forgetsEverythingHeldWhenCleared() {
        held.pick(SEAT, SERIAL, UpgradeKind.MARKSMEN);
        held.crate(0, null);

        held.clear();
        held.offer(into, SAME);

        assertThat(into.pickOf(SEAT)).isNull();
        assertThat(into.closesCrate(0)).isFalse();
    }

    @Test
    void ignoresASeatThatIsNotOne() {
        held.pick(PlayerSeats.SEATS, SERIAL, UpgradeKind.MARKSMEN);
        held.crate(-1, null);

        held.offer(into, SAME);

        for (int slot = 0; slot < Players.MAX; slot++) {
            assertThat(into.pickOf(slot)).isNull();
            assertThat(into.closesCrate(slot)).isFalse();
        }
    }
}
