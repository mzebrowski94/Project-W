package pl.mzebrows.pwt.net;

import java.util.List;
import pl.mzebrows.pwt.sim.ConfigBinder;
import pl.mzebrows.pwt.sim.ConfigFile;
import pl.mzebrows.pwt.sim.ConfigKey;

/**
 * The networking tunables: {@code network.properties}, bound by component name ({@link ConfigBinder}),
 * so a tunable is a field here and a line there and nothing else. {@code net} reads no file: the shipped
 * one is compiled in ({@code :net:generateShippedNetConfig}) and is {@link #defaults()}; the client finds
 * a player's copy and hands its text to {@link #FILE}.
 *
 * @param port the port a host listens on, for both TCP and UDP
 * @param portSearch how many ports after {@code port} a host tries when it is taken - a second instance
 *     on the same machine lands on the next one instead of failing
 * @param connectTimeoutMillis how long a link may take to open, handshake included, before it is given up
 * @param linkTimeoutMillis how long an open link may go without hearing anything before it is dropped
 * @param heartbeatMillis how often a quiet link says it is still there; also how often round trip is measured
 * @param maxDatagramBytes the largest unreliable message, kept under a typical path MTU so nothing fragments;
 *     the largest reliable one is {@code Transport.MAX_MESSAGE_BYTES}, not tuning
 * @param conditions network conditions to simulate on top of the real ones, all zero for none
 * @param latencySteps the one-way latencies, in milliseconds, a joining copy steps through during a
 *     latency playtest; the rest of the conditions stay as set
 * @param log where the connection log goes and how much it keeps
 * @param pacing how this machine paces its part of a session - local config
 * @param session what every peer of a session agrees on, the host's holding for all - session config
 * @param lan how sessions on the local network are found
 */
