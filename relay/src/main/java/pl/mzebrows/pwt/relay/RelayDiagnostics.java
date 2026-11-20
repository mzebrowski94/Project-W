package pl.mzebrows.pwt.relay;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.door.DoorClient;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.RoomWire;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

/**
 * The relay's diagnostics (G0103): a small HTTP server on {@code net.relay.diagnosticPort}, for an operator with curl or
 * Postman to ask whether the relay is up, what reached it and whether its door works, without a copy of the game.
 *
 * <ul>
 *   <li>{@code GET /status} - build, ports, uptime, the rooms and links open now, and the address the request came
 *       from as the relay saw it.
 *   <li>{@code GET /log?lines=200} - the tail of this run's log file: every link the transport accepted, and why each
 *       one that closed did - the lines a link that never finished its handshake leaves and nothing else does.
 *   <li>{@code POST /probe?host=127.0.0.1} (GET too) - the relay dials its own door at {@code host} on its game port,
 *       over a transport of its own, opens a room and joins it from a second link, and says how far each step got and
 *       how long it took. The room is left to close by itself after the rejoin window.
 * </ul>
 *
 * <p>The relay is not thread-safe: what {@code /status} reads it asks of the thread that polls the relay, through
 * {@link #serve}. A probe is a client of the relay like any other and touches nothing of it. One probe at a time.
 */
public final class RelayDiagnostics implements AutoCloseable {

    /** What a probe calls its build version in the door's messages and the relay's log. */
    static final String PROBE_VERSION = "diagnostic-probe";

    private static final int LOG_LINES_DEFAULT = 200;
    private static final int LOG_LINES_MAX = 5_000;
    private static final int LOG_TAIL_BYTES_MAX = 4 * 1024 * 1024;
    private static final long STATUS_WAIT_SECONDS = 2L;
    private static final long DOOR_ANSWER_NANOS = 3_000_000_000L;
    private static final long PROBE_POLL_NANOS = 1_000_000L;

    private final HttpServer server;
    private final ExecutorService threads;
    private final ConcurrentLinkedQueue<Consumer<Relay>> asked = new ConcurrentLinkedQueue<>();
    private final Supplier<Transport> probeTransports;
    private final NetConfig config;
    private final int relayPort;
    private final Clock clock;
    private final NetLog log;
    private final String buildId;
    private final long startedNanos;
    private final AtomicBoolean probing = new AtomicBoolean();
    private final SecureRandom keys = new SecureRandom();

    private RelayDiagnostics(
            HttpServer server,
            ExecutorService threads,
            Supplier<Transport> probeTransports,
            NetConfig config,
            int relayPort,
            Clock clock,
            NetLog log,
            String buildId) {
        this.server = server;
        this.threads = threads;
        this.probeTransports = probeTransports;
        this.config = config;
        this.relayPort = relayPort;
        this.clock = clock;
        this.log = log;
        this.buildId = buildId;
        this.startedNanos = clock.nanos();
    }

