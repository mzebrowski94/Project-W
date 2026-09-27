package pl.mzebrows.pwt.net;

import java.util.List;
import java.util.SortedMap;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.ConfigKey;

/**
 * The networking tunables: {@code network.properties}, bound by component name ({@link ConfigBinder}),
 * so a tunable is a field here and a line there and nothing else. {@code net} reads no file: the shipped
 * one is compiled in ({@code :net:generateShippedNetConfig}) and is {@link #defaults()}; the client finds
 * a player's copy and hands its text to {@link #parse}.
 *
 * @param port the port a host listens on, for both TCP and UDP
 * @param portSearch how many ports after {@code port} a host tries when it is taken - a second instance
 *     on the same machine lands on the next one instead of failing
 * @param connectTimeoutMillis how long a link may take to open, handshake included, before it is given up
 * @param linkTimeoutMillis how long an open link may go without hearing anything before it is dropped
 * @param heartbeatMillis how often a quiet link says it is still there; also how often round trip is measured
 * @param maxDatagramBytes the largest unreliable message, kept under a typical path MTU so nothing fragments
 * @param maxMessageBytes the largest reliable message - a snapshot has to fit
 * @param conditions network conditions to simulate on top of the real ones, all zero for none
 * @param latencySteps the one-way latencies, in milliseconds, a joining copy steps through during a
 *     latency playtest; the rest of the conditions stay as set
 * @param log where the connection log goes and how much it keeps
 * @param session how a lockstep session paces itself
 * @param lan how sessions on the local network are found
 */
