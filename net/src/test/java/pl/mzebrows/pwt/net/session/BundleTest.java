package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Order;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

class BundleTest {

    private final WireWriter out = new WireWriter(64);
    private final WireReader in = new WireReader();

    @Test
    void carriesAimToTheBitAndMovementToASixteenThousandthOfAPush() {
        CommanderInput odd = new CommanderInput(0.70710677f, -1f, Float.MAX_VALUE, 0.1f + 0.2f, true, false)
                .acting(true)
                .aimingRelatively();

        InputCodec.writeHeld(out.reset(), odd);
        CommanderInput read = InputCodec.readHeld(out.array(), 0, CommanderInput.IDLE);

        assertThat(read.moveX()).isCloseTo(0.70710677f, within(1f / 16384));
        assertThat(read.moveY()).isEqualTo(-1f);
        assertThat(Float.floatToRawIntBits(read.aimX())).isEqualTo(Float.floatToRawIntBits(Float.MAX_VALUE));
        assertThat(Float.floatToRawIntBits(read.aimY())).isEqualTo(Float.floatToRawIntBits(0.1f + 0.2f));
        assertThat(read.acting()).isTrue();
        assertThat(read.aimRelative()).isTrue();
        assertThat(out.size()).isEqualTo(Bundle.HELD_BYTES);
    }

    /** Movement goes as a sixteen-thousandth of a push, so a full push and every half and quarter stay whole. */
    @Test
    void carriesAFullPushAndItsHalvesExactly() {
        for (float push : new float[] {1f, -1f, 0.5f, -0.75f, 0.25f, 0f}) {
            InputCodec.writeHeld(out.reset(), new CommanderInput(push, push, 0f, 0f, false, false));

            assertThat(InputCodec.readHeld(out.array(), 0, CommanderInput.IDLE).moveX())
                    .isEqualTo(push);
        }
    }

    /**
     * Every tick in a bundle datagram after the first carries only the held input that changed from the
     * one before it, and a peer rebuilds the whole tick from the two: the 2026-10-01 playtest sent every
     * seat's whole input three times a tick, 87% of what a session sends.
     */
    @Test
    void sendsOnlyTheHeldInputThatChangedFromTheTickBefore() {
        CommanderInput standing = new CommanderInput(0f, 0f, 40f, 30f, false, false);
        CommanderInput walking = new CommanderInput(0.5f, 0f, 40f, 30f, true, false);
        byte[] before = bundle(7, standing, standing);
        byte[] after = bundle(8, standing, walking);

        WireWriter changed = new WireWriter(64);
        Bundle.writeChanged(changed, after, after.length, before);
        WireWriter rebuilt = new WireWriter(64);
        int tick = Bundle.rebuild(in.wrap(changed.array(), 0, changed.size()), before, 0, Players.MAX, rebuilt);

        assertThat(tick).isEqualTo(8);
        assertThat(rebuilt.toBytes()).isEqualTo(after);
        assertThat(changed.size()).isLessThan(after.length - Bundle.HELD_BYTES);
    }

    @Test
    void sendsAWholeSeatThatTheTickBeforeDidNotHave() {
        CommanderInput walking = new CommanderInput(0.5f, 0f, 40f, 30f, true, false);
        byte[] before = bundle(7, null, walking);
        byte[] after = bundle(8, walking, walking);

        WireWriter changed = new WireWriter(64);
        Bundle.writeChanged(changed, after, after.length, before);
        WireWriter rebuilt = new WireWriter(64);
        Bundle.rebuild(in.wrap(changed.array(), 0, changed.size()), before, 0, Players.MAX, rebuilt);

        assertThat(rebuilt.toBytes()).isEqualTo(after);
    }

    @Test
    void refusesASeatSaidUnchangedFromATickThatDidNotHaveIt() {
        CommanderInput walking = new CommanderInput(0.5f, 0f, 40f, 30f, true, false);
        byte[] before = bundle(7, null, walking);
        byte[] after = bundle(8, walking, walking);
        WireWriter changed = new WireWriter(64);
        Bundle.writeChanged(changed, after, after.length, after);

        assertThatThrownBy(() -> Bundle.rebuild(
                        in.wrap(changed.array(), 0, changed.size()), before, 0, Players.MAX, new WireWriter(64)))
                .isInstanceOf(WireException.class);
    }

    /** A seat's slot and which of its fields changed share a byte: three bits of slot. */
    @Test
    void fitsEverySlotInThreeBits() {
        assertThat(Players.MAX).isLessThanOrEqualTo(8);
    }

    private static byte[] held(CommanderInput input) {
        WireWriter written = new WireWriter(Bundle.HELD_BYTES);
        InputCodec.writeHeld(written, input);
        return written.toBytes();
    }

