package pl.mzebrows.pwt.relay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.locks.LockSupport;
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
 * beats {@code net.relay.port}. The only class of this module that names the Netty adapter ({@code RelayArchitectureTest}).
 */
public final class RelayMain {

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
        Path file = log.file();
        RelayBanner.lines(
                        config,
                        port,
                        BuildId.current(),
                        Runtime.version().toString(),
                        file == null ? "" : file.toString(),
                        overrides.isEmpty()
                                ? ""
                                : Path.of(overrides).toAbsolutePath().toString())
                .forEach(System.out::println);
        log.info("relay", "event=listening port=" + port + " build=" + BuildId.current());
        Relay relay =
                new Relay(transport, SystemClock.INSTANCE, log, config, new SecureRandom(), Relay.Sequencing.THREADED);
        Runtime.getRuntime().addShutdownHook(new Thread(transport::close, "pwt-relay-shutdown"));
        while (true) {
            relay.poll();
            LockSupport.parkNanos(1_000_000L);
        }
    }
}
