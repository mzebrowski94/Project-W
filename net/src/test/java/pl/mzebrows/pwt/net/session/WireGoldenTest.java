package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.sim.CommanderInput;

/**
 * The protocol's bytes, written down. Every expected array below is a literal, and the protocol number
 * sits beside them: a change to a layout, a message number or a rounding fails here until the expected
 * bytes are changed on purpose - and {@link Msg#PROTOCOL} with them (ADR 0023). A relay in another build
 * than a peer's can only trust the layout is what it was if this test says so.
 */
class WireGoldenTest {

    /** What {@link Msg#PROTOCOL} was when the bytes in this file were written. Bump both together. */
    private static final int PROTOCOL_OF_THESE_BYTES = 8;

    private final WireWriter out = new WireWriter(64);
    private final WireReader in = new WireReader();

    @Test
    void theBytesBelowAreOfTheProtocolInForce() {
        assertThat(Msg.PROTOCOL)
                .as("a layout changed: rewrite the expected bytes here and bump Msg.PROTOCOL")
                .isEqualTo(PROTOCOL_OF_THESE_BYTES);
    }

    @Test
    void numbersEveryMessageTypeAsItWasSent() {
        Map<String, Integer> numbers = new LinkedHashMap<>();
        numbers.put("HELLO", Msg.HELLO);
        numbers.put("INPUT", Msg.INPUT);
        numbers.put("CHECKSUM", Msg.CHECKSUM);
        numbers.put("SNAPSHOT_TAKEN", Msg.SNAPSHOT_TAKEN);
        numbers.put("SEAT", Msg.SEAT);
        numbers.put("PAUSE", Msg.PAUSE);
        numbers.put("RESTART", Msg.RESTART);
        numbers.put("GOD_MODE", Msg.GOD_MODE);
        numbers.put("START", Msg.START);
        numbers.put("TO_BRIEFING", Msg.TO_BRIEFING);
        numbers.put("NOTE", Msg.NOTE);
        numbers.put("REJOIN", Msg.REJOIN);
        numbers.put("LEAVE", Msg.LEAVE);
        numbers.put("WELCOME", Msg.WELCOME);
        numbers.put("REFUSED", Msg.REFUSED);
        numbers.put("BUNDLES", Msg.BUNDLES);
        numbers.put("SNAPSHOT", Msg.SNAPSHOT);
        numbers.put("SLOT", Msg.SLOT);
        numbers.put("PAUSED", Msg.PAUSED);
        numbers.put("DESYNC", Msg.DESYNC);
        numbers.put("SNAPSHOT_WANTED", Msg.SNAPSHOT_WANTED);
        numbers.put("DUMP_WANTED", Msg.DUMP_WANTED);
        numbers.put("STARTED", Msg.STARTED);
        numbers.put("BRIEFING", Msg.BRIEFING);
        numbers.put("PLAYERS", Msg.PLAYERS);
        numbers.put("NOTED", Msg.NOTED);
        numbers.put("ENDED", Msg.ENDED);

        assertThat(numbers)
                .containsExactly(
                        Map.entry("HELLO", 1),
                        Map.entry("INPUT", 2),
                        Map.entry("CHECKSUM", 3),
                        Map.entry("SNAPSHOT_TAKEN", 4),
                        Map.entry("SEAT", 5),
                        Map.entry("PAUSE", 6),
                        Map.entry("RESTART", 7),
                        Map.entry("GOD_MODE", 8),
                        Map.entry("START", 9),
                        Map.entry("TO_BRIEFING", 10),
                        Map.entry("NOTE", 11),
                        Map.entry("REJOIN", 12),
                        Map.entry("LEAVE", 13),
                        Map.entry("WELCOME", 20),
                        Map.entry("REFUSED", 21),
                        Map.entry("BUNDLES", 22),
                        Map.entry("SNAPSHOT", 23),
                        Map.entry("SLOT", 24),
                        Map.entry("PAUSED", 25),
                        Map.entry("DESYNC", 26),
                        Map.entry("SNAPSHOT_WANTED", 27),
                        Map.entry("DUMP_WANTED", 28),
                        Map.entry("STARTED", 29),
                        Map.entry("BRIEFING", 30),
                        Map.entry("PLAYERS", 31),
                        Map.entry("NOTED", 32),
                        Map.entry("ENDED", 33));
        assertThat(Msg.TOKEN_BYTES).isEqualTo(16);
        assertThat(new int[] {Msg.EVENT_ORDER, Msg.EVENT_PICK, Msg.EVENT_CRATE}).containsExactly(1, 2, 3);
        assertThat(Msg.EVENT_BYTES).isEqualTo(11);
        assertThat(Msg.NO_SLOT).isEqualTo(255);
        assertThat(Msg.PAUSE_BY_PLAYER).isZero();
        assertThat(Msg.PAUSE_FOR_DRAFT).isEqualTo(1);
    }

    @Test
    void writesThePrimitivesBigEndian() {
        out.putByte(0xAB)
                .putShort(-2)
                .putInt(0x01020304)
                .putLong(0x0102030405060708L)
                .putString("hi");

        assertThat(out.toBytes())
                .containsExactly(bytes(0xAB, 0xFF, 0xFE, 1, 2, 3, 4, 1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 2, 'h', 'i'));
    }

    @Test
    void writesAHeldInputAsThirteenBytes() {
        InputCodec.writeHeld(out, heldInput());

        assertThat(out.toBytes()).containsExactly(bytes(0x20, 0x00, 0xC0, 0x00, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0, 9));
        out.reset();
        InputCodec.writeHeld(out, CommanderInput.IDLE);
        assertThat(out.toBytes()).containsExactly(new byte[Bundle.HELD_BYTES]);
    }

