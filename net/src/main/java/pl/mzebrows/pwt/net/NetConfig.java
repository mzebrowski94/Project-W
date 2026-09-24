package pl.mzebrows.pwt.net;

/**
 * The networking tunables, handed in already parsed. {@code net} reads no config file itself: the
 * client loads this from {@code network.properties}, as it loads {@code SimConfig} from
 * {@code game.properties}.
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
 * @param log where the connection log goes and how much it keeps
 * @param session how a lockstep session paces itself
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
        LogConfig log,
        Session session) {

    public NetConfig {
        require(port > 0 && port < 65_536, "port", port);
        require(portSearch >= 0 && port + portSearch < 65_536, "portSearch", portSearch);
        require(connectTimeoutMillis > 0, "connectTimeoutMillis", connectTimeoutMillis);
        require(heartbeatMillis > 0, "heartbeatMillis", heartbeatMillis);
        require(linkTimeoutMillis > heartbeatMillis, "linkTimeoutMillis", linkTimeoutMillis);
        require(maxDatagramBytes >= 64 && maxDatagramBytes <= 65_000, "maxDatagramBytes", maxDatagramBytes);
        require(maxMessageBytes >= maxDatagramBytes, "maxMessageBytes", maxMessageBytes);
    }

    /** The values the game ships with, and what every missing key in {@code network.properties} falls back to. */
    public static NetConfig defaults() {
        return new NetConfig(
                47_100,
                8,
                5_000,
                10_000,
                250,
                1_200,
                8 * 1024 * 1024,
                Conditions.NONE,
                LogConfig.DEFAULT,
                Session.DEFAULT);
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
                log,
                session);
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
                newLog,
                session);
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

        public static final LogConfig DEFAULT = new LogConfig("logs/net", "INFO", 4_096, 1);

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
                log,
                newSession);
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
     */
    public record Session(
            int leadMinTicks,
            int leadMaxTicks,
            int marginTicks,
            int bufferTicks,
            int catchUpTicks,
            int checksumTicks,
            int historyTicks) {

        public static final Session DEFAULT = new Session(2, 30, 1, 2, 4, 60, 600);

        public Session {
            require(leadMinTicks >= 1, "leadMinTicks", leadMinTicks);
            require(leadMaxTicks >= leadMinTicks && leadMaxTicks <= 120, "leadMaxTicks", leadMaxTicks);
            require(marginTicks >= 0, "marginTicks", marginTicks);
            require(bufferTicks >= 0, "bufferTicks", bufferTicks);
            require(catchUpTicks >= 1, "catchUpTicks", catchUpTicks);
            require(checksumTicks >= 1, "checksumTicks", checksumTicks);
            require(historyTicks > leadMaxTicks + checksumTicks, "historyTicks", historyTicks);
        }
    }

    private static void require(boolean ok, String name, Object value) {
        if (!ok) {
            throw new IllegalArgumentException("network config " + name + " out of range: " + value);
        }
    }
}
