package pl.mzebrows.pwt.net.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.RecordingListener;
import pl.mzebrows.pwt.net.transport.RecordingListener.Heard;

/**
 * The transport over real sockets on this machine - what two instances on one PC will do.
 *
 * <p>Every test closes what it opened and then asks Netty's leak detector, running at its most
 * suspicious in this module's tests, whether any buffer was lost on the way. A leak fails the test that
 * caused it rather than slowly eating memory in a two-hour session.
 */
class NettyTransportTest {

    private static final String HERE = "127.0.0.1";
    private static final long PATIENCE_MILLIS = 5_000;

    private final List<NettyTransport> transports = new ArrayList<>();
    private final LeakWatcher leaks = LeakWatcher.install();

    @AfterEach
    void closesEverythingWithoutLeakingABuffer() {
        transports.forEach(NettyTransport::close);
        // A leak is only reported once the lost buffer has been collected and another one is allocated,
        // so collect, give the reference queue a moment, and allocate - a few times over.
        for (int round = 0; round < 5 && leaks.reported().isEmpty(); round++) {
            System.gc();
            pauseQuietly(20);
            for (int i = 0; i < 64; i++) {
                // Pooled and direct, like what the sockets read into: the leak detector checks for lost
                // buffers when it is handed a new one to track, and it only tracks this kind.
                ByteBuf probe = PooledByteBufAllocator.DEFAULT.directBuffer(16);
                probe.release();
            }
        }
        leaks.uninstall();
        assertThat(leaks.reported()).as("buffers Netty says were leaked").isEmpty();
    }

    @Test
    void opensALinkAtBothEnds() {
        Ends ends = open(47_310);

        assertThat(ends.atHost.of("opened")).hasSize(1);
        assertThat(ends.atJoiner.of("opened"))
                .singleElement()
                .extracting(Heard::link)
                .isEqualTo(ends.link);
    }

    @Test
    void carriesAReliableMessageOfAMegabyteWhole() {
        Ends ends = open(47_320);
        byte[] snapshot = new byte[1024 * 1024];
        new Random(7).nextBytes(snapshot);

        ends.joiner.send(ends.link, Delivery.RELIABLE, snapshot, 0, snapshot.length);
        pollUntil(() -> !ends.atHost.of("received").isEmpty(), ends);

        assertThat(ends.atHost.of("received")).singleElement().satisfies(heard -> {
            assertThat(heard.length()).isEqualTo(snapshot.length);
            assertThat(heard.hash()).isEqualTo(Arrays.hashCode(snapshot));
        });
    }

    @Test
    void carriesUnreliableMessagesBothWays() {
        Ends ends = open(47_330);
        int hostLink = ends.atHost.of("opened").getFirst().link();

        ends.joiner.send(ends.link, Delivery.UNRELIABLE, new byte[] {1}, 0, 1);
        pollUntil(() -> !ends.atHost.of("received").isEmpty(), ends);
        ends.host.send(hostLink, Delivery.UNRELIABLE, new byte[] {2}, 0, 1);
        pollUntil(() -> !ends.atJoiner.of("received").isEmpty(), ends);

        assertThat(ends.atHost.of("received")).extracting(Heard::firstByte).containsExactly(1);
        assertThat(ends.atJoiner.of("received")).extracting(Heard::firstByte).containsExactly(2);
    }

    /** Two instances on one machine: the second host finds the next port rather than failing. */
    @Test
    void givesASecondHostOnThisMachineTheNextPort() {
        NettyTransport first = transport();
        NettyTransport second = transport();

        int firstPort = first.listen(47_340, 8);
        int secondPort = second.listen(47_340, 8);

        assertThat(firstPort).isEqualTo(47_340);
        assertThat(secondPort).isEqualTo(47_341);
    }

    @Test
    void tellsTheOtherEndWhyTheLinkWasClosed() {
        Ends ends = open(47_350);

        ends.joiner.disconnect(ends.link, "leaving");
        pollUntil(() -> !ends.atHost.of("closed").isEmpty(), ends);

        assertThat(ends.atHost.of("closed"))
                .singleElement()
                .extracting(Heard::text)
                .isEqualTo("remote: leaving");
        assertThat(ends.atJoiner.of("closed"))
                .singleElement()
                .extracting(Heard::text)
                .isEqualTo("leaving");
    }