    /**
     * Starts the diagnostics on {@code port}, every interface.
     *
     * @param port the TCP port to serve on; zero takes any free one, which {@link #port()} then names
     * @param relayPort the port the relay's door listens on: what a probe dials
     * @param probeTransports a fresh client transport per probe, closed by the probe
     * @throws IOException if the port cannot be bound
     */
    public static RelayDiagnostics start(
            int port,
            int relayPort,
            Supplier<Transport> probeTransports,
            NetConfig config,
            Clock clock,
            NetLog log,
            String buildId)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService threads = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "pwt-relay-diagnostics");
            thread.setDaemon(true);
            return thread;
        });
        RelayDiagnostics diagnostics =
                new RelayDiagnostics(server, threads, probeTransports, config, relayPort, clock, log, buildId);
        server.createContext("/", diagnostics::handle);
        server.setExecutor(threads);
        server.start();
        log.info("diagnostics", "event=listening port=" + diagnostics.port() + " http=yes");
        return diagnostics;
    }

    /** The port the diagnostics serve on. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Answers what the requests asked of the relay: call on the thread that polls it, after each poll. */
    public void serve(Relay relay) {
        for (Consumer<Relay> ask = asked.poll(); ask != null; ask = asked.poll()) {
            ask.accept(relay);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        String from = String.valueOf(exchange.getRemoteAddress());
        log.info(
                "diagnostics",
                "event=request method=" + exchange.getRequestMethod() + " path=" + path + " from=" + from);
        try {
            switch (path) {
                case "/", "" -> reply(exchange, 200, "application/json", index());
                case "/status" -> reply(exchange, 200, "application/json", status(from));
                case "/log" -> reply(exchange, 200, "text/plain; charset=utf-8", logTail(query.get("lines")));
                case "/probe" -> probe(exchange, query);
                default -> reply(exchange, 404, "application/json", "{\"ok\":false,\"error\":\"no such endpoint\"}");
            }
        } catch (RuntimeException e) {
            log.warn("diagnostics", "event=failed path=" + path + " error=\"" + e + "\"");
            reply(exchange, 500, "application/json", "{\"ok\":false,\"error\":" + json(String.valueOf(e)) + "}");
        }
    }

    private static String index() {
        return "{\"ok\":true,\"endpoints\":["
                + json("GET /status") + "," + json("GET /log?lines=200") + ","
                + json("POST /probe?host=127.0.0.1") + "]}";
    }

    private String status(String from) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        asked.add(relay -> answer.complete(statusOf(relay, from)));
        try {
            return answer.get(STATUS_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the relay", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("the relay did not answer within " + STATUS_WAIT_SECONDS + " s", e);
        }
    }

    /** On the relay's thread. */
    private String statusOf(Relay relay, String from) {
        StringBuilder rooms = new StringBuilder();
        for (String code : relay.roomCodes()) {
            if (rooms.length() > 0) {
                rooms.append(',');
            }
            rooms.append("{\"code\":")
                    .append(json(code))
                    .append(",\"links\":")
                    .append(relay.linksIn(code))
                    .append('}');
        }
        Path file = log.file();
        return "{\"ok\":true"
                + ",\"build\":" + json(buildId)
                + ",\"relayPort\":" + relayPort
                + ",\"protocol\":\"TCP and UDP on relayPort; a client needs both\""
                + ",\"uptimeSeconds\":" + (clock.nanos() - startedNanos) / 1_000_000_000L
                + ",\"you\":" + json(from)
                + ",\"rooms\":[" + rooms + "]"
                + ",\"roomsMax\":" + config.relay().roomsMax()
                + ",\"links\":" + relay.linkCount()
                + ",\"linksAtTheDoor\":" + relay.linksAtTheDoor()
                + ",\"logLevel\":" + json(config.log().level())
                + ",\"logFile\":" + json(file == null ? "" : file.toString())
                + "}";
    }

    private String logTail(@Nullable String lines) {
        Path file = log.file();
        if (file == null) {
            return "the relay's log could not be opened: nothing is written\n";
        }
        int wanted = LOG_LINES_DEFAULT;
        if (lines != null && lines.matches("\\d{1,6}")) {
            wanted = Math.max(1, Math.min(LOG_LINES_MAX, Integer.parseInt(lines)));
        }
        String text;
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long length = in.length();
            int read = (int) Math.min(length, LOG_TAIL_BYTES_MAX);
            byte[] bytes = new byte[read];
            in.seek(length - read);
            in.readFully(bytes);
            text = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + file + ": " + e.getMessage(), e);
        }
        List<String> all = text.lines().toList();
        StringBuilder tail = new StringBuilder();
        for (String line : all.subList(Math.max(0, all.size() - wanted), all.size())) {
            tail.append(line).append('\n');
        }
        return tail.toString();
    }

    private void probe(HttpExchange exchange, Map<String, String> query) throws IOException {
        String host = query.getOrDefault("host", "127.0.0.1").strip();
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }
        if (!probing.compareAndSet(false, true)) {
            reply(exchange, 409, "application/json", "{\"ok\":false,\"error\":\"a probe is already running\"}");
            return;
        }
        try {
            reply(exchange, 200, "application/json", new Probe(host).run());
        } finally {
            probing.set(false);
        }
    }

    private static void reply(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> query(@Nullable String raw) {
        Map<String, String> pairs = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return pairs;
        }
        for (String pair : raw.split("&", -1)) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            pairs.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return pairs;
    }

    /** A string as a JSON string literal. */
    static String json(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * One probe: a host's link and a joiner's to the relay's door, as two copies of the game would make them, over a
     * transport of the probe's own on the request's thread.
     */
    private final class Probe implements TransportListener {

        private final String host;
        private final List<String> steps = new ArrayList<>();
        private final Map<Integer, String> closedWhy = new HashMap<>();
        private final Map<Integer, byte[]> firstHeard = new HashMap<>();
        private final List<Integer> opened = new ArrayList<>();

        Probe(String host) {
            this.host = host;
        }

        String run() {
            log.info("diagnostics", "event=probe-start target=" + host + ":" + relayPort);
            long started = clock.nanos();
            boolean ok = false;
            String code = "";
            Transport own = probeTransports.get();
            try {
                int hostLink = connect("host link", own);
                if (hostLink >= 0) {
                    long key = keys.nextLong() | 1L;
                    DoorClient open =
                            DoorClient.open(buildId, PROBE_VERSION, key, RoomWire.sessionConfig(config.session()), "");
                    code = ask("open a room", own, hostLink, open);
                    if (!code.isEmpty()) {
                        int joinLink = connect("join link", own);
                        if (joinLink >= 0) {
                            ok = !ask("join the room", own, joinLink, DoorClient.join(code, "", buildId, PROBE_VERSION))
                                    .isEmpty();
                            own.disconnect(joinLink, "diagnostic probe done");
                        }
                    }
                    own.disconnect(hostLink, "diagnostic probe done");
                }
                own.poll(this);
            } finally {
                own.close();
            }
            long tookMs = (clock.nanos() - started) / 1_000_000L;
            log.info(
                    "diagnostics",
                    "event=probe-end target=" + host + ":" + relayPort + " ok=" + (ok ? "yes" : "no") + " ms=" + tookMs
                            + (code.isEmpty() ? "" : " code=" + code));
            return "{\"ok\":" + ok
                    + ",\"target\":" + json(host + ":" + relayPort)
                    + ",\"build\":" + json(buildId)
                    + ",\"room\":" + json(code)
                    + ",\"ms\":" + tookMs
                    + ",\"note\":"
                    + json(
                            code.isEmpty()
                                    ? "no room was opened"
                                    : "the probe's room closes by itself within net.rejoin.windowSeconds ("
                                            + config.rejoin().windowSeconds() + " s)")
                    + ",\"steps\":[" + String.join(",", steps) + "]}";
        }

        /** Opens a link, TCP and the UDP handshake both; its id, or -1 with the step recorded as failed. */
        private int connect(String step, Transport own) {
            long started = clock.nanos();
            int link;
            try {
                link = own.connect(host, relayPort);
            } catch (RuntimeException e) {
                step(step, false, started, "could not dial: " + e);
                return -1;
            }
            long deadline = started + (config.connectTimeoutMillis() + 1_000L) * 1_000_000L;
            while (clock.nanos() < deadline && !opened.contains(link) && !closedWhy.containsKey(link)) {
                own.poll(this);
                LockSupport.parkNanos(PROBE_POLL_NANOS);
            }
            if (opened.contains(link)) {
                step(step, true, started, "TCP connected and UDP bound");
                return link;
            }
            String why = closedWhy.get(link);
            step(step, false, started, why == null ? "no answer" : why);
            if (why == null) {
                own.disconnect(link, "diagnostic probe gave up");
            }
            return -1;
        }

        /** Says a door's request on an open link and waits for its answer: the room's code, or empty. */
        private String ask(String step, Transport own, int link, DoorClient door) {
            long started = clock.nanos();
            byte[] first = door.first();
            own.send(link, Delivery.RELIABLE, first, 0, first.length);
            long deadline = started + DOOR_ANSWER_NANOS;
            while (clock.nanos() < deadline && !firstHeard.containsKey(link) && !closedWhy.containsKey(link)) {
                own.poll(this);
                LockSupport.parkNanos(PROBE_POLL_NANOS);
            }
            byte[] answer = firstHeard.get(link);
            if (answer == null) {
                String why = closedWhy.get(link);
                step(step, false, started, why == null ? "the door did not answer" : "link closed: " + why);
                return "";
            }
            try {
                door.hear(answer, 0, answer.length);
            } catch (IllegalArgumentException e) {
                step(step, false, started, "not a door answer: " + e.getMessage());
                return "";
            }
            String code = door.code();
            if (door.state() == DoorClient.State.IN && code != null) {
                step(step, true, started, "room " + code);
                return code;
            }
            String refusal = door.refusal();
            step(step, false, started, refusal == null ? "no answer" : refusal);
            return "";
        }

        private void step(String name, boolean ok, long startedNanos, String detail) {
            long ms = (clock.nanos() - startedNanos) / 1_000_000L;
            steps.add("{\"step\":" + json(name) + ",\"ok\":" + ok + ",\"ms\":" + ms + ",\"detail\":" + json(detail)
                    + "}");
            log.info(
                    "diagnostics",
                    "event=probe-step step=\"" + name + "\" ok=" + (ok ? "yes" : "no") + " ms=" + ms + " detail=\""
                            + detail + "\"");
        }

        @Override
        public void opened(int link, String remote) {
            opened.add(link);
        }

        @Override
        public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
            if (!firstHeard.containsKey(link)) {
                byte[] copy = new byte[length];
                System.arraycopy(data, offset, copy, 0, length);
                firstHeard.put(link, copy);
            }
        }

        @Override
        public void closed(int link, String reason) {
            closedWhy.put(link, reason);
        }
    }
}