    @Test
    void writesAWelcomesSessionConfig() {
        Msg.putSession(out, new NetConfig.Session(30));

        assertThat(out.toBytes()).containsExactly(bytes(0, 0, 0, 30));
    }

    @Test
    void writesAWelcomeWithTheMachinesTokenAndHowLongItsSeatsAreKept() {
        byte[] token = new byte[Msg.TOKEN_BYTES];
        for (int i = 0; i < token.length; i++) {
            token[i] = (byte) (i + 1);
        }

        Msg.putWelcome(out, 2, 8, false, new NetConfig.Session(30), 0L, new byte[0], token, 30);

        byte[] written = out.toBytes();
        assertThat(written)
                .startsWith(bytes(20, 0, 0, 0, 2, 8, 0, 0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 0, 0))
                .endsWith(bytes(0, 0, 0, 16, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 0, 0, 0, 30));
        assertThat(written).hasSize(1 + 4 + 1 + 1 + 4 + 8 + packedEmptyLength() + 4 + 16 + 4);
    }

    @Test
    void writesARejoinAsAHelloWithTheTokenInPlaceOfAKey() {
        byte[] token = new byte[Msg.TOKEN_BYTES];
        for (int i = 0; i < token.length; i++) {
            token[i] = (byte) (i + 1);
        }

        Msg.putRejoin(out, "b1", "m", token);

        assertThat(out.toBytes())
                .containsExactly(bytes(
                        12, 0, 0, 0, 8, 0, 0, 0, 2, 'b', '1', 0, 0, 0, 1, 'm', 0, 0, 0, 16, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16));
    }

    private int packedEmptyLength() {
        WireWriter packed = new WireWriter(16);
        packed.putPacked(new byte[0], 0, 0);
        return packed.size();
    }

    @Test
    void writesASealedTickInEveryOfItsSections() {
        assertThat(sealed(7, true)).containsExactly(WHOLE_TICK);
    }

    @Test
    void writesOnlyWhatChangedForTheSecondTickOfADatagram() {
        byte[] before = sealed(6, true);
        byte[] after = sealed(7, true);

        out.reset();
        Bundle.writeChanged(out, after, after.length, before);

        // The seat's byte says nothing of its held input changed: its slot, no fields.
        assertThat(out.toBytes())
                .containsExactly(bytes(
                        0, 0, 0, 7, 7, 1, 1, 1, 2, 3, 1, 4, 1, 2, 3, 4, 1, 5, 0, 0, 1, 2, 9, 2, 6, 1, 7, 0, 1, 0, 0));
    }

    @Test
    void writesAWholeSeatWhenTheTickBeforeDidNotHaveIt() {
        byte[] before = sealed(6, false);
        byte[] after = sealed(7, true);

        out.reset();
        Bundle.writeChanged(out, after, after.length, before);

        // Slot 1 with all five stretches of its held input following: 1 | 31 << 3.
        assertThat(out.toBytes())
                .containsExactly(bytes(
                        0, 0, 0, 7, 7, 1, 0xF9, 0x20, 0x00, 0xC0, 0x00, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0, 9, 1, 2, 3, 1,
                        4, 1, 2, 3, 4, 1, 5, 0, 0, 1, 2, 9, 2, 6, 1, 7, 0, 1, 0, 0));
    }

    @Test
    void readsBackWhatWasWritten() {
        byte[] whole = sealed(7, true);

        Bundle read = new Bundle(8).read(in.wrap(whole, 0, whole.length));

        assertThat(read.tick()).isEqualTo(7);
        assertThat(read.holds(1)).isTrue();
        assertThat(read.eventValue(2, Msg.EVENT_ORDER)).isEqualTo(3);
        assertThat(read.joinMachine(4)).isEqualTo(0x01020304);
        assertThat(read.eventValue(5, Msg.EVENT_PICK)).isEqualTo(258);
        assertThat(read.eventExtra(5, Msg.EVENT_PICK)).isEqualTo(9);
        assertThat(read.goesAway(6)).isTrue();
        assertThat(read.comesBack(7)).isTrue();
        assertThat(read.hasEvent(0, Msg.EVENT_CRATE)).isTrue();
    }

    private static final byte[] WHOLE_TICK = bytes(
            0, 0, 0, 7, // tick
            7, // flags: restart, switches invulnerability, invulnerable
            1, // held count
            1, 0x20, 0x00, 0xC0, 0x00, 0x3F, 0x80, 0, 0, 0x40, 0, 0, 0, 9, // slot 1 and its held input
            1, 2, 3, // orders: slot 2, value 3
            1, 4, 1, 2, 3, 4, // joins: slot 4, machine
            1, 5, 0, 0, 1, 2, 9, // picks: slot 5, serial 258, extra 9
            2, 6, 1, 7, 0, // presence: slot 6 away, slot 7 back
            1, 0, 0); // crates: slot 0, value 0

    private static CommanderInput heldInput() {
        return new CommanderInput(0.5f, -1f, 1f, 2f, true, false).acting(true);
    }

    private static byte[] sealed(int tick, boolean holding) {
        Bundle bundle = new Bundle(8).clear(tick).restarting();
        bundle.switchInvulnerability(true);
        if (holding) {
            WireWriter held = new WireWriter(Bundle.HELD_BYTES);
            InputCodec.writeHeld(held, heldInput());
            bundle.hold(1, held.toBytes(), 0);
        }
        bundle.event(2, Msg.EVENT_ORDER, 3, 0)
                .join(4, 0x01020304)
                .event(5, Msg.EVENT_PICK, 258, 9)
                .away(6)
                .back(7)
                .event(0, Msg.EVENT_CRATE, 0, 0);
        WireWriter written = new WireWriter(64);
        bundle.write(written);
        return written.toBytes();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