public record NetConfig(
        int port,
        int portSearch,
        int connectTimeoutMillis,
        int linkTimeoutMillis,
        int heartbeatMillis,
        int maxDatagramBytes,
        Conditions conditions,
        @ConfigKey("conditions.latencySteps") List<Integer> latencySteps,
        LogConfig log,
        Pacing pacing,
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
        require(
                !latencySteps.isEmpty() && latencySteps.stream().allMatch(step -> step >= 0),
                "latencySteps",
                latencySteps);
        require(
                pacing.historyTicks() > pacing.leadMaxTicks() + session.checksumTicks(),
                "pacing.historyTicks",
                pacing.historyTicks());
        latencySteps = List.copyOf(latencySteps);
    }

    /** {@code assets/network.properties}: what ships, compiled in, and how a player's copy is read. */
    public static final ConfigFile<NetConfig> FILE = new ConfigFile<>(
            "network.properties", NetConfig.class, ROOT, ShippedNetConfig.TEXT, ConfigBinder.Conversions.none());

    /** What ships: {@code assets/network.properties}, compiled in. What every test runs. */
    public static NetConfig defaults() {
        return FILE.defaults();
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
                newConditions,
                latencySteps,
                log,
                pacing,
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
                conditions,
                latencySteps,
                newLog,
                pacing,
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
     * @param spikeEveryMillis how far apart, on average, the link stops for a moment - a relay or a radio
     *     holding everything, then letting it all go at once, as the 2026-10-01 playtest's Hamachi link did
     * @param spikeMillis how long each of those moments holds every message, both ways and both kinds
     * @param seed what the draws come from, so a run under the same conditions misbehaves the same way
     */
    public record Conditions(
            int latencyMillis,
            int jitterMillis,
            float loss,
            float duplicate,
            int spikeEveryMillis,
            int spikeMillis,
            long seed) {

        public static final Conditions NONE = new Conditions(0, 0, 0f, 0f, 1L);

        public Conditions {
            require(latencyMillis >= 0, "latencyMillis", latencyMillis);
            require(jitterMillis >= 0, "jitterMillis", jitterMillis);
            require(loss >= 0f && loss < 1f, "loss", loss);
            require(duplicate >= 0f && duplicate < 1f, "duplicate", duplicate);
            require(spikeEveryMillis >= 0, "spikeEveryMillis", spikeEveryMillis);
            require(spikeMillis >= 0, "spikeMillis", spikeMillis);
        }

        /** Conditions without spikes. */
        public Conditions(int latencyMillis, int jitterMillis, float loss, float duplicate, long seed) {
            this(latencyMillis, jitterMillis, loss, duplicate, 0, 0, seed);
        }

        /** The same conditions at another latency: what a latency playtest steps. */
        public Conditions withLatency(int millis) {
            return new Conditions(millis, jitterMillis, loss, duplicate, spikeEveryMillis, spikeMillis, seed);
        }

        /** Whether the link stops now and then. */
        public boolean spikes() {
            return spikeEveryMillis > 0 && spikeMillis > 0;
        }

        /** Whether these conditions change anything at all. */
        public boolean any() {
            return latencyMillis > 0 || jitterMillis > 0 || loss > 0f || duplicate > 0f || spikes();
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

    /** The same config with another session config - a joiner's, once the host has sent its own. */
    public NetConfig with(Session newSession) {
        return new NetConfig(
                port,
                portSearch,
                connectTimeoutMillis,
                linkTimeoutMillis,
                heartbeatMillis,
                maxDatagramBytes,
                conditions,
                latencySteps,
                log,
                pacing,
                newSession,
                lan);
    }

    /** The same config with other pacing. */
    public NetConfig with(Pacing newPacing) {
        return new NetConfig(
                port,
                portSearch,
                connectTimeoutMillis,
                linkTimeoutMillis,
                heartbeatMillis,
                maxDatagramBytes,
                conditions,
                latencySteps,
                log,
                newPacing,
                session,
                lan);
    }

    /**
     * How this machine paces its part of a lockstep session (ADR 0012) - local config: two peers may
     * differ in it and play the same game. Every value is in ticks.
     *
     * @param leadMinTicks the fewest ticks ahead a peer tags its input - the host's own peer, with no
     *     network in the way, sits here
     * @param leadMaxTicks the most; a peer that would need more is further away than a session is built
     *     for (same region), and plays with this much delay rather than more
     * @param marginTicks how long before its tick is sealed a peer's input should reach the sequencer; each
     *     peer's lead is adapted to keep it there
     * @param bufferTicks how many sealed ticks a peer may hold unplayed before it plays faster to catch up
     * @param catchUpTicks the most ticks a peer plays in one frame, however far behind it is
     * @param historyTicks how many sealed ticks the sequencer keeps, so a peer dropping in or being healed
     *     can be brought forward from a snapshot; read only where the sequencer runs
     * @param redundantTicks how many of the newest unacknowledged ticks every bundle datagram repeats;
     *     older ones are sent again only when a round trip has passed without their acknowledgement; read
     *     only where the sequencer runs
     * @param cushionMaxTicks the most ticks a peer keeps in hand against a link that delivers them
     *     unevenly - a delay on its screen and its input while it does; zero keeps none
     * @param cushionCalmTicks how long a peer must not run dry before it gives one of those back
     * @param spikeTicks how much longer than its usual gap a peer may go silent before what it sends next
     *     counts as a spike - held by its link and let go at once - whose lateness its lead does not rise
     *     to cover; zero covers every one; read only where the sequencer runs
     */
    public record Pacing(
            int leadMinTicks,
            int leadMaxTicks,
            int marginTicks,
            int bufferTicks,
            int catchUpTicks,
            int historyTicks,
            int redundantTicks,
            int cushionMaxTicks,
            int cushionCalmTicks,
            int spikeTicks) {

        /**
         * The ceiling on {@code leadMaxTicks}. The rings that hold input ahead of its tick are sized from
         * it, so raising it is the one edit.
         */
        public static final int MOST_LEAD_TICKS = 120;

        public Pacing {
            require(leadMinTicks >= 1, "leadMinTicks", leadMinTicks);
            require(leadMaxTicks >= leadMinTicks && leadMaxTicks <= MOST_LEAD_TICKS, "leadMaxTicks", leadMaxTicks);
            require(marginTicks >= 0, "marginTicks", marginTicks);
            require(bufferTicks >= 0, "bufferTicks", bufferTicks);
            require(catchUpTicks >= 1, "catchUpTicks", catchUpTicks);
            require(redundantTicks >= 1 && redundantTicks <= 8, "redundantTicks", redundantTicks);
            require(cushionMaxTicks >= 0, "cushionMaxTicks", cushionMaxTicks);
            require(cushionCalmTicks >= 1, "cushionCalmTicks", cushionCalmTicks);
            require(spikeTicks >= 0, "spikeTicks", spikeTicks);
        }
    }

    /**
     * The session config: what every peer of a session must agree on (ADR 0018). The host's holds for
     * everyone - the sequencer sends it in each welcome, and a joiner plays by that, never by its own file.
     * A component added here is one the welcome carries; {@code WireTest} holds the two together.
     *
     * @param checksumTicks how often peers compare worlds, and how often each keeps a keyframe for a dump
     */
    public record Session(int checksumTicks) {

        /**
         * The ceiling on {@code checksumTicks}, a minute at 60 Hz. A joiner sizes its tick ring from the
         * host's value, so this bounds what a welcome can make it allocate.
         */
        public static final int MOST_CHECKSUM_TICKS = 3_600;

        public Session {
            require(checksumTicks >= 1 && checksumTicks <= MOST_CHECKSUM_TICKS, "checksumTicks", checksumTicks);
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
