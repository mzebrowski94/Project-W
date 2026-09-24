package pl.mzebrows.pwt.net.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mzebrows.pwt.net.NetConfig.LogConfig;

class NetLogTest {

    @TempDir
    Path dir;

    @Test
    void writesOneKeyValueLinePerEvent() throws IOException {
        try (NetLog log = NetLog.open(config("INFO"), "host")) {
            log.info("link", "link=3 event=opened remote=127.0.0.1:50123");
        }

        assertThat(lines())
                .singleElement()
                .asString()
                .matches("t=\\S+ lvl=INFO inst=host cat=link link=3 event=opened remote=127\\.0\\.0\\.1:50123");
    }

    @Test
    void leavesOutWhatIsBelowItsLevel() throws IOException {
        try (NetLog log = NetLog.open(config("WARNING"), "host")) {
            log.info("link", "event=opened");
            log.warn("link", "event=timeout");
        }

        assertThat(lines()).singleElement().asString().contains("event=timeout");
    }

    @Test
    void quotesALibrarysOwnLinesAsAMessage() throws IOException {
        try (NetLog log = NetLog.open(config("INFO"), "host")) {
            log.adopt("test.library");
            Logger.getLogger("test.library").warning("said \"hello\"");
        }

        assertThat(lines()).singleElement().asString().endsWith("cat=test.library msg=\"said \\\"hello\\\"\"");
    }

    @Test
    void dumpsOnlyTheLinkAskedAboutAndOnlyItsLatestEvents() throws IOException {
        try (NetLog log = NetLog.open(config("INFO"), "host")) {
            NetTrace trace = log.trace();
            for (int i = 0; i < 5; i++) {
                trace.record(i * 1_000_000L, NetTrace.Kind.SENT_UNRELIABLE, 1, 10 + i);
                trace.record(i * 1_000_000L, NetTrace.Kind.SENT_UNRELIABLE, 2, 99);
            }

            trace.dump(log, 1, "lost", 2);
        }

        List<String> events =
                lines().stream().filter(line -> line.contains("kind=")).toList();
        assertThat(events).hasSize(2).allMatch(line -> line.contains("link=1"));
        assertThat(events.getLast()).contains("bytes=14").contains("dt=0.0ms");
    }

    private LogConfig config(String level) {
        return new LogConfig(dir.toString(), level, 16, 0);
    }

    private List<String> lines() throws IOException {
        try (var files = Files.list(dir)) {
            Path file = files.filter(path -> path.toString().endsWith(".log"))
                    .findFirst()
                    .orElseThrow();
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        }
    }
}
