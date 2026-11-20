package pl.mzebrows.pwt.relay;

import java.util.List;
import pl.mzebrows.pwt.net.NetConfig;

/**
 * What the relay prints when it starts: which build, where it listens, where it logs and the limits it runs with,
 * so an operator reading a console - or a pasted one - knows what is on the other end without opening a file.
 * Plain lines, built here so a test can read them; {@link RelayMain} prints them.
 */
final class RelayBanner {

    private RelayBanner() {}

    /**
     * @param config the relay's config as it was read, overrides included
     * @param port the port actually bound, TCP and UDP
     * @param buildId the build id this relay's protocol and tick rules hash to
     * @param javaVersion the runtime the relay runs on
     * @param logFile where this run's log is written, or empty when it could not be opened
     * @param overrides the file of override lines it was started with, or empty for none
     * @param diagnosticPort the port the HTTP diagnostics serve on, or zero for none
     */
    static List<String> lines(
            NetConfig config,
            int port,
            String buildId,
            String javaVersion,
            String logFile,
            String overrides,
            int diagnosticPort) {
        NetConfig.Relay relay = config.relay();
        return List.of(
                "============================================================",
                " Project-W relay",
                "============================================================",
                " build id      " + buildId,
                " java          " + javaVersion,
                " listening     port " + port + ", TCP and UDP (every interface)",
                " config        "
                        + (overrides.isEmpty() ? "network.properties as shipped" : "overrides from " + overrides),
                " log           " + (logFile.isEmpty() ? "none (could not be opened)" : logFile) + " ["
                        + config.log().level() + "]",
                " rooms         at most " + relay.roomsMax() + ", " + relay.roomsPerAddressMax() + " per address, idle "
                        + relay.idleRoomMinutes() + " min",
                " links         at most " + relay.linksPerAddressMax() + " per address, hello within "
                        + relay.helloTimeoutSeconds() + " s, " + relay.bytesPerSecondPerLink() + " B/s each",
                " refusals      " + relay.refusalsPerAddressMax() + " in " + relay.refusalWindowSeconds()
                        + " s silence an address for " + relay.silenceSeconds() + " s",
                " passwords     " + relay.wrongPasswordsPerRoomMax() + " wrong lock a room for "
                        + relay.roomLockSeconds() + " s",
                " rejoin        " + config.rejoin().windowSeconds() + " s window",
                " diagnostics   "
                        + (diagnosticPort == 0
                                ? "off (net.relay.diagnosticPort)"
                                : "http port " + diagnosticPort + ": GET /status, GET /log?lines=200, POST /probe"),
                "============================================================");
    }
}
