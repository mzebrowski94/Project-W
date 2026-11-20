package pl.mzebrows.pwt.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.netty.NettyTransport;
import pl.mzebrows.pwt.net.session.BuildId;

/** The relay's HTTP diagnostics on real sockets: a relay on Netty, polled on a thread of its own, and an HTTP client. */
final class RelayDiagnosticsTest {

    private static final long POLL_NANOS = 1_000_000L;

    @TempDir
    Path logs;

    @Test
    void aProbeOpensARoomAndJoinsItThroughTheRealDoor() throws Exception {
        try (Running relay = new Running(logs)) {
            HttpResponse<String> probe = relay.http("POST", "/probe");

            assertThat(probe.statusCode()).isEqualTo(200);
            assertThat(probe.body())
                    .startsWith("{\"ok\":true")
                    .contains("\"step\":\"host link\",\"ok\":true")
                    .contains("\"step\":\"open a room\",\"ok\":true")
                    .contains("\"step\":\"join the room\",\"ok\":true");
        }
    }

    @Test
    void statusNamesTheRoomsOpenAndTheCaller() throws Exception {
        try (Running relay = new Running(logs)) {
            relay.http("POST", "/probe");

            HttpResponse<String> status = relay.http("GET", "/status");

            assertThat(status.statusCode()).isEqualTo(200);
            assertThat(status.body())
                    .contains("\"build\":\"" + BuildId.current() + "\"")
                    .contains("\"you\":\"/127.0.0.1:")
                    .containsPattern("\"rooms\":\\[\\{\"code\":\"[A-Z0-9]{4}\"");
        }
    }

    @Test
    void theLogTailShowsEveryLinkTheTransportAccepted() throws Exception {
        try (Running relay = new Running(logs)) {
            relay.http("POST", "/probe");

            HttpResponse<String> tail = relay.http("GET", "/log?lines=500");

            assertThat(tail.body())
                    .contains("cat=link")
                    .contains("event=accepted")
                    .contains("cat=relay event=room-opened");
        }
    }

    @Test
    void aProbeOfAPortNobodyListensOnSaysTheLinkNeverOpened() throws Exception {
        try (Running relay = new Running(logs, 1)) {
            HttpResponse<String> probe = relay.http("POST", "/probe");

            assertThat(probe.body()).startsWith("{\"ok\":false").contains("\"step\":\"host link\",\"ok\":false");
        }
    }

    @Test
    void anUnknownPathIsNotFound() throws Exception {
        try (Running relay = new Running(logs)) {
            assertThat(relay.http("GET", "/nothing").statusCode()).isEqualTo(404);
        }
    }

    /** A relay on Netty with its diagnostics, polled on a thread of its own as {@link RelayMain} polls it. */
    private static final class Running implements AutoCloseable {

        private final NetLog log;
        private final Relay relay;
        private final RelayDiagnostics diagnostics;
        private final Thread poller;
        private final AtomicBoolean stopping = new AtomicBoolean();
        private final HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        Running(Path logs) throws IOException {
            this(logs, 0);
        }

        /** @param probedPort the port a probe dials, or zero for the relay's own */
        Running(Path logs, int probedPort) throws IOException {
            NetConfig base = NetConfig.defaults();
            NetConfig config = base.with(new NetConfig.LogConfig(
                            logs.toString(), "INFO", base.log().traceSize(), 0))
                    .with(new NetConfig.Rejoin(1, base.rejoin().retryMillis()));
            log = NetLog.open(config.log(), "relay");
            NettyTransport transport = new NettyTransport(config, SystemClock.INSTANCE, log, "relay");
            int port = transport.listen(config.port(), config.portSearch());
            relay = new Relay(
                    transport, SystemClock.INSTANCE, log, config, new SplittableRandom(3), Relay.Sequencing.THREADED);
            diagnostics = RelayDiagnostics.start(
                    0,
                    probedPort == 0 ? port : probedPort,
                    () -> new NettyTransport(config, SystemClock.INSTANCE, log, "probe"),
                    config,
                    SystemClock.INSTANCE,
                    log,
                    BuildId.current());
            poller = new Thread(
                    () -> {
                        while (!stopping.get()) {
                            relay.poll();
                            diagnostics.serve(relay);
                            LockSupport.parkNanos(POLL_NANOS);
                        }
                        relay.close();
                    },
                    "test-relay");
            poller.start();
        }

        HttpResponse<String> http(String method, String path) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + diagnostics.port() + path))
                    .timeout(Duration.ofSeconds(20))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        @Override
        public void close() {
            diagnostics.close();
            stopping.set(true);
            try {
                poller.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.close();
            client.close();
        }
    }
}
