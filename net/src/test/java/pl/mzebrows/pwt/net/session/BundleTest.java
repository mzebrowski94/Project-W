package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.def.Order;

class BundleTest {

    private final WireWriter out = new WireWriter(64);
    private final WireReader in = new WireReader();

    @Test
    void carriesHeldInputToTheBit() {
        CommanderInput odd = new CommanderInput(-0f, Float.MIN_VALUE, Float.MAX_VALUE, 0.1f + 0.2f, true, false)
                .acting(true)
                .aimingRelatively();

        Bundle.writeHeld(out.reset(), odd);
        CommanderInput read = Bundle.readHeld(in.wrap(out.array(), 0, out.size()), CommanderInput.IDLE);

        assertThat(read).isEqualTo(odd);
        assertThat(Float.floatToRawIntBits(read.moveX())).isEqualTo(Float.floatToRawIntBits(-0f));
        assertThat(out.size()).isEqualTo(Bundle.HELD_BYTES);
    }

    @Test
    void turnsANumberThatIsNotFiniteIntoZero() {
        Bundle.writeHeld(out.reset(), new CommanderInput(Float.NaN, Float.POSITIVE_INFINITY, 3f, 4f, false, false));

        CommanderInput read = Bundle.readHeld(in.wrap(out.array(), 0, out.size()), CommanderInput.IDLE);

        assertThat(read.moveX()).isZero();
        assertThat(read.moveY()).isZero();
        assertThat(read.aimX()).isEqualTo(3f);
    }

    @Test
    void handsBackTheSameInputWhenNothingHasChanged() {
        CommanderInput steady = new CommanderInput(0.5f, 0.25f, 10f, 20f, true, false);
        Bundle.writeHeld(out.reset(), new CommanderInput(0.5f, 0.25f, 10f, 20f, true, false));

        assertThat(Bundle.readHeld(in.wrap(out.array(), 0, out.size()), steady)).isSameAs(steady);
    }

    @Test
    void carriesATickWholeAndHandsItToAWorld() {
        Bundle sent = new Bundle(8).clear(42);
        CommanderInput moving = new CommanderInput(1f, 0f, 5f, 5f, true, false);
        sent.hold(0, moving)
                .hold(5, CommanderInput.IDLE)
                .order(5, Order.DEFENCE)
                .join(7)
                .restarting();
        sent.switchInvulnerability(true);
        sent.write(out.reset());
        int length = out.size();

        assertThat(Bundle.skip(in.wrap(out.array(), 0, length), 8)).isEqualTo(42);
        assertThat(in.remaining()).isZero();
        Bundle received = new Bundle(8).read(in.wrap(out.array(), 0, length));
        PlayerInputs inputs = new PlayerInputs(8);
        received.applyTo(inputs);

        assertThat(received.tick()).isEqualTo(42);
        assertThat(received.restart()).isTrue();
        assertThat(inputs.get(0)).isEqualTo(moving);
        assertThat(inputs.get(3)).isSameAs(CommanderInput.IDLE);
        assertThat(inputs.orderOf(5)).isEqualTo(Order.DEFENCE);
        assertThat(inputs.joins(7)).isTrue();
        assertThat(inputs.joins(0)).isFalse();
        assertThat(inputs.switchesInvulnerability()).isTrue();
        assertThat(inputs.invulnerable()).isTrue();
    }

    @Test
    void refusesASlotTheSessionDoesNotHave() {
        new Bundle(8).clear(1).hold(7, CommanderInput.IDLE).write(out.reset());

        assertThatThrownBy(() -> new Bundle(4).read(in.wrap(out.array(), 0, out.size())))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("slot 7");
        assertThatThrownBy(() -> Bundle.skip(in.wrap(out.array(), 0, out.size()), 4))
                .isInstanceOf(WireException.class);
    }

    @Test
    void refusesABundleThatEndsEarly() {
        new Bundle(8).clear(1).hold(2, CommanderInput.IDLE).write(out.reset());

        assertThatThrownBy(() -> new Bundle(8).read(in.wrap(out.array(), 0, out.size() - 3)))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("early");
    }

    @Test
    void refusesAnOrderNobodyCanGive() {
        assertThatThrownBy(() -> Bundle.orderFromCode(0)).isInstanceOf(WireException.class);
        assertThatThrownBy(() -> Bundle.orderFromCode(Order.values().length + 1))
                .isInstanceOf(WireException.class);
    }
}