    private static byte[] bundle(int tick, @Nullable CommanderInput slot0, CommanderInput slot1) {
        Bundle bundle = new Bundle(Players.MAX);
        bundle.clear(tick);
        if (slot0 != null) {
            bundle.hold(0, held(slot0), 0);
        }
        bundle.hold(1, held(slot1), 0);
        bundle.join(2, 1);
        WireWriter written = new WireWriter(64);
        bundle.write(written);
        return written.toBytes();
    }

    /**
     * Every component of a held input, one at a time. The wire lists its fields by hand, so a button
     * added to {@code CommanderInput} and not to {@link InputCodec} would never reach any peer, the host's
     * own included - no desync, only a feature that does nothing online.
     */
    @Test
    void carriesEveryComponentOfAHeldInput() throws ReflectiveOperationException {
        RecordComponent[] components = CommanderInput.class.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }
        Constructor<CommanderInput> canonical = CommanderInput.class.getDeclaredConstructor(types);
        for (int changed = 0; changed < components.length; changed++) {
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                values[i] = types[i] == boolean.class ? (Object) (i == changed) : (Object) (i == changed ? 0.75f : 0f);
            }
            CommanderInput sent = canonical.newInstance(values);

            InputCodec.writeHeld(out.reset(), sent);
            CommanderInput read = InputCodec.readHeld(out.array(), 0, CommanderInput.IDLE);