    @Test
    void givesUpOnAHostNobodyIsRunningAndSaysSo() {
        NettyTransport joiner = transport();
        RecordingListener heard = new RecordingListener();

        joiner.connect(HERE, 47_399);
        pollUntil(() -> !heard.of("closed").isEmpty(), joiner, heard);

        assertThat(heard.of("closed"))
                .singleElement()
                .extracting(Heard::text)
                .asString()
                .contains("tcp connect failed");
    }

    @Test
    void writesTheLifeOfALinkToTheHostsLog(@TempDir Path dir) throws IOException {
        NetLog hostLog = NetLog.open(new NetConfig.LogConfig(dir.toString(), "INFO", 1024, 0), "host");
        NettyTransport host = new NettyTransport(config(), SystemClock.INSTANCE, hostLog, "host");
        transports.add(host);
        NettyTransport joiner = transport();
        host.listen(47_360, 0);
        RecordingListener atHost = new RecordingListener();
        RecordingListener atJoiner = new RecordingListener();
        int link = joiner.connect(HERE, 47_360);
        Ends ends = new Ends(host, joiner, link, atHost, atJoiner);
        pollUntil(() -> !atJoiner.of("opened").isEmpty(), ends);

        joiner.disconnect(link, "leaving");
        pollUntil(() -> !atHost.of("closed").isEmpty(), ends);
        hostLog.close();

        String written = Files.readString(Objects.requireNonNull(hostLog.file()), StandardCharsets.UTF_8);
        assertThat(written)
                .contains("event=listening")
                .contains("event=accepted")
                .contains("event=udp-bound")
                .contains("event=opened")
                .contains("event=closed reason=\"remote: leaving\"");
    }

    private Ends open(int port) {
        NettyTransport host = transport();
        NettyTransport joiner = transport();
        host.listen(port, 0);
        RecordingListener atHost = new RecordingListener();
        RecordingListener atJoiner = new RecordingListener();
        int link = joiner.connect(HERE, port);
        Ends ends = new Ends(host, joiner, link, atHost, atJoiner);
        pollUntil(() -> !atHost.of("opened").isEmpty() && !atJoiner.of("opened").isEmpty(), ends);
        return ends;
    }

    private NettyTransport transport() {
        NettyTransport transport = new NettyTransport(config(), SystemClock.INSTANCE, NetLog.silent(1024), "test");
        transports.add(transport);
        return transport;
    }

    private static NetConfig config() {
        NetConfig shipped = NetConfig.defaults();
        return new NetConfig(
                47_300,
                8,
                1_500,
                2_000,
                100,
                shipped.maxDatagramBytes(),
                shipped.maxMessageBytes(),
                NetConfig.Conditions.NONE,
                new NetConfig.LogConfig("unused", "INFO", 1024, 0),
                NetConfig.Session.DEFAULT,
                NetConfig.Lan.DEFAULT);
    }

    private static void pollUntil(BooleanSupplier done, Ends ends) {
        long deadline = System.nanoTime() + PATIENCE_MILLIS * 1_000_000L;
        while (!done.getAsBoolean()) {
            ends.host.poll(ends.atHost);
            ends.joiner.poll(ends.atJoiner);
            pause(deadline);
        }
    }

    private static void pollUntil(BooleanSupplier done, NettyTransport transport, RecordingListener listener) {
        long deadline = System.nanoTime() + PATIENCE_MILLIS * 1_000_000L;
        while (!done.getAsBoolean()) {
            transport.poll(listener);
            pause(deadline);
        }
    }

    private static void pause(long deadline) {
        if (System.nanoTime() > deadline) {
            fail("nothing happened within " + PATIENCE_MILLIS + " ms");
        }
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
    }

    private static void pauseQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Ends(
            NettyTransport host,
            NettyTransport joiner,
            int link,
            RecordingListener atHost,
            RecordingListener atJoiner) {}

    /** Collects what Netty's leak detector says, from the logger it says it on. */
    private static final class LeakWatcher extends Handler {

        private final List<String> reported = new ArrayList<>();
        private final Logger netty = Logger.getLogger("io.netty");

        static LeakWatcher install() {
            LeakWatcher watcher = new LeakWatcher();
            watcher.netty.addHandler(watcher);
            return watcher;
        }

        void uninstall() {
            netty.removeHandler(this);
        }

        synchronized List<String> reported() {
            return new ArrayList<>(reported);
        }

        @Override
        public synchronized void publish(LogRecord record) {
            String message = record.getMessage();
            if (record.getLevel().intValue() >= Level.WARNING.intValue()
                    && message != null
                    && message.contains("LEAK")) {
                reported.add(message);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
