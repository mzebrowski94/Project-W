package pl.mzebrows.pwt.net.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mzebrows.pwt.net.NetConfig;

class WireTest {

    private static final int MOST = 64 * 1024;

    /** Like a world: long runs of the same few values. */
    private static final byte[] WORLD = world();

    @Test
    void readsBackAPackedRunAsItWasWrittenInAFractionOfTheSpace() {
        WireWriter out =
                new WireWriter(64).putByte(7).putPacked(WORLD, 0, WORLD.length).putByte(9);

        WireReader in = new WireReader().wrap(out.array(), 0, out.size());

        assertThat(in.getByte()).isEqualTo(7);
        assertThat(in.getPacked(MOST)).isEqualTo(WORLD);
        assertThat(in.getByte()).isEqualTo(9);
        in.expectEnd();
        assertThat(out.size()).isLessThan(WORLD.length / 4);
    }

    @Test
    void readsBackTheSessionConfigAWelcomeCarries() {
        NetConfig.Session session = new NetConfig.Session(37);
        WireWriter out = new WireWriter(64);
        Msg.putSession(out, session);

        WireReader in = new WireReader().wrap(out.array(), 0, out.size());

        assertThat(Msg.getSession(in)).isEqualTo(session);
        in.expectEnd();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, NetConfig.Session.MOST_CHECKSUM_TICKS + 1})
    void refusesASessionConfigNoSessionCouldRunBy(int checksumTicks) {
        WireWriter out = new WireWriter(64).putInt(checksumTicks);

        WireReader in = new WireReader().wrap(out.array(), 0, out.size());

        assertThatThrownBy(() -> Msg.getSession(in)).isInstanceOf(WireException.class);
    }

    @Test
    void readsBackAnEmptyPackedRun() {
        WireWriter out = new WireWriter(64).putPacked(new byte[0], 0, 0);

        WireReader in = new WireReader().wrap(out.array(), 0, out.size());

        assertThat(in.getPacked(MOST)).isEmpty();
        in.expectEnd();
    }

    @ParameterizedTest
    @MethodSource("broken")
    void refusesAPackedRunThatIsNotWhatItClaims(String what, byte[] message) {
        WireReader in = new WireReader().wrap(message, 0, message.length);

        assertThatThrownBy(() -> in.getPacked(MOST)).as(what).isInstanceOf(WireException.class);
    }

    static Stream<Arguments> broken() {
        byte[] packed = new WireWriter(64).putPacked(WORLD, 0, WORLD.length).toBytes();
        return Stream.of(
                Arguments.of("longer than may be read", withLength(packed, MOST + 1)),
                Arguments.of("longer than it inflates to", withLength(packed, WORLD.length + 1)),
                Arguments.of("shorter than it inflates to", withLength(packed, WORLD.length - 1)),
                Arguments.of("cut short", Arrays.copyOf(packed, packed.length - 5)),
                Arguments.of(
                        "not deflated",
                        new WireWriter(64)
                                .putInt(5)
                                .putBytes("hello".getBytes(StandardCharsets.UTF_8))
                                .toBytes()));
    }

    private static byte[] withLength(byte[] packed, int length) {
        byte[] changed = packed.clone();
        byte[] prefix = new WireWriter(4).putInt(length).toBytes();
        System.arraycopy(prefix, 0, changed, 0, 4);
        return changed;
    }

    private static byte[] world() {
        byte[] world = new byte[20_000];
        for (int i = 0; i < world.length; i++) {
            world[i] = (byte) ((i / 40) % 7);
        }
        return world;
    }
}