            assertThat(read).as(components[changed].getName()).isEqualTo(sent);
        }
    }

    @Test
    void turnsANumberThatIsNotFiniteIntoZero() {
        InputCodec.writeHeld(out.reset(), new CommanderInput(Float.NaN, Float.POSITIVE_INFINITY, 3f, 4f, false, false));

        CommanderInput read = InputCodec.readHeld(out.array(), 0, CommanderInput.IDLE);

        assertThat(read.moveX()).isZero();
        assertThat(read.moveY()).isZero();
        assertThat(read.aimX()).isEqualTo(3f);
    }

    /** What no peer writes is dropped the same on every machine - a sequencer that cannot read it cannot refuse it. */
    @Test
    void dropsButtonsAndPushesNobodyHas() {
        byte[] bytes = new byte[Bundle.HELD_BYTES];
        bytes[0] = 0x7F;
        bytes[12] = (byte) 0xC1;

        CommanderInput read = InputCodec.readHeld(bytes, 0, CommanderInput.IDLE);

        assertThat(read.moveX()).isEqualTo(1f);
        assertThat(read.firing()).isTrue();
        assertThat(read.striking()).isFalse();
    }

    @Test
    void handsBackTheSameInputWhenNothingHasChanged() {
        CommanderInput steady = new CommanderInput(0.5f, 0.25f, 10f, 20f, true, false);
        InputCodec.writeHeld(out.reset(), new CommanderInput(0.5f, 0.25f, 10f, 20f, true, false));

        assertThat(InputCodec.readHeld(out.array(), 0, steady)).isSameAs(steady);
    }

    @Test
    void carriesATickWholeAndHandsItToAWorld() {
        Bundle sent = new Bundle(8).clear(42);
        CommanderInput moving = new CommanderInput(1f, 0f, 5f, 5f, true, false);
        sent.hold(0, held(moving), 0)
                .hold(5, held(CommanderInput.IDLE), 0)
                .event(5, Msg.EVENT_ORDER, InputCodec.orderCode(Order.DEFENCE), 0)
                .join(7, 3)
                .restarting();
        sent.switchInvulnerability(true);
        sent.write(out.reset());
        int length = out.size();

        assertThat(Bundle.skip(in.wrap(out.array(), 0, length), 8)).isEqualTo(42);
        assertThat(in.remaining()).isZero();
        Bundle received = new Bundle(8).read(in.wrap(out.array(), 0, length));
        PlayerInputs inputs = new PlayerInputs(8);
        new InputCodec(8).applyTo(received, inputs);

        assertThat(received.tick()).isEqualTo(42);
        assertThat(received.restart()).isTrue();
        assertThat(inputs.get(0)).isEqualTo(moving);
        assertThat(inputs.get(3)).isSameAs(CommanderInput.IDLE);
        assertThat(inputs.orderOf(5)).isEqualTo(Order.DEFENCE);
        assertThat(inputs.joins(7)).isTrue();
        assertThat(inputs.machineOf(7)).isEqualTo(3);
        assertThat(inputs.joins(0)).isFalse();
        assertThat(inputs.switchesInvulnerability()).isTrue();
        assertThat(inputs.invulnerable()).isTrue();
    }

    /** A card taken and a link dropped or come back cross whole, and reach the world as their events (S8). */
    @Test
    void carriesPicksAndPresenceAndHandsThemToAWorld() {
        Bundle sent = new Bundle(8)
                .clear(9)
                .event(2, Msg.EVENT_PICK, 17, InputCodec.pickCode(UpgradeKind.FIREPOWER))
                .away(3)
                .back(6);
        sent.write(out.reset());
        int length = out.size();

        assertThat(Bundle.skip(in.wrap(out.array(), 0, length), 8)).isEqualTo(9);
        assertThat(in.remaining()).isZero();
        PlayerInputs inputs = new PlayerInputs(8);
        new InputCodec(8).applyTo(new Bundle(8).read(in.wrap(out.array(), 0, length)), inputs);

        assertThat(inputs.pickOf(2)).isEqualTo(UpgradeKind.FIREPOWER);
        assertThat(inputs.pickSerialOf(2)).isEqualTo(17);
        assertThat(inputs.pickOf(3)).isNull();
        assertThat(inputs.goesAway(3)).isTrue();
        assertThat(inputs.comesBack(3)).isFalse();
        assertThat(inputs.comesBack(6)).isTrue();
        assertThat(inputs.goesAway(6)).isFalse();
    }

    /** A take from a crate and a crate shut cross whole, and reach the world as their events (G0056). */
    @Test
    void carriesCrateEventsAndHandsThemToAWorld() {
        Bundle sent = new Bundle(8)
                .clear(4)
                .event(1, Msg.EVENT_CRATE, InputCodec.crateCode(PickupKind.ARMOURY_FLAMER), 0)
                .event(5, Msg.EVENT_CRATE, InputCodec.CRATE_CLOSE, 0);
        sent.write(out.reset());
        int length = out.size();

        assertThat(Bundle.skip(in.wrap(out.array(), 0, length), 8)).isEqualTo(4);
        assertThat(in.remaining()).isZero();
        PlayerInputs inputs = new PlayerInputs(8);
        new InputCodec(8).applyTo(new Bundle(8).read(in.wrap(out.array(), 0, length)), inputs);

        assertThat(inputs.crateTakeOf(1)).isEqualTo(PickupKind.ARMOURY_FLAMER);
        assertThat(inputs.closesCrate(1)).isFalse();
        assertThat(inputs.closesCrate(5)).isTrue();
        assertThat(inputs.crateTakeOf(5)).isNull();
    }

    @Test
    void ignoresAPickupNobodyCanTakeFromACrate() {
        assertThat(InputCodec.crateFromCode(PickupKind.values().length + 1)).isNull();
    }

    /** The history a sequencer keeps is sized by this: a tick with everything in it must fit. */
    @Test
    void fitsATickWithEverythingInItInTheRoomTheSequencerKeeps() {
        Bundle full = new Bundle(8).clear(1).restarting();
        full.switchInvulnerability(true);
        for (int slot = 0; slot < 8; slot++) {
            full.hold(slot, held(new CommanderInput(1f, 1f, 1f, 1f, true, true)), 0)
                    .event(slot, Msg.EVENT_ORDER, InputCodec.orderCode(Order.FORWARD), 0)
                    .join(slot, Integer.MAX_VALUE)
                    .event(slot, Msg.EVENT_PICK, Integer.MAX_VALUE, InputCodec.pickCode(UpgradeKind.EXPLOSIVES))
                    .event(slot, Msg.EVENT_CRATE, InputCodec.crateCode(PickupKind.MEDKIT), 0);
            if (slot % 2 == 0) {
                full.away(slot);
            } else {
                full.back(slot);
            }
        }
        WireWriter big = new WireWriter(1024);

        full.write(big);

        assertThat(big.size()).isLessThanOrEqualTo(Sequencer.maxBundleBytes(8));
    }

    @Test
    void ignoresAnUpgradeNobodyCanTake() {
        assertThat(InputCodec.pickFromCode(0)).isNull();
        assertThat(InputCodec.pickFromCode(UpgradeKind.values().length + 1)).isNull();
    }

    @Test
    void refusesASlotTheSessionDoesNotHave() {
        new Bundle(8).clear(1).hold(7, held(CommanderInput.IDLE), 0).write(out.reset());

        assertThatThrownBy(() -> new Bundle(4).read(in.wrap(out.array(), 0, out.size())))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("slot 7");
        assertThatThrownBy(() -> Bundle.skip(in.wrap(out.array(), 0, out.size()), 4))
                .isInstanceOf(WireException.class);
    }

    @Test
    void refusesABundleThatEndsEarly() {
        new Bundle(8).clear(1).hold(2, held(CommanderInput.IDLE), 0).write(out.reset());

        assertThatThrownBy(() -> new Bundle(8).read(in.wrap(out.array(), 0, out.size() - 3)))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("early");
    }

    @Test
    void ignoresAnOrderNobodyCanGive() {
        assertThat(InputCodec.orderFromCode(0)).isNull();
        assertThat(InputCodec.orderFromCode(Order.values().length + 1)).isNull();
    }
}
