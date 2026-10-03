package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.mzebrows.pwt.net.session.DesyncReport;

/**
 * Reads every desync in a directory of dumps - the host's {@code reference} and a friend's {@code
 * desynced} file for the same tick - and writes {@link DesyncReport}'s verdict beside them as {@code
 * desync-t<tick>.report.txt}. A tool, not a test: {@code PWT_DESYNC=<dir>}. Here in {@code core} because
 * the replay has to build worlds from the host's shipped config, which is {@link ScenarioWorlds}'s job.
 */
@EnabledIfEnvironmentVariable(named = "PWT_DESYNC", matches = ".+")
class DesyncReportRunnerTest {

    private static final Pattern DUMP = Pattern.compile("desync-t(\\d+)-peer\\d+-(reference|desynced)\\.bin");

    @Test
    void reportsEveryDesyncInTheDirectory() throws IOException {
        Path dir = Path.of(System.getenv("PWT_DESYNC"));
        List<Path> references = dumps(dir, "reference");
        assertThat(references).as("reference dumps in %s", dir).isNotEmpty();
        int reported = 0;
        for (Path reference : references) {
            String tick = tickOf(reference);
            for (Path desynced : dumps(dir, "desynced")) {
                if (!tickOf(desynced).equals(tick)) {
                    continue;
                }
                String report = DesyncReport.compare(reference, desynced, new ScenarioWorlds());
                Path out = dir.resolve("desync-t" + tick + ".report.txt");
                Files.writeString(out, report, StandardCharsets.UTF_8);
                System.out.println("--- " + reference.getFileName() + " vs " + desynced.getFileName() + " -> " + out);
                System.out.println(report);
                reported++;
            }
        }
        assertThat(reported).as("reference and desynced dumps of the same tick").isPositive();
    }

    private static List<Path> dumps(Path dir, String role) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> {
                        Matcher name = DUMP.matcher(file.getFileName().toString());
                        return name.matches() && name.group(2).equals(role);
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String tickOf(Path dump) {
        Matcher name = DUMP.matcher(dump.getFileName().toString());
        return name.matches() ? name.group(1) : "";
    }
}