public record NetConfig(
        int port,
        int portSearch,
        int connectTimeoutMillis,
        int linkTimeoutMillis,
        int heartbeatMillis,
        int maxDatagramBytes,
        int maxMessageBytes,
        Conditions conditions,
        @ConfigKey("conditions.latencySteps") List<Integer> latencySteps,
        LogConfig log,
        Session session,
        Lan lan) {

    private static final String ROOT = "net";

    public NetConfig {
        require(port > 0 && port < 65_536, "port", port);
        require(portSearch >= 0 && port + portSearch < 65_536, "portSearch", portSearch);
        require(connectTimeoutMillis > 0, "connectTimeoutMillis", connectTimeoutMillis);
        require(heartbeatMillis > 0, "heartbeatMillis", heartbeatMillis);
        require(linkTimeoutMillis > heartbeatMillis, "linkTimeoutMillis", linkTimeoutMillis);
        require(maxDatagramBytes >= 64 && maxDatagramBytes <= 65_000, "maxDatagramBytes", maxDatagramBytes);
        require(maxMessageBytes >= maxDatagramBytes, "maxMessageBytes", maxMessageBytes);
        require(
                !latencySteps.isEmpty() && latencySteps.stream().allMatch(step -> step >= 0),
                "latencySteps",
                latencySteps);
        latencySteps = List.copyOf(latencySteps);
    }

    /** What ships: {@code assets/network.properties}, compiled in. What every test runs. */
    public static NetConfig defaults() {
        return Shipped.CONFIG;
    }

    /**
     * A config from the text of a {@code network.properties}, with every value it lacks or garbles taken
     * from {@link #defaults()} and named in the result, as is every key no record has.
     *
     * @throws IllegalArgumentException if the values are readable but break a rule a record checks
     */
    public static Parsed parse(String text) {
        ConfigBinder.Bound<NetConfig> bound =
                ConfigBinder.bind(NetConfig.class, ROOT, ConfigBinder.parse(text), Shipped.VALUES);
        NetConfig config = bound.value();
        if (config == null) {
            throw new IllegalArgumentException("network config: " + String.join("; ", bound.problems()));
        }
        return new Parsed(config, bound.problems());
    }

    /** A config read from text, and everything that had to be taken from what ships instead. */
    public record Parsed(NetConfig config, List<String> problems) {}

    /** Bound on first use, so a failure names the key rather than a class. */
    private static final class Shipped {

        static final SortedMap<String, String> VALUES = ConfigBinder.parse(ShippedNetConfig.TEXT);

        static final NetConfig CONFIG = ConfigBinder.bindStrictly(NetConfig.class, ROOT, VALUES);

        private Shipped() {}
    }

    /** The same config with other simulated conditions. */
    public NetConfig with(Conditions newConditions) {
        return new NetConfig(
                port,
                portSearch,
                connectTimeoutMillis,
                linkTimeoutMillis,
                heartbeatMillis,
                maxDatagramBytes,
                maxMessageBytes,
                newConditions,
                latencySteps,
                log,
                session,
                lan);
    }

    /** The same config with another log setup. */
    public NetConfig with(LogConfig newLog) {
        return new NetConfig(
                port,
                portSearch,
                connectTimeoutMillis,
                linkTimeoutMillis,
                heartbeatMillis,
                maxDatagramBytes,
                maxMessageBytes,
                conditions,
                latencySteps,
                newLog,
                session,
                lan);
    }

    /**
     * Network conditions laid on top of whatever the real network does, so two instances on one machine
     * can be played through real-looking lag. Zero means off, field by field.
     *
     * <p>Latency and jitter apply to both kinds of message; loss and duplication only to unreliable ones,
     * because a reliable message is by definition neither lost nor repeated - its cost under a bad
     * network is delay, which latency and jitter already model. Reliable messages keep their order.
     *
     * @param latencyMillis added to every message on the way out
     * @param jitterMillis a further delay drawn between zero and this, per message
     * @param loss the fraction of unreliable messages dropped, in {@code [0, 1)}
     * @param duplicate the fraction of unreliable messages sent twice, in {@code [0, 1)}
     * @param seed what the draws come from, so a run under the same conditions misbehaves the same way
     */
    public record Conditions(int latencyMillis, int jitterMillis, float loss, float duplicate, long seed) {

        public static final Conditions NONE = new Conditions(0, 0, 0f, 0f, 1L);

        public Conditions {
            require(latencyMillis >= 0, "latencyMillis", latencyMillis);
            require(jitterMillis >= 0, "jitterMillis", jitterMillis);
            require(loss >= 0f && loss < 1f, "loss", loss);
            require(duplicate >= 0f && duplicate < 1f, "duplicate", duplicate);
        }

        /** Whether these conditions change anything at all. */
        public boolean any() {
            return latencyMillis > 0 || jitterMillis > 0 || loss > 0f || duplicate > 0f;
        }
    }

    /**
     * The connection log.
     *
     * @param dir where log files go, one per running instance
     * @param level the least severe line written: {@code FINE}, {@code INFO}, {@code WARNING} or {@code SEVERE}
     * @param traceSize how many per-message events the trace keeps for a dump; a power of two
     * @param statsSeconds how often each link's numbers are written; zero for never
     */
    public record LogConfig(String dir, String level, int traceSize, int statsSeconds) {

        public LogConfig {
            require(traceSize > 0 && Integer.bitCount(traceSize) == 1, "traceSize", traceSize);
            require(statsSeconds >= 0, "statsSeconds", statsSeconds);
        }
    }

    public NetConfig with(Session newSession) {
        return new NetConfig(
                port,
                portSearch,
                connectTimeoutMillis,
                linkTimeoutMillis,
                heartbeatMillis,
                maxDatagramBytes,
                maxMessageBytes,
                conditions,
                latencySteps,
                log,
                newSession,
                lan);
    }

    /**
     * How a lockstep session paces itself (ADR 0012). Every value is in ticks.
     *
     * @param leadMinTicks the fewest ticks ahead a peer tags its input - the host's own peer, with no
     *     network in the way, sits here
     * @param leadMaxTicks the most; a peer that would need more is further away than a session is built
     *     for (same region, D4), and plays with this much delay rather than more
     * @param marginTicks how long before its tick is sealed a peer's input should reach the sequencer; each
     *     peer's lead is adapted to keep it there
     * @param bufferTicks how many sealed ticks a peer may hold unplayed before it plays faster to catch up
     * @param catchUpTicks the most ticks a peer plays in one frame, however far behind it is
     * @param checksumTicks how often peers compare worlds, and how often each keeps a keyframe for a dump
     * @param historyTicks how many sealed ticks the sequencer keeps, so a peer dropping in or being healed
     *     can be brought forward from a snapshot
     * @param redundantTicks how many of the newest unacknowledged ticks every bundle datagram repeats;
     *     older ones are sent again only when a round trip has passed without their acknowledgement
     */
    public record Session(
            int leadMinTicks,
            int leadMaxTicks,
            int marginTicks,
            int bufferTicks,
            int catchUpTicks,
            int checksumTicks,
            int historyTicks,
            int redundantTicks) {

        /**
         * The ceiling on {@code leadMaxTicks}. The rings that hold input ahead of its tick are sized from
         * it, so raising it is the one edit.
         */
        public static final int MOST_LEAD_TICKS = 120;

        public Session {
            require(leadMinTicks >= 1, "leadMinTicks", leadMinTicks);
            require(leadMaxTicks >= leadMinTicks && leadMaxTicks <= MOST_LEAD_TICKS, "leadMaxTicks", leadMaxTicks);
            require(marginTicks >= 0, "marginTicks", marginTicks);
            require(bufferTicks >= 0, "bufferTicks", bufferTicks);
            require(catchUpTicks >= 1, "catchUpTicks", catchUpTicks);
            require(checksumTicks >= 1, "checksumTicks", checksumTicks);
            require(historyTicks > leadMaxTicks + checksumTicks, "historyTicks", historyTicks);
            require(redundantTicks >= 1 && redundantTicks <= 8, "redundantTicks", redundantTicks);
        }
    }

    /**
     * How sessions on the local network are found: a host announces itself, a joiner listens.
     *
     * @param port the port joiners listen on and hosts announce to; shared, so several copies on one PC
     *     can listen at once
     * @param announceMillis how often a host announces itself
     * @param forgetMillis how long a host that has gone quiet stays on a joiner's list
     */
    public record Lan(int port, int announceMillis, int forgetMillis) {

        public Lan {
            require(port > 0 && port < 65_536, "lan.port", port);
            require(announceMillis > 0, "lan.announceMillis", announceMillis);
            require(forgetMillis > announceMillis, "lan.forgetMillis", forgetMillis);
        }
    }

    private static void require(boolean ok, String name, Object value) {
        if (!ok) {
            throw new IllegalArgumentException("network config " + name + " out of range: " + value);
        }
    }
}
