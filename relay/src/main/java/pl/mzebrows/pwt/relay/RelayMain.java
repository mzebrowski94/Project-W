package pl.mzebrows.pwt.relay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.SystemClock;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.netty.NettyTransport;
import pl.mzebrows.pwt.net.session.BuildId;
import pl.mzebrows.pwt.net.transport.Transport;

/**
 * The relay as a process, and nothing else: Netty on {@code net.relay.port}, the system clock, a secure random, and
 * {@code network.properties} as it ships - or, as the one argument, a file of lines that override it, so an
 * operator tunes the relay without a build. A thread per room runs its sequencer; this thread polls the door
 * every millisecond. It prints a banner of what it runs with, and echoes what rooms and links do to the console as
 * well as the log file. Arguments, in any order and both optional: a file of override lines, and a port that
 * beats {@code net.relay.port}. With {@code net.relay.diagnosticPort} set it serves {@link RelayDiagnostics} too,
 * answered on this thread after each poll. The only class of this module that names the Netty adapter ({@code RelayArchitectureTest}).
 */
public final class RelayMain {

    /** How often the door is polled: what a link's first word waits for at most. */
    private static final long POLL_NANOS = 1_000_000L;

    /** How long a stop waits for every room to be told: a sequencer thread stops within a millisecond. */
    private static final long SHUTDOWN_WAIT_SECONDS = 5L;

    private RelayMain() {}

    public static void main(String[] args) throws IOException {
        String overrides = "";
        int chosenPort = -1;
        for (String arg : args) {
            if (arg.matches("\\d{1,5}") && Integer.parseInt(arg) > 0 && Integer.parseInt(arg) < 65_536) {
                chosenPort = Integer.parseInt(arg);
            } else {
                overrides = arg;
            }
        }
        String text = overrides.isEmpty() ? "" : Files.readString(Path.of(overrides), StandardCharsets.ISO_8859_1);
        NetConfig config = NetConfig.read(
                text,
                problem ->
                        System.err.println("[relay] network.properties: " + problem + " (the shipped value stands)"));
        if (chosenPort > 0) {
            config = config.with(config.relay().at(config.relay().host(), chosenPort));
        }
        NetLog log = NetLog.open(config.log(), "relay", true);
        Transport transport = new NettyTransport(config, SystemClock.INSTANCE, log, "relay");
        int port = transport.listen(config.relay().port(), 0);
        RelayDiagnostics diagnostics = diagnostics(config, port, log);
        Path file = log.file();
        RelayBanner.lines(
                        config,
                        port,
                        BuildId.current(),
                        Runtime.version().toString(),
                        file == null ? "" : file.toString(),
                        overrides.isEmpty()
                                ? ""
                                : Path.of(overrides).toAbsolutePath().toString(),
                        diagnostics == null ? 0 : diagnostics.port())
                .forEach(System.out::println);
        log.info("relay", "event=listening port=" + port + " build=" + BuildId.current());
        Relay relay =
                new Relay(transport, SystemClock.INSTANCE, log, config, new SecureRandom(), Relay.Sequencing.THREADED);
        // Stopped, the relay ends every room on this thread - the one that polls it - telling each machine the relay
        // stopped rather than leaving it to try to come back for its window; the hook waits for that, a while.
        AtomicBoolean stopping = new AtomicBoolean();
        CountDownLatch closed = new CountDownLatch(1);
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            stopping.set(true);
                            try {
                                closed.await(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        "pwt-relay-shutdown"));
        try {
            while (!stopping.get()) {
                relay.poll();
                if (diagnostics != null) {
                    diagnostics.serve(relay);
                }
                LockSupport.parkNanos(POLL_NANOS);
            }
            relay.close();
        } finally {
            if (diagnostics != null) {
                diagnostics.close();
            }
            closed.countDown();
        }
    }

    /** The HTTP diagnostics on {@code net.relay.diagnosticPort}, or null when it is zero or cannot be bound. */
    private static @Nullable RelayDiagnostics diagnostics(NetConfig config, int port, NetLog log) {
        int diagnosticPort = config.relay().diagnosticPort();
        if (diagnosticPort == 0) {
            return null;
        }
        try {
            return RelayDiagnostics.start(
                    diagnosticPort,
                    port,
                    () -> new NettyTransport(config, SystemClock.INSTANCE, log, "probe"),
                    config,
                    SystemClock.INSTANCE,
                    log,
                    BuildId.current());
        } catch (IOException e) {
            log.error("diagnostics", "event=failed port=" + diagnosticPort, e);
            System.err.println("[relay] diagnostics could not listen on port " + diagnosticPort + ": " + e);
            return null;
        }
    }
}
